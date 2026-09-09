package io.edupilot.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;

import io.edupilot.MainServiceApplication;

@JdbcTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:exam-timing-migration;MODE=MySQL;DB_CLOSE_DELAY=-1",
	"spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MainServiceApplication.class)
@SqlGroup({
	@Sql(statements = {
		"create table users (id bigint primary key)",
		"create table exams (id bigint primary key)",
		"create table exam_submissions (id bigint primary key, submitted_at datetime(6) not null)",
		"insert into users (id) values (1)",
		"insert into exams (id) values (10)",
		"insert into exam_submissions (id, submitted_at) values (100, '2026-09-09 00:00:00')"
	}),
	@Sql(scripts = "classpath:db/migration/V38__exam_review_timing.sql")
})
class ExamTimingMigrationTest {

	@Autowired private JdbcTemplate jdbcTemplate;

	@Test
	void createsUniquePendingStartsAndLeavesExistingSubmissionTimingNull() {
		jdbcTemplate.update(
			"insert into exam_attempt_starts (exam_id, user_id, started_at) values (10, 1, '2026-09-09 01:00:00')"
		);

		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from exam_attempt_starts", Integer.class
		)).isOne();
		assertThatThrownBy(() -> jdbcTemplate.update(
			"insert into exam_attempt_starts (exam_id, user_id, started_at) values (10, 1, '2026-09-09 02:00:00')"
		)).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbcTemplate.queryForObject(
			"select started_at from exam_submissions where id = 100", Object.class
		)).isNull();
		assertThat(jdbcTemplate.queryForObject(
			"select duration_seconds from exam_submissions where id = 100", Integer.class
		)).isNull();

		List<String> constraints = jdbcTemplate.queryForList(
			"select constraint_name from information_schema.table_constraints where table_name = 'EXAM_ATTEMPT_STARTS'",
			String.class
		);
		assertThat(constraints)
			.contains("UK_EXAM_ATTEMPT_STARTS_EXAM_USER")
			.contains("FK_EXAM_ATTEMPT_STARTS_EXAM")
			.contains("FK_EXAM_ATTEMPT_STARTS_USER");
	}
}
