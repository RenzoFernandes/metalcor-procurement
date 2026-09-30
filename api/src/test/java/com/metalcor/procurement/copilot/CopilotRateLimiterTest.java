package com.metalcor.procurement.copilot;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.metalcor.procurement.common.TooManyRequestsException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CopilotRateLimiterTest {

    private final AtomicLong now = new AtomicLong();
    private final CopilotRateLimiter limiter = new CopilotRateLimiter(3, now::get);

    @Test
    void allowsUpToTheLimitThenBlocks() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(1);
        }
        assertThatThrownBy(() -> limiter.acquire(1)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void windowSlidesAndUsersAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(1);
        }
        assertThatCode(() -> limiter.acquire(2)).doesNotThrowAnyException();

        now.addAndGet(Duration.ofSeconds(61).toNanos());
        assertThatCode(() -> limiter.acquire(1)).doesNotThrowAnyException();
    }
}
