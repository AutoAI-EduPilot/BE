package io.edupilot.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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
	"spring.datasource.url=jdbc:h2:mem:user-last-active-migration;MODE=MySQL;DB_CLOSE_DELAY=-1",
	"spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MainServiceApplication.class)
@SqlGroup({
	@Sql(statements = {
		"create table users (id bigint primary key)",
		"create table refresh_tokens (id bigint primary key, user_id bigint not null, created_at datetime(6) not null)",
		"create table learning_sessions (id bigint primary key, user_id bigint not null, updated_at datetime(6) not null)",
		"create table chat_messages (id bigint primary key, session_id bigint not null, created_at datetime(6) not null)",
		"create table exam_submissions (id bigint primary key, user_id bigint not null, submitted_at datetime(6) not null)",
		"insert into users (id) values (1), (2), (3), (4), (5), (6)",
		"insert into refresh_tokens (id, user_id, created_at) values (1, 1, '2026-01-01 00:00:00'), (2, 5, '2026-01-01 00:00:00')",
		"insert into learning_sessions (id, user_id, updated_at) values (1, 2, '2026-02-01 00:00:00'), (2, 3, '2026-02-01 00:00:00'), (3, 5, '2026-02-01 00:00:00')",
		"insert into chat_messages (id, session_id, created_at) values (1, 2, '2026-03-01 00:00:00'), (2, 3, '2026-03-01 00:00:00')",
		"insert into exam_submissions (id, user_id, submitted_at) values (1, 4, '2026-04-01 00:00:00'), (2, 5, '2026-04-01 00:00:00')"
	}),
	@Sql(scripts = "classpath:db/migration/V37__user_last_active_at.sql")
})
class UserLastActiveMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void backfillsLatestAvailableActivityAndLeavesUnknownUsersNull() {
		List<ActivityRow> rows = jdbcTemplate.query(
			"select id, last_active_at from users order by id",
			(resultSet, rowNumber) -> new ActivityRow(
				resultSet.getLong("id"),
				resultSet.getObject("last_active_at", LocalDateTime.class)
			)
		);

		assertThat(rows).containsExactly(
			new ActivityRow(1L, LocalDateTime.parse("2026-01-01T00:00:00")),
			new ActivityRow(2L, LocalDateTime.parse("2026-02-01T00:00:00")),
			new ActivityRow(3L, LocalDateTime.parse("2026-03-01T00:00:00")),
			new ActivityRow(4L, LocalDateTime.parse("2026-04-01T00:00:00")),
			new ActivityRow(5L, LocalDateTime.parse("2026-04-01T00:00:00")),
			new ActivityRow(6L, null)
		);
		List<String> indexNames = jdbcTemplate.queryForList(
			"""
				select index_name
				from information_schema.indexes
				where table_name = 'USERS'
				""",
			String.class
		);
		assertThat(indexNames).contains("IDX_USERS_LAST_ACTIVE_AT");
	}

	private record ActivityRow(Long userId, LocalDateTime lastActiveAt) {
	}
}
