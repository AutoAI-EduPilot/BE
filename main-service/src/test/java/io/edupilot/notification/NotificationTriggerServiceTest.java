package io.edupilot.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import io.edupilot.classroom.Classroom;
import io.edupilot.classroom.ClassroomColor;
import io.edupilot.classroom.ClassroomJoinRequest;
import io.edupilot.classroom.ClassroomNotice;
import io.edupilot.classroom.ClassroomNoticeRepository;
import io.edupilot.user.User;
import io.edupilot.user.UserRole;

@ExtendWith(MockitoExtension.class)
class NotificationTriggerServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-14T03:00:00Z");

	@Mock
	private NotificationBulkRepository bulkRepository;
	@Mock
	private ClassroomNoticeRepository noticeRepository;
	@Mock
	private DeduplicatedNotificationWriter deduplicatedWriter;

	private NotificationTriggerService service;
	private Classroom classroom;
	private User learner;

	@BeforeEach
	void setUp() {
		service = new NotificationTriggerService(
			bulkRepository,
			noticeRepository,
			deduplicatedWriter,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		User instructor = user(1L, "teacher@example.com", "Teacher", UserRole.INSTRUCTOR);
		learner = user(2L, "learner@example.com", "Learner", UserRole.LEARNER);
		classroom = Classroom.create(
			instructor,
			"AI Basics",
			LocalDate.of(2026, 8, 1),
			LocalDate.of(2026, 8, 31),
			ClassroomColor.BLUE,
			null,
			"AAAA-BBBB"
		);
		ReflectionTestUtils.setField(classroom, "id", 30L);
	}

	@Test
	void materialAndJoinTriggersUseExpectedRecipientsAndLinks() {
		service.materialUploaded(30L, 10L, "Week 1 PDF");

		verify(bulkRepository).insertForClassroomMembers(
			eq(30L),
			eq(NotificationType.MATERIAL_UPLOADED),
			any(),
			eq("Week 1 PDF"),
			org.mockito.ArgumentMatchers.argThat(link ->
				link.get("classroomId").equals(30L)
					&& link.get("materialId").equals(10L)
			),
			eq(NOW)
		);

		ClassroomJoinRequest request = ClassroomJoinRequest.create(
			classroom, learner, NOW
		);
		ReflectionTestUtils.setField(request, "id", 50L);
		service.joinRequestReceived(request);
		verify(bulkRepository).insertForUser(
			eq(1L),
			eq(NotificationType.JOIN_REQUEST_RECEIVED),
			any(),
			eq("Learner"),
			any(),
			eq(NOW)
		);

		request.approve(NOW);
		service.joinRequestProcessed(request);
		verify(bulkRepository).insertForUser(
			eq(2L),
			eq(NotificationType.JOIN_REQUEST_PROCESSED),
			eq("강의실 입장 요청이 승인되었습니다"),
			eq("AI Basics"),
			any(),
			eq(NOW)
		);
	}

	@Test
	void rejectedJoinRequestNotifiesStudentWithProcessedType() {
		ClassroomJoinRequest request = ClassroomJoinRequest.create(
			classroom, learner, NOW.minusSeconds(60)
		);
		ReflectionTestUtils.setField(request, "id", 51L);
		request.reject(NOW);

		service.joinRequestProcessed(request);

		verify(bulkRepository).insertForUser(
			eq(2L),
			eq(NotificationType.JOIN_REQUEST_PROCESSED),
			eq("강의실 입장 요청이 거절되었습니다"),
			eq("AI Basics"),
			any(),
			eq(NOW)
		);
	}

	@Test
	void scheduledNoticeIsPublishedOnceAfterDueTime() {
		ClassroomNotice notice = notice(NOW.minusSeconds(1));
		when(noticeRepository.findNotificationCandidates(
			eq(NOW), any(Pageable.class)
		)).thenReturn(List.of(notice));

		service.publishDueNotices(NOW, 100);
		service.publishDueNotices(NOW, 100);

		verify(bulkRepository, times(1)).insertForClassroomMembers(
			eq(30L),
			eq(NotificationType.NOTICE_PUBLISHED),
			eq("Notice"),
			eq("Content"),
			any(),
			eq(NOW)
		);
		assertThat(notice.getNotificationSentAt()).isEqualTo(NOW);
	}

	@Test
	void futureNoticeDoesNotPublish() {
		service.noticePublished(notice(NOW.plusSeconds(1)), NOW);

		verify(bulkRepository, never()).insertForClassroomMembers(
			any(), any(), any(), any(), any(), any()
		);
	}

	@Test
	void examPublishedTargetsOnlySelectedLearnersWithCentralDedupKeys() {
		when(bulkRepository.findClassroomLearnerUserIds(30L))
			.thenReturn(List.of(2L, 3L));

		assertThat(service.examPublished(30L, 80L, "Midterm")).isEqualTo(2);

		ArgumentCaptor<Long> users = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
		verify(deduplicatedWriter, times(2)).insert(
			users.capture(),
			eq(NotificationType.EXAM_PUBLISHED),
			any(),
			eq("Midterm"),
			org.mockito.ArgumentMatchers.argThat(link ->
				link.get("classroomId").equals(30L)
					&& link.get("examId").equals(80L)
			),
			keys.capture(),
			eq(NOW)
		);
		assertThat(users.getAllValues()).containsExactly(2L, 3L);
		assertThat(keys.getAllValues()).containsExactly(
			"EXAM_PUBLISHED:80:2",
			"EXAM_PUBLISHED:80:3"
		);
	}

	@Test
	void deadlineWindowsUseKstDatesAndD3D1DedupKeys() {
		Instant boundaryNow = Instant.parse("2026-08-14T15:30:00Z");
		Instant d3From = Instant.parse("2026-08-17T15:00:00Z");
		Instant d3Until = Instant.parse("2026-08-18T15:00:00Z");
		Instant d1From = Instant.parse("2026-08-15T15:00:00Z");
		Instant d1Until = Instant.parse("2026-08-16T15:00:00Z");
		when(bulkRepository.findUnsubmittedExamDeadlineCandidates(
			d3From, d3Until
		)).thenReturn(List.of(new ExamDeadlineNotificationCandidate(
			80L, 30L, "Midterm", 2L
		)));
		when(bulkRepository.findUnsubmittedExamDeadlineCandidates(
			d1From, d1Until
		)).thenReturn(List.of(new ExamDeadlineNotificationCandidate(
			81L, 30L, "Final", 3L
		)));

		assertThat(service.publishExamDeadlineNotifications(boundaryNow)).isEqualTo(2);

		ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
		verify(deduplicatedWriter, times(2)).insert(
			any(),
			eq(NotificationType.EXAM_DEADLINE_APPROACHING),
			any(),
			any(),
			any(),
			keys.capture(),
			eq(boundaryNow)
		);
		assertThat(keys.getAllValues()).containsExactly(
			"EXAM_DEADLINE:80:2:D3",
			"EXAM_DEADLINE:81:3:D1"
		);
	}

	@Test
	void duplicateAndOtherNotificationFailuresAreFailSoft() {
		when(bulkRepository.findClassroomLearnerUserIds(30L))
			.thenReturn(List.of(2L, 3L));
		doThrow(new DuplicateKeyException("duplicate"))
			.doThrow(new IllegalStateException("storage unavailable"))
			.when(deduplicatedWriter).insert(
				any(), any(), any(), any(), any(), any(), any()
			);

		assertThat(service.examPublished(30L, 80L, "Midterm")).isZero();
	}

	private ClassroomNotice notice(Instant publishAt) {
		ClassroomNotice notice = ClassroomNotice.create(
			classroom,
			"Notice",
			"Content",
			2,
			publishAt,
			NOW.minusSeconds(60)
		);
		ReflectionTestUtils.setField(notice, "id", 70L);
		return notice;
	}

	private User user(Long id, String email, String name, UserRole role) {
		User user = User.create(email, "hash", name, role);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}
}
