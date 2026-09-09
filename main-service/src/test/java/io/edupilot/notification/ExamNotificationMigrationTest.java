package io.edupilot.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;

import io.edupilot.MainServiceApplication;

@JdbcTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:exam-notification-migration;MODE=MySQL;DB_CLOSE_DELAY=-1",
	"spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MainServiceApplication.class)
@SqlGroup({
	@Sql(statements = {
		"create table exams (id bigint primary key)",
		"create table notifications (id bigint primary key, type varchar(40) not null, "
			+ "constraint chk_notifications_type check (type in ('MATERIAL_UPLOADED', "
			+ "'NOTICE_PUBLISHED', 'JOIN_REQUEST_RECEIVED', 'JOIN_REQUEST_PROCESSED')))"
	}),
	@Sql(scripts = "classpath:db/migration/V39__exam_due_at_notifications.sql")
})
class ExamNotificationMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void v39RunsInMysqlModeAndAddsDueDateAndDedupContract() {
		List<String> examColumns = jdbcTemplate.queryForList(
			"select column_name from information_schema.columns where table_name = 'EXAMS'",
			String.class
		);
		List<String> notificationColumns = jdbcTemplate.queryForList(
			"select column_name from information_schema.columns where table_name = 'NOTIFICATIONS'",
			String.class
		);
		List<String> indexNames = jdbcTemplate.queryForList(
			"select index_name from information_schema.indexes where table_name = 'NOTIFICATIONS'",
			String.class
		);

		assertThat(examColumns).contains("DUE_AT");
		assertThat(notificationColumns).contains("DEDUP_KEY");
		assertThat(indexNames).contains("UK_NOTIFICATIONS_DEDUP");
		jdbcTemplate.update(
			"insert into notifications(id, type, dedup_key) values (1, 'EXAM_PUBLISHED', 'key-1')"
		);
		assertThat(jdbcTemplate.queryForObject(
			"select type from notifications where id = 1", String.class
		)).isEqualTo("EXAM_PUBLISHED");
	}
}
