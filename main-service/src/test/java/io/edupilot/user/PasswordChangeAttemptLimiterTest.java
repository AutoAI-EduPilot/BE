package io.edupilot.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Ticker;

import io.edupilot.global.error.BusinessException;
import io.edupilot.global.error.ErrorCode;

class PasswordChangeAttemptLimiterTest {

	private static final Long USER_ID = 1L;

	@Test
	void rejectsRequestsAfterFiveFailuresAndAllowsRetryAfterFifteenMinutes() {
		MutableTicker ticker = new MutableTicker();
		PasswordChangeAttemptLimiter limiter = new PasswordChangeAttemptLimiter(ticker);

		for (int attempt = 0; attempt < 5; attempt++) {
			limiter.checkAllowed(USER_ID);
			limiter.recordFailure(USER_ID);
		}

		assertThatThrownBy(() -> limiter.checkAllowed(USER_ID))
			.isInstanceOfSatisfying(
				BusinessException.class,
				exception -> org.assertj.core.api.Assertions.assertThat(
					exception.errorCode()
				).isEqualTo(ErrorCode.PASSWORD_CHANGE_RATE_LIMITED)
			);

		ticker.advance(Duration.ofMinutes(15).plusNanos(1));

		assertThatCode(() -> limiter.checkAllowed(USER_ID)).doesNotThrowAnyException();
	}

	@Test
	void successfulResetClearsFailureCount() {
		PasswordChangeAttemptLimiter limiter = new PasswordChangeAttemptLimiter(
			new MutableTicker()
		);
		for (int attempt = 0; attempt < 5; attempt++) {
			limiter.recordFailure(USER_ID);
		}

		limiter.reset(USER_ID);

		assertThatCode(() -> limiter.checkAllowed(USER_ID)).doesNotThrowAnyException();
	}

	private static final class MutableTicker implements Ticker {

		private final AtomicLong nanos = new AtomicLong();

		@Override
		public long read() {
			return nanos.get();
		}

		void advance(Duration duration) {
			nanos.addAndGet(duration.toNanos());
		}
	}
}
