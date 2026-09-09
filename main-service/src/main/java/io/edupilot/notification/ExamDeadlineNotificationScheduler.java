package io.edupilot.notification;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExamDeadlineNotificationScheduler {

	private static final Logger log = LoggerFactory.getLogger(
		ExamDeadlineNotificationScheduler.class
	);

	private final NotificationTriggerService triggerService;
	private final ExamDeadlineNotificationProperties properties;
	private final Clock clock;

	public ExamDeadlineNotificationScheduler(
		NotificationTriggerService triggerService,
		ExamDeadlineNotificationProperties properties,
		Clock clock
	) {
		this.triggerService = triggerService;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
	public void notifyApproachingDeadlines() {
		if (!properties.enabled()) {
			return;
		}
		int created = triggerService.publishExamDeadlineNotifications(clock.instant());
		if (created > 0) {
			log.atInfo()
				.addKeyValue("created", created)
				.log("Created approaching exam deadline notifications");
		}
	}
}
