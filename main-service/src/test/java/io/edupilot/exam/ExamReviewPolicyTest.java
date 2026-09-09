package io.edupilot.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExamReviewPolicyTest {

	private final ExamReviewPolicy policy = new ExamReviewPolicy();

	@ParameterizedTest
	@MethodSource("reviewAvailability")
	void reviewAvailabilityMatchesExamAndSubmissionState(
		ExamStatus examStatus,
		boolean allowRetake,
		SubmissionStatus submissionStatus,
		boolean expected
	) {
		Exam exam = mock(Exam.class);
		ExamSubmission submission = mock(ExamSubmission.class);
		when(exam.getStatus()).thenReturn(examStatus);
		when(exam.isAllowRetake()).thenReturn(allowRetake);
		when(submission.getStatus()).thenReturn(submissionStatus);

		assertThat(policy.isReviewAvailable(exam, submission)).isEqualTo(expected);
	}

	private static Stream<Arguments> reviewAvailability() {
		return Stream.of(
			Arguments.of(ExamStatus.CLOSED, true, SubmissionStatus.SUBMITTED, true),
			Arguments.of(ExamStatus.PUBLISHED, true, SubmissionStatus.GRADED, false),
			Arguments.of(ExamStatus.PUBLISHED, false, SubmissionStatus.GRADED, true),
			Arguments.of(ExamStatus.PUBLISHED, false, SubmissionStatus.SUBMITTED, false)
		);
	}
}
