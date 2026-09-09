package io.edupilot.exam;

import java.time.Instant;

import io.edupilot.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "exam_attempt_starts",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_exam_attempt_starts_exam_user",
		columnNames = {"exam_id", "user_id"}
	)
)
public class ExamAttemptStart {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "exam_id", nullable = false)
	private Exam exam;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	protected ExamAttemptStart() {
	}

	private ExamAttemptStart(Exam exam, User user, Instant startedAt) {
		this.exam = exam;
		this.user = user;
		this.startedAt = startedAt;
	}

	public static ExamAttemptStart create(Exam exam, User user, Instant startedAt) {
		return new ExamAttemptStart(exam, user, startedAt);
	}

	public Long getId() {
		return id;
	}

	public Instant getStartedAt() {
		return startedAt;
	}
}
