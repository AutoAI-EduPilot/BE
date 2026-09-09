package io.edupilot.notification;

public record ExamDeadlineNotificationCandidate(
	Long examId,
	Long classroomId,
	String examTitle,
	Long userId
) {
}
