package io.edupilot.admin;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.edupilot.admin.dto.AdminPasswordResetResponse;
import io.edupilot.admin.dto.AdminUserDetailResponse;
import io.edupilot.admin.dto.AdminUserListResponse;
import io.edupilot.auth.AuthenticatedUser;
import io.edupilot.global.response.ApiResponse;
import io.edupilot.user.UserRole;
import io.edupilot.user.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Admin Users")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

	private final AdminUserService adminUserService;

	public AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@GetMapping
	@Operation(summary = "관리자 회원 목록 조회")
	public ApiResponse<AdminUserListResponse> list(
		@RequestParam(required = false) String q,
		@RequestParam(required = false) UserRole role,
		@RequestParam(required = false) UserStatus status,
		@RequestParam(defaultValue = "RECENT") AdminListSort sort,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return ApiResponse.success(adminUserService.list(
			q,
			role,
			status,
			sort,
			page,
			size
		));
	}

	@GetMapping("/{id}")
	@Operation(summary = "관리자 회원 상세 조회")
	public ApiResponse<AdminUserDetailResponse> detail(
		@PathVariable("id") Long userId
	) {
		return ApiResponse.success(adminUserService.detail(userId));
	}

	/**
	 * 관리자 조회 전용 원칙의 명시적 예외다. 기존 운영 수작업을 안전한 경로로
	 * 대체하고 대상 사용자가 다음 로그인에서 즉시 인지하도록 한다.
	 */
	@PostMapping("/{id}/password-reset")
	@Operation(summary = "관리자 사용자 비밀번호 초기화")
	public ResponseEntity<ApiResponse<AdminPasswordResetResponse>> resetPassword(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable("id") Long targetUserId
	) {
		AdminPasswordResetResponse response = adminUserService.resetPassword(
			authenticatedUser.userId(),
			targetUserId
		);
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore().cachePrivate())
			.body(ApiResponse.success(response));
	}
}
