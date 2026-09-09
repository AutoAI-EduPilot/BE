package io.edupilot.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import tools.jackson.databind.ObjectMapper;

class NotificationBulkRepositoryTest {

	@Test
	void classroomBulkInsertTargetsMembersOnly() {
		DataSource dataSource = dataSource("notification-members");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		createSchema(jdbc);
		jdbc.update("INSERT INTO users(id) VALUES (1), (2), (3), (4)");
		jdbc.update("INSERT INTO classroom_members(classroom_id, user_id) VALUES (30, 2), (30, 3), (31, 4)");
		NotificationBulkRepository repository = repository(dataSource);

		int inserted = repository.insertForClassroomMembers(
			30L,
			NotificationType.MATERIAL_UPLOADED,
			"New material",
			"Week 1 PDF",
			Map.of("classroomId", 30L, "materialId", 10L),
			Instant.parse("2026-08-14T03:00:00Z")
		);

		assertThat(inserted).isEqualTo(2);
		assertThat(jdbc.queryForList(
			"SELECT user_id FROM notifications ORDER BY user_id",
			Long.class
		)).containsExactly(2L, 3L);
	}

	@Test
	void cleanupDeletesOnlyRowsOlderThanThirtyDayBoundary() {
		DataSource dataSource = dataSource("notification-cleanup");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		createSchema(jdbc);
		jdbc.update("INSERT INTO users(id) VALUES (1)");
		Instant cutoff = Instant.parse("2026-07-15T03:00:00Z");
		insertNotification(jdbc, 1L, cutoff.minusSeconds(1));
		insertNotification(jdbc, 2L, cutoff);
		insertNotification(jdbc, 3L, cutoff.plusSeconds(1));

		int deleted = repository(dataSource).deleteExpired(cutoff, 100);

		assertThat(deleted).isEqualTo(1);
		assertThat(jdbc.queryForList(
			"SELECT id FROM notifications ORDER BY id",
			Long.class
		)).containsExactly(2L, 3L);
	}

	@Test
	void deadlineCandidatesIncludeOnlyActiveUnsubmittedLearners() {
		DataSource dataSource = dataSource("notification-deadline-candidates");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		createSchema(jdbc);
		jdbc.update("""
			INSERT INTO users(id, role, status) VALUES
			(1, 'LEARNER', 'ACTIVE'),
			(2, 'LEARNER', 'ACTIVE'),
			(3, 'INSTRUCTOR', 'ACTIVE'),
			(4, 'LEARNER', 'DELETED')
			""");
		jdbc.update("""
			INSERT INTO classroom_members(classroom_id, user_id) VALUES
			(30, 1), (30, 2), (30, 3), (30, 4)
			""");
		jdbc.update("""
			INSERT INTO exams(id, classroom_id, title, status, due_at) VALUES
			(10, 30, 'Included', 'PUBLISHED', '2026-08-15 03:00:00'),
			(11, 30, 'Closed', 'CLOSED', '2026-08-15 03:00:00'),
			(12, 30, 'No due date', 'PUBLISHED', NULL)
			""");
		jdbc.update("""
			INSERT INTO exam_submissions(id, exam_id, user_id, status)
			VALUES (100, 10, 2, 'GRADING_FAILED')
			""");
		NotificationBulkRepository repository = repository(dataSource);

		assertThat(repository.findClassroomLearnerUserIds(30L))
			.containsExactly(1L, 2L);
		assertThat(repository.findUnsubmittedExamDeadlineCandidates(
			Instant.parse("2026-08-14T15:00:00Z"),
			Instant.parse("2026-08-15T15:00:00Z")
		)).containsExactly(new ExamDeadlineNotificationCandidate(
			10L, 30L, "Included", 1L
		));
	}

