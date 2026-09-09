package io.edupilot.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ExamNotificationDispatcher {

	private static final Logger log = LoggerFactory.getLogger(
		ExamNotificationDispatcher.class
	);

	private final NotificationTriggerService triggerService;

	public ExamNotificationDispatcher(NotificationTriggerService triggerService) {
		this.triggerService = triggerService;
	}

	public void publishedAfterCommit(
		Long examId,
		Long classroomId,
		String examTitle
	) {
		registerAfterCommit(
			"EXAM_PUBLISHED",
			examId,
			() -> triggerService.examPublished(classroomId, examId, examTitle)
		);
	}

	public void gradedAfterCommit(
		Long submissionId,
		Long examId,
		Long classroomId,
		Long userId
	) {
		// submissionId 기반 dedup으로 worker 재시도와 복구 재처리를 모두 차단합니다.
		registerAfterCommit(
			"EXAM_GRADED",
			submissionId,
			() -> triggerService.examGraded(
				submissionId, examId, classroomId, userId
			)
		);
	}

	private void registerAfterCommit(
		String action,
		Long resourceId,
		Runnable notification
	) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("Exam notification dispatch requires a transaction");
		}
		TransactionSynchronizationManager.registerSynchronization(
			new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					try {
						notification.run();
					} catch (RuntimeException exception) {
						log.atWarn()
							.addKeyValue("action", action)
							.addKeyValue("resourceId", resourceId)
							.addKeyValue(
								"failureType", exception.getClass().getSimpleName()
							)
							.log("Exam notification creation failed after commit");
					}
				}
			}
		);
	}
}
