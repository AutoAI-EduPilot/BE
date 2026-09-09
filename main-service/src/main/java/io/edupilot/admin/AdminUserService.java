package io.edupilot.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.edupilot.admin.dto.AdminPasswordResetResponse;
import io.edupilot.admin.dto.AdminUserDetailResponse;
import io.edupilot.admin.dto.AdminUserListResponse;
import io.edupilot.admin.dto.AdminUserResponse;
import io.edupilot.auth.RefreshTokenService;
import io.edupilot.global.error.BusinessException;
import io.edupilot.global.error.ErrorCode;
import io.edupilot.user.AuthProvider;
import io.edupilot.user.User;
import io.edupilot.user.UserRepository;
import io.edupilot.user.UserRole;
import io.edupilot.user.UserStatus;

@Service
public class AdminUserService {
	private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
	private static final String PASSWORD_RESET_ACTION = "ADMIN_PASSWORD_RESET";
	private static final String PASSWORD_RESET_MESSAGE = "로그인 후 즉시 변경 안내";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokenService;
	private final TemporaryPasswordGenerator temporaryPasswordGenerator;
	private final Clock clock;

	public AdminUserService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		RefreshTokenService refreshTokenService,
		TemporaryPasswordGenerator temporaryPasswordGenerator,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenService = refreshTokenService;
		this.temporaryPasswordGenerator = temporaryPasswordGenerator;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AdminUserListResponse list(
		String query,
		UserRole role,
		UserStatus status,
		AdminUserSort sort,
		int page,
		int size
	) {
		Page<User> users = userRepository.findAdminUsers(
			normalizedQuery(query),
			role,
			status,
			PageRequest.of(page, size, userSort(sort))
		);
		Page<AdminUserResponse> responses = users.map(AdminUserResponse::from);
		return new AdminUserListResponse(
			responses.getContent(),
			responses.getNumber(),
			responses.getSize(),
			responses.getTotalElements(),
			responses.getTotalPages()
		);
	}

	@Transactional(readOnly = true)
	public AdminUserDetailResponse detail(Long userId) {
		return userRepository.findById(userId)
			.map(AdminUserDetailResponse::from)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}

	/**
	 * 관리자 조회 전용 원칙의 명시적 예외다. 운영 중 수작업 비밀번호 변경을
	 * 감사 가능한 안전 경로로 바꾸며, 대상자가 즉시 인지하는 행위만 허용한다.
	 */
	@Transactional
	public AdminPasswordResetResponse resetPassword(Long actorUserId, Long targetUserId) {
		User target = userRepository.findById(targetUserId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (!target.isActive()) {
			throw new BusinessException(ErrorCode.PASSWORD_RESET_NOT_ALLOWED);
		}
		if (target.getAuthProvider() != AuthProvider.LOCAL) {
			throw new BusinessException(ErrorCode.PASSWORD_NOT_SUPPORTED);
		}
		if (actorUserId.equals(targetUserId)) {
			throw new BusinessException(ErrorCode.PASSWORD_RESET_NOT_ALLOWED);
		}

		String temporaryPassword = temporaryPasswordGenerator.generate();
		target.changePassword(passwordEncoder.encode(temporaryPassword));
		userRepository.flush();
		refreshTokenService.revokeAll(targetUserId);
		Instant occurredAt = clock.instant();
		log.atInfo()
			.addKeyValue("actorUserId", actorUserId)
			.addKeyValue("targetUserId", targetUserId)
			.addKeyValue("action", PASSWORD_RESET_ACTION)
			.addKeyValue("occurredAt", occurredAt)
			.log("Administrator reset user password");
		return new AdminPasswordResetResponse(
			temporaryPassword,
			PASSWORD_RESET_MESSAGE
		);
	}

	private String normalizedQuery(String query) {
		if (query == null || query.isBlank()) {
			return null;
		}
		return query.trim().toLowerCase(Locale.ROOT);
	}

	private Sort userSort(AdminUserSort sort) {
		return switch (sort == null ? AdminUserSort.RECENT : sort) {
			case RECENT -> Sort.by(
				Sort.Order.desc("createdAt"),
				Sort.Order.desc("id")
			);
			case NAME -> Sort.by(
				Sort.Order.asc("name"),
				Sort.Order.asc("id")
			);
			case RECENT_ACTIVITY_DESC -> Sort.by(
				Sort.Order.desc("lastActiveAt").nullsLast(),
				Sort.Order.desc("id")
			);
			case RECENT_ACTIVITY_ASC -> Sort.by(
				Sort.Order.asc("lastActiveAt").nullsLast(),
				Sort.Order.asc("id")
			);
		};
	}
}
