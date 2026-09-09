package io.edupilot.notification;

final class ExamNotificationDedupKeys {

	private ExamNotificationDedupKeys() {
	}

	static String published(Long examId, Long userId) {
		return "EXAM_PUBLISHED:" + examId + ":" + userId;
	}

	static String deadline(
		Long examId,
		Long userId,
		ExamDeadlineReminder reminder
	) {
		return "EXAM_DEADLINE:" + examId + ":" + userId
			+ ":" + reminder.keyLabel();
	}

	static String graded(Long submissionId, Long userId) {
		return "EXAM_GRADED:" + submissionId + ":" + userId;
	}
}
