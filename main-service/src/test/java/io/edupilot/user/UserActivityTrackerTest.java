package io.edupilot.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.benmanes.caffeine.cache.Ticker;

@ExtendWith(MockitoExtension.class)
class UserActivityTrackerTest {

	private static final Long USER_ID = 10L;
	private static final Instant START = Instant.parse("2026-09-09T00:00:00Z");

	@Mock
	private UserRepository userRepository;

	private MutableTicker ticker;
	private MutableClock clock;
	private UserActivityTracker tracker;

	@BeforeEach
	void setUp() {
		ticker = new MutableTicker();
		clock = new MutableClock(START);
		tracker = new UserActivityTracker(userRepository, clock, ticker);
	}

	@Test
	void updatesAtMostOncePerFiveMinutesAndUpdatesAgainAfterWindow() {
		tracker.track(USER_ID);
		tracker.track(USER_ID);

		verify(userRepository, times(1)).updateLastActiveAt(USER_ID, START);

		advance(Duration.ofMinutes(5));
		tracker.track(USER_ID);

		verify(userRepository).updateLastActiveAt(
			USER_ID,
			START.plus(Duration.ofMinutes(5))
		);
	}

	@Test
	void swallowsUpdateFailure() {
		when(userRepository.updateLastActiveAt(USER_ID, START))
			.thenThrow(new IllegalStateException("database unavailable"));

		assertThatCode(() -> tracker.track(USER_ID)).doesNotThrowAnyException();
	}

	private void advance(Duration duration) {
		ticker.advance(duration);
		clock.advance(duration);
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

	private static final class MutableClock extends Clock {
		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}

		void advance(Duration duration) {
			instant = instant.plus(duration);
		}
	}
}
