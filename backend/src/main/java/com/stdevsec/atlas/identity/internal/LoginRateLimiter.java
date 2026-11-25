package com.stdevsec.atlas.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Sliding-window limit of login attempts per client IP (T-02). In memory: a
 * single-node, self-hosted deployment does not need a shared store. Account
 * lockout after repeated failures is handled separately on the user record.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_ATTEMPTS = 20;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Records an attempt; returns false if the client exceeded the limit. */
    public boolean tryAcquire(String clientKey) {
        long now = clock.millis();
        Deque<Long> q = attempts.computeIfAbsent(clientKey, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW.toMillis()) {
                q.pollFirst();
            }
            if (q.size() >= MAX_ATTEMPTS) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
