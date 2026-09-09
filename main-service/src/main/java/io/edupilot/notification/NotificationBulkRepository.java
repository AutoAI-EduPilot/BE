package io.edupilot.notification;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.ObjectMapper;

@Repository
public class NotificationBulkRepository {

	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public NotificationBulkRepository(
		NamedParameterJdbcTemplate jdbcTemplate,
		ObjectMapper objectMapper
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public int insertForClassroomMembers(
		Long classroomId,
		NotificationType type,
		String title,
		String body,
		Map<String, Object> link,
		Instant createdAt
	) {
		return jdbcTemplate.update("""
			INSERT INTO notifications (
			    user_id, type, title, body, link_json, created_at
			)
			SELECT member.user_id, :type, :title, :body, :linkJson, :createdAt
			FROM classroom_members member
			WHERE member.classroom_id = :classroomId
			""", parameters(type, title, body, link, createdAt)
			.addValue("classroomId", classroomId));
	}

	public int insertForUser(
		Long userId,
		NotificationType type,
		String title,
		String body,
		Map<String, Object> link,
		Instant createdAt
	) {
		return jdbcTemplate.update("""
			INSERT INTO notifications (
			    user_id, type, title, body, link_json, created_at
			)
			VALUES (
			    :userId, :type, :title, :body, :linkJson, :createdAt
			)
			""", parameters(type, title, body, link, createdAt)
			.addValue("userId", userId));
	}

	public int insertForUserDeduplicated(
		Long userId,
		NotificationType type,
		String title,
		String body,
		Map<String, Object> link,
		String dedupKey,
		Instant createdAt
	) {
		return jdbcTemplate.update("""
			INSERT INTO notifications (
			    user_id, type, title, body, link_json, dedup_key, created_at
			)
			VALUES (
			    :userId, :type, :title, :body, :linkJson, :dedupKey, :createdAt
			)
			""", parameters(type, title, body, link, createdAt)
			.addValue("userId", userId)
			.addValue("dedupKey", dedupKey));
	}

	public List<Long> findClassroomLearnerUserIds(Long classroomId) {
		return jdbcTemplate.queryForList("""
			SELECT member.user_id
			FROM classroom_members member
			JOIN users learner ON learner.id = member.user_id
			WHERE member.classroom_id = :classroomId
			  AND learner.role = 'LEARNER'
			  AND learner.status = 'ACTIVE'
			ORDER BY member.user_id
			""", new MapSqlParameterSource("classroomId", classroomId), Long.class);
	}

	public List<ExamDeadlineNotificationCandidate> findUnsubmittedExamDeadlineCandidates(
		Instant dueFrom,
		Instant dueUntil
	) {
		return jdbcTemplate.query("""
			SELECT exam.id AS exam_id,
			       exam.classroom_id,
			       exam.title AS exam_title,
			       member.user_id
			FROM exams exam
			JOIN classroom_members member
			  ON member.classroom_id = exam.classroom_id
			JOIN users learner
			  ON learner.id = member.user_id
			WHERE exam.status = 'PUBLISHED'
			  AND exam.due_at >= :dueFrom
			  AND exam.due_at < :dueUntil
			  AND learner.role = 'LEARNER'
			  AND learner.status = 'ACTIVE'
			  AND NOT EXISTS (
			      SELECT 1
			      FROM exam_submissions submission
			      WHERE submission.exam_id = exam.id
			        AND submission.user_id = member.user_id
			  )
			ORDER BY exam.id, member.user_id
			""", new MapSqlParameterSource()
			.addValue("dueFrom", dueFrom)
			.addValue("dueUntil", dueUntil),
			(resultSet, rowNumber) -> new ExamDeadlineNotificationCandidate(
				resultSet.getLong("exam_id"),
				resultSet.getLong("classroom_id"),
				resultSet.getString("exam_title"),
				resultSet.getLong("user_id")
			));
	}

	public int deleteExpired(Instant cutoff, int limit) {
		return jdbcTemplate.update("""
			DELETE FROM notifications
			WHERE id IN (
			    SELECT id
			    FROM (
			        SELECT id
			        FROM notifications
			        WHERE created_at < :cutoff
			        ORDER BY created_at, id
			        LIMIT :limit
			    ) expired
			)
			""", new MapSqlParameterSource()
			.addValue("cutoff", cutoff)
			.addValue("limit", limit));
	}

	private MapSqlParameterSource parameters(
		NotificationType type,
		String title,
		String body,
		Map<String, Object> link,
		Instant createdAt
	) {
		return new MapSqlParameterSource()
			.addValue("type", type.name())
			.addValue("title", title)
			.addValue("body", body)
			.addValue("linkJson", objectMapper.writeValueAsString(link))
			.addValue("createdAt", createdAt);
	}
}
