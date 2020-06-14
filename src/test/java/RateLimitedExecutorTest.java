import exception.RequestDeniedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class RateLimitedExecutorTest {
    /**
     * Configuration parameters are accepted in the constructor and should be immutable over the lifetime of the
     * instance.
     */
    @Test
    void RateLimitedExecutor_constructorParameters() {
        // Arrange
        int requestsPerMinute = 58;
        int maxQueueSize = 23;

        // Act
        RateLimitedExecutor executor = new RateLimitedExecutor(requestsPerMinute, maxQueueSize);

        // Assert
        assertThat(executor.getRequestsPerMinute(), equalTo(requestsPerMinute));
        assertThat(executor.getMaxQueueSize(), equalTo(maxQueueSize));
    }

    /**
     * Ensure that the rate limiter executes immediately a number of requests that is below the configured rate limit.
     */
    @Test
    void queue_RequestsBelowRateLimit() throws Exception {
        // Arrange
        RateLimitedExecutor executor = new RateLimitedExecutor(10, 10);
        int executed = 0;

        // Act
        executor.queue(RateLimitedExecutor.Request.create());
        executor.queue(RateLimitedExecutor.Request.create());
        executor.queue(RateLimitedExecutor.Request.create());

        // Assert
        // No RequestDeniedException
    }
}