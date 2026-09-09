package io.edupilot.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.edupilot.classroom.ClassroomJoinRequest;
import io.edupilot.classroom.ClassroomJoinRequestStatus;
import io.edupilot.classroom.ClassroomNotice;
import io.edupilot.classroom.ClassroomNoticeRepository;

@Service
public class NotificationTriggerService {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final List<ExamDeadlineReminder> DEADLINE_REMINDERS = List.of(
		ExamDeadlineReminder.D3,
		ExamDeadlineReminder.D1
	);
	private static final Logger log = LoggerFactory.getLogger(
		NotificationTriggerService.class
	);

	private final NotificationBulkRepository bulkRepository;
	private final ClassroomNoticeRepository noticeRepository;
	private final DeduplicatedNotificationWriter deduplicatedWriter;
	private final Clock clock;

	public NotificationTriggerService(
		NotificationBulkRepository bulkRepository,
		ClassroomNoticeRepository noticeRepository,
		DeduplicatedNotificationWriter deduplicatedWriter,
		Clock clock
	) {
		this.bulkRepository = bulkRepository;
		this.noticeRepository = noticeRepository;
		this.deduplicatedWriter = deduplicatedWriter;
		this.clock = clock;
	}

	public void materialUploaded(
		Long classroomId,
		Long materialId,
		String materialTitle
	) {
		bulkRepository.insertForClassroomMembers(
			classroomId,
			NotificationType.MATERIAL_UPLOADED,
			"새 학습 자료가 등록되었습니다",
			materialTitle,
			link("classroomId", classroomId, "materialId", materialId),
			clock.instant()
		);
	}

	public void noticePublished(ClassroomNotice notice, Instant now) {
		if (notice.isNotificationSent() || !notice.isPublished(now)) {
			return;
		}
		bulkRepository.insertForClassroomMembers(
			notice.getClassroom().getId(),
			NotificationType.NOTICE_PUBLISHED,
			notice.getTitle(),
			notice.getContent(),
			link(
				"classroomId", notice.getClassroom().getId(),
				"noticeId", notice.getId()
			),
			now
		);
		notice.markNotificationSent(now);
	}

	public void joinRequestReceived(ClassroomJoinRequest request) {
		bulkRepository.insertForUser(
			request.getClassroom().getInstructorId(),
			NotificationType.JOIN_REQUEST_RECEIVED,
			"새 강의실 입장 요청",
			request.getUser().getName(),
			link(
				"classroomId", request.getClassroom().getId(),
				"joinRequestId", request.getId()
			),
			clock.instant()
		);
	}

	public void joinRequestProcessed(ClassroomJoinRequest request) {
		ClassroomJoinRequestStatus status = request.getStatus();
		String title = status == ClassroomJoinRequestStatus.APPROVED
			? "강의실 입장 요청이 승인되었습니다"
			: "강의실 입장 요청이 거절되었습니다";
		bulkRepository.insertForUser(
			request.getUser().getId(),
			NotificationType.JOIN_REQUEST_PROCESSED,
			title,
			request.getClassroom().getName(),
			link(
				"classroomId", request.getClassroom().getId(),
				"joinRequestId", request.getId()
			),
			clock.instant()
		);
	}

	public int examPublished(
		Long classroomId,
		Long examId,
		String examTitle
	) {
		int created = 0;
		for (Long userId : bulkRepository.findClassroomLearnerUserIds(classroomId)) {
			if (insertDeduplicated(
				userId,
				NotificationType.EXAM_PUBLISHED,
				"새 시험이 공개되었습니다",
				examTitle,
				link("classroomId", classroomId, "examId", examId),
				ExamNotificationDedupKeys.published(examId, userId),
				clock.instant()
			)) {
				created++;
			}
		}
		return created;
	}

	public int publishExamDeadlineNotifications(Instant now) {
		LocalDate today = now.atZone(SEOUL).toLocalDate();
		int created = 0;
		for (ExamDeadlineReminder reminder : DEADLINE_REMINDERS) {
			LocalDate dueDate = today.plusDays(reminder.daysBefore());
			Instant dueFrom = dueDate.atStartOfDay(SEOUL).toInstant();
			Instant dueUntil = dueDate.plusDays(1).atStartOfDay(SEOUL).toInstant();
			for (ExamDeadlineNotificationCandidate candidate
				: bulkRepository.findUnsubmittedExamDeadlineCandidates(
					dueFrom, dueUntil
				)) {
				if (insertDeduplicated(
					candidate.userId(),
					NotificationType.EXAM_DEADLINE_APPROACHING,
					"시험 마감이 다가옵니다",
					candidate.examTitle() + " (" + reminder.displayLabel() + ")",
					link(
						"classroomId", candidate.classroomId(),
						"examId", candidate.examId()
					),
					ExamNotificationDedupKeys.deadline(
						candidate.examId(), candidate.userId(), reminder
					),
					now
				)) {
					created++;
				}
			}
		}
		return created;
	}

	public boolean examGraded(
		Long submissionId,
		Long examId,
		Long classroomId,
		Long userId
	) {
		return insertDeduplicated(
			userId,
			NotificationType.EXAM_GRADED,
			"시험 채점이 완료되었습니다",
			"시험 결과를 확인해 주세요.",
			link("classroomId", classroomId, "examId", examId),
			ExamNotificationDedupKeys.graded(submissionId, userId),
			clock.instant()
		);
	}

	@Transactional
	public int publishDueNotices(Instant now, int limit) {
		var notices = noticeRepository.findNotificationCandidates(
			now,
			PageRequest.of(0, limit)
		);
		for (ClassroomNotice notice : notices) {
			noticePublished(notice, now);
		}
		noticeRepository.flush();
		return notices.size();
	}

	@Transactional
	public int deleteExpired(Instant cutoff, int limit) {
		return bulkRepository.deleteExpired(cutoff, limit);
	}

	private boolean insertDeduplicated(
		Long userId,
		NotificationType type,
		String title,
		String body,
		Map<String, Object> link,
		String dedupKey,
		Instant createdAt
	) {
		try {
			deduplicatedWriter.insert(
				userId, type, title, body, link, dedupKey, createdAt
			);
			return true;
		} catch (DuplicateKeyException exception) {
			log.atDebug()
				.addKeyValue("dedupKey", dedupKey)
				.log("Skipped duplicate notification");
			return false;
		} catch (RuntimeException exception) {
			log.atWarn()
				.addKeyValue("type", type)
				.addKeyValue("userId", userId)
				.addKeyValue("failureType", exception.getClass().getSimpleName())
				.log("Notification creation failed");
			return false;
		}
	}

	private Map<String, Object> link(
		String firstKey,
		Long firstValue,
		String secondKey,
		Long secondValue
	) {
		Map<String, Object> link = new LinkedHashMap<>();
		link.put(firstKey, firstValue);
		link.put(secondKey, secondValue);
		return link;
	}
}
