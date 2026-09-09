package io.edupilot.notification;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExamDeadlineNotificationSchedulerTest {

	private static final Instant NOW = Instant.parse("2026-08-14T03:00:00Z");

	@Test
	void enabledSchedulerUsesCurrentInstant() {
		NotificationTriggerService triggerService = Mockito.mock(
			NotificationTriggerService.class
		);
		var scheduler = new ExamDeadlineNotificationScheduler(
			triggerService,
			new ExamDeadlineNotificationProperties(true),
			Clock.fixed(NOW, ZoneOffset.UTC)
		);

		scheduler.notifyApproachingDeadlines();

		verify(triggerService).publishExamDeadlineNotifications(NOW);
	}

	@Test
	void disabledSchedulerDoesNotScan() {
		NotificationTriggerService triggerService = Mockito.mock(
			NotificationTriggerService.class
		);
		var scheduler = new ExamDeadlineNotificationScheduler(
			triggerService,
			new ExamDeadlineNotificationProperties(false),
			Clock.fixed(NOW, ZoneOffset.UTC)
		);

		scheduler.notifyApproachingDeadlines();

		verify(triggerService, never()).publishExamDeadlineNotifications(NOW);
	}
}
