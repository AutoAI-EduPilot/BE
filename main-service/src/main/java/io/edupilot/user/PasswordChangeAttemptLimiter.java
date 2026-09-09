package io.edupilot.user;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import io.edupilot.global.error.BusinessException;
import io.edupilot.global.error.ErrorCode;

@Component
public class PasswordChangeAttemptLimiter {

	private static final int MAX_FAILURES = 5;
	private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

	private final Cache<Long, Integer> failures;

	public PasswordChangeAttemptLimiter() {
		this(System::nanoTime);
	}

	PasswordChangeAttemptLimiter(Ticker ticker) {
		this.failures = Caffeine.newBuilder()
			.expireAfterWrite(FAILURE_WINDOW)
			.ticker(ticker)
			.build();
	}

	public void checkAllowed(Long userId) {
		Integer failureCount = failures.getIfPresent(userId);
		if (failureCount != null && failureCount >= MAX_FAILURES) {
			throw new BusinessException(ErrorCode.PASSWORD_CHANGE_RATE_LIMITED);
		}
	}

	public void recordFailure(Long userId) {
		failures.asMap().merge(userId, 1, Integer::sum);
	}

	public void reset(Long userId) {
		failures.invalidate(userId);
	}
}
