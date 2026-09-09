package io.edupilot.exam;

import org.springframework.stereotype.Component;

@Component
public class ExamReviewPolicy {

	public boolean isReviewAvailable(Exam exam, ExamSubmission mySubmission) {
		return exam.getStatus() == ExamStatus.CLOSED
			|| !exam.isAllowRetake() && mySubmission.getStatus() == SubmissionStatus.GRADED;
	}
}
