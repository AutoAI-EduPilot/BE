package io.edupilot.notification;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeduplicatedNotificationWriter {

	private final NotificationBulkRepository bulkRepository;

	public DeduplicatedNotificationWriter(NotificationBulkRepository bulkRepository) {
		this.bulkRepository = bulkRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void insert(
		Long userId,
		NotificationType type,
		String title,
		String body,
		Map<String, Object> link,
		String dedupKey,
		Instant createdAt
	) {
		bulkRepository.insertForUserDeduplicated(
			userId, type, title, body, link, dedupKey, createdAt
		);
	}
}
