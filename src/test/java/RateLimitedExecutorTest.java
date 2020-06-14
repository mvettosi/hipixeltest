import exception.RateLimitedRequestException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        // Act
        executor.queue(RateLimitedExecutor.Request.create());
        executor.queue(RateLimitedExecutor.Request.create());
        executor.queue(RateLimitedExecutor.Request.create());

        // Assert
        // No RequestDeniedException
    }

    /**
     * Ensure that the rate limiter rejects requests that are above the configured rate limit.
     */
    @Test()
    void queue_RequestsAboveRateLimit() throws Exception {
        // Arrange
        RateLimitedExecutor executor = new RateLimitedExecutor(2, 10);

        // Act & Assert
        assertThrows(RateLimitedRequestException.class, () -> {
            executor.queue(RateLimitedExecutor.Request.create());
            executor.queue(RateLimitedExecutor.Request.create());
            executor.queue(RateLimitedExecutor.Request.create());
        });
    }
}