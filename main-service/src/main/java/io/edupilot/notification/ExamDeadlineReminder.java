package io.edupilot.notification;

enum ExamDeadlineReminder {
	D3(3, "D3", "D-3"),
	D1(1, "D1", "D-1");

	private final int daysBefore;
	private final String keyLabel;
	private final String displayLabel;

	ExamDeadlineReminder(int daysBefore, String keyLabel, String displayLabel) {
		this.daysBefore = daysBefore;
		this.keyLabel = keyLabel;
		this.displayLabel = displayLabel;
	}

	int daysBefore() {
		return daysBefore;
	}

	String keyLabel() {
		return keyLabel;
	}

	String displayLabel() {
		return displayLabel;
	}
}
