package io.edupilot.auth.dto;

import io.edupilot.auth.validation.ValidEmail;
import io.edupilot.auth.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
	@ValidEmail
	@Schema(example = "user@example.com")
	String email,

	@ValidPassword
	@Schema(accessMode = Schema.AccessMode.WRITE_ONLY, example = "password123")
	String password,

	@NotBlank(message = "이름은 필수입니다.")
	@Size(max = 100, message = "이름은 100자 이하여야 합니다.")
	@Schema(example = "홍길동")
	String name,

	@NotNull(message = "역할은 필수입니다.")
	@Schema(example = "LEARNER")
	SignupRole role,

	@Size(max = 100, message = "소속은 100자 이하여야 합니다.")
	@Schema(example = "EduPilot University")
	String affiliation,

	@Schema(defaultValue = "false")
	Boolean learningEmailOptIn,

	@Size(max = 50, message = "약관 버전은 50자 이하여야 합니다.")
	@Schema(example = "2026-07-01")
	String termsVersion,

	@Size(max = 50, message = "개인정보 처리방침 버전은 50자 이하여야 합니다.")
	@Schema(example = "2026-07-01")
	String privacyVersion
) {
	public SignupRequest(
		String email,
		String password,
		String name,
		SignupRole role
	) {
		this(email, password, name, role, null, null, null, null);
	}
}
