package io.edupilot.user;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

@Component
public class UserActivityTracker {

	private static final Logger log = LoggerFactory.getLogger(UserActivityTracker.class);
	private static final Duration UPDATE_INTERVAL = Duration.ofMinutes(5);

	private final UserRepository userRepository;
	private final Clock clock;
	private final Cache<Long, Boolean> recentlyTracked;

	@Autowired
	public UserActivityTracker(UserRepository userRepository, Clock clock) {
		this(userRepository, clock, Ticker.systemTicker());
	}

	UserActivityTracker(UserRepository userRepository, Clock clock, Ticker ticker) {
		this.userRepository = userRepository;
		this.clock = clock;
		this.recentlyTracked = Caffeine.newBuilder()
			.expireAfterWrite(UPDATE_INTERVAL)
			.ticker(ticker)
			.build();
	}

	public void track(Long userId) {
		if (recentlyTracked.asMap().putIfAbsent(userId, Boolean.TRUE) != null) {
			return;
		}
		try {
			userRepository.updateLastActiveAt(userId, clock.instant());
		} catch (RuntimeException exception) {
			log.warn("Failed to update user activity userId={}", userId, exception);
		}
	}
}
