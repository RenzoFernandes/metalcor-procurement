package com.metalcor.procurement.copilot;

import com.metalcor.procurement.common.TooManyRequestsException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-user sliding-window limit on copilot questions, in memory (single API instance). Each call
 * spends Gemini quota and one of the 3 connections of the read-only pool, so it is capped.
 */
@Component
public class CopilotRateLimiter {

    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();

    private final int maxPerMinute;
    private final LongSupplier nanoClock;
    private final ConcurrentMap<Long, Deque<Long>> callsByUser = new ConcurrentHashMap<>();

    @Autowired
    public CopilotRateLimiter(@Value("${app.copilot.rate-limit-per-minute:10}") int maxPerMinute) {
        this(maxPerMinute, System::nanoTime);
    }

    CopilotRateLimiter(int maxPerMinute, LongSupplier nanoClock) {
        this.maxPerMinute = maxPerMinute;
        this.nanoClock = nanoClock;
    }

    /** Records one call for the user, or throws {@link TooManyRequestsException} when the window is full. */
    public void acquire(long userId) {
        Deque<Long> calls = callsByUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (calls) {
            long now = nanoClock.getAsLong();
            while (!calls.isEmpty() && now - calls.peekFirst() >= WINDOW_NANOS) {
                calls.pollFirst();
            }
            if (calls.size() >= maxPerMinute) {
                long waitNanos = WINDOW_NANOS - (now - calls.peekFirst());
                long retryAfter = Math.max(1, Duration.ofNanos(waitNanos).toSeconds() + 1);
                throw new TooManyRequestsException(
                        "Limite de " + maxPerMinute + " perguntas por minuto atingido. Tente novamente em "
                                + retryAfter + " segundos.",
                        retryAfter);
            }
            calls.addLast(now);
        }
    }
}
