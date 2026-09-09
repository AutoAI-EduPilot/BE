package io.edupilot.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ExamNotificationDispatcherTest {

	private NotificationTriggerService triggerService;
	private ExamNotificationDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		triggerService = Mockito.mock(NotificationTriggerService.class);
		dispatcher = new ExamNotificationDispatcher(triggerService);
		TransactionSynchronizationManager.initSynchronization();
	}

	@AfterEach
	void tearDown() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void dispatchesPublishedAndGradedOnlyAfterCommit() {
		dispatcher.publishedAfterCommit(80L, 30L, "Midterm");
		dispatcher.gradedAfterCommit(90L, 80L, 30L, 2L);

		TransactionSynchronizationManager.getSynchronizations()
			.forEach(synchronization -> synchronization.afterCommit());

		verify(triggerService).examPublished(30L, 80L, "Midterm");
		verify(triggerService).examGraded(90L, 80L, 30L, 2L);
	}

	@Test
	void notificationFailureDoesNotEscapeCommittedBusinessFlow() {
		doThrow(new IllegalStateException("notification unavailable"))
			.when(triggerService).examPublished(30L, 80L, "Midterm");
		dispatcher.publishedAfterCommit(80L, 30L, "Midterm");

		assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
			.forEach(synchronization -> synchronization.afterCommit()))
			.doesNotThrowAnyException();
	}
}
