package io.edupilot.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminPasswordResetResponse(
	@Schema(description = "응답에서 한 번만 제공되는 임시 비밀번호")
	String temporaryPassword,

	String message
) {
}
