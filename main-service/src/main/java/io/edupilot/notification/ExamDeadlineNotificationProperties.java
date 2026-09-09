package io.edupilot.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "edupilot.notification.exam-deadline")
public record ExamDeadlineNotificationProperties(boolean enabled) {
}
