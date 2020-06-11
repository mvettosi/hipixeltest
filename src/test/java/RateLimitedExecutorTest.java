import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class RateLimitedExecutorTest {
    /**
     * Configuration parameters are accepted in the
     * constructor and should be immutable over the lifetime of the instance.
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
}