	@Test
	void sameDeadlineRunTwiceIsStoppedByUniqueDedupKey() {
		DataSource dataSource = dataSource("notification-deadline-dedup");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		createSchema(jdbc);
		jdbc.update("INSERT INTO users(id, role, status) VALUES (1, 'LEARNER', 'ACTIVE')");
		jdbc.update("INSERT INTO classroom_members(classroom_id, user_id) VALUES (30, 1)");
		jdbc.update("""
			INSERT INTO exams(id, classroom_id, title, status, due_at)
			VALUES (10, 30, 'D-1 exam', 'PUBLISHED', '2026-08-15 03:00:00')
			""");
		NotificationBulkRepository repository = repository(dataSource);
		NotificationTriggerService service = new NotificationTriggerService(
			repository,
			Mockito.mock(io.edupilot.classroom.ClassroomNoticeRepository.class),
			new DeduplicatedNotificationWriter(repository),
			java.time.Clock.fixed(
				Instant.parse("2026-08-14T03:00:00Z"),
				java.time.ZoneOffset.UTC
			)
		);

		assertThat(service.publishExamDeadlineNotifications(
			Instant.parse("2026-08-14T03:00:00Z")
		)).isEqualTo(1);
		assertThat(service.publishExamDeadlineNotifications(
			Instant.parse("2026-08-14T03:00:00Z")
		)).isZero();
		assertThat(jdbc.queryForObject(
			"SELECT COUNT(*) FROM notifications", Integer.class
		)).isEqualTo(1);

		assertThatThrownBy(() -> repository.insertForUserDeduplicated(
			1L,
			NotificationType.EXAM_DEADLINE_APPROACHING,
			"title",
			"body",
			Map.of("classroomId", 30L, "examId", 10L),
			"EXAM_DEADLINE:10:1:D1",
			Instant.parse("2026-08-14T03:00:00Z")
		)).isInstanceOf(DuplicateKeyException.class);
	}

	private DataSource dataSource(String name) {
		return new EmbeddedDatabaseBuilder()
			.setType(EmbeddedDatabaseType.H2)
			.setName(name + ";MODE=MySQL;DB_CLOSE_DELAY=-1")
			.build();
	}

	private NotificationBulkRepository repository(DataSource dataSource) {
		return new NotificationBulkRepository(
			new NamedParameterJdbcTemplate(dataSource),
			new ObjectMapper()
		);
	}

	private void createSchema(JdbcTemplate jdbc) {
		jdbc.execute("""
			CREATE TABLE users (
			    id BIGINT PRIMARY KEY,
			    role VARCHAR(20) NOT NULL DEFAULT 'LEARNER',
			    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
			)
			""");
		jdbc.execute("CREATE TABLE classroom_members (classroom_id BIGINT NOT NULL, user_id BIGINT NOT NULL)");
		jdbc.execute("""
			CREATE TABLE exams (
			    id BIGINT PRIMARY KEY,
			    classroom_id BIGINT NOT NULL,
			    title VARCHAR(200) NOT NULL,
			    status VARCHAR(20) NOT NULL,
			    due_at TIMESTAMP NULL
			)
			""");
		jdbc.execute("""
			CREATE TABLE exam_submissions (
			    id BIGINT PRIMARY KEY,
			    exam_id BIGINT NOT NULL,
			    user_id BIGINT NOT NULL,
			    status VARCHAR(20) NOT NULL
			)
			""");
		jdbc.execute("""
			CREATE TABLE notifications (
			    id BIGINT AUTO_INCREMENT PRIMARY KEY,
			    user_id BIGINT NOT NULL,
			    type VARCHAR(40) NOT NULL,
			    title VARCHAR(200) NOT NULL,
			    body CLOB NOT NULL,
			    link_json VARCHAR(1000) NOT NULL,
			    read_at TIMESTAMP NULL,
			    dedup_key VARCHAR(120) NULL,
			    created_at TIMESTAMP NOT NULL
			    , CONSTRAINT uk_notifications_dedup UNIQUE (dedup_key)
			)
			""");
	}

	private void insertNotification(JdbcTemplate jdbc, Long id, Instant createdAt) {
		jdbc.update("""
			INSERT INTO notifications (
			    id, user_id, type, title, body, link_json, created_at
			) VALUES (?, 1, 'MATERIAL_UPLOADED', 'title', 'body', '{}', ?)
			""", id, createdAt);
	}
}
