package io.edupilot.user.dto;

public record PasswordChangeResponse(
	boolean reauthenticationRequired
) {
}
