package io.edupilot.user.dto;

import io.edupilot.auth.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
	@NotBlank(message = "현재 비밀번호는 필수입니다.")
	@Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
	String currentPassword,

	@ValidPassword
	@Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
	String newPassword
) {
}
