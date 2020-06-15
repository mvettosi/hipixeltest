import exception.RateLimitException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
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
        assertThrows(RateLimitException.class, () -> {
            executor.queue(RateLimitedExecutor.Request.create());
            executor.queue(RateLimitedExecutor.Request.create());
            executor.queue(RateLimitedExecutor.Request.create());
        });
    }

    /**
     * Ensure that the calling thread is blocked until the queue is free to accept new requests.
     */
    @Test
    void queue_PausesCallingThreadWhenQueueIsFull() throws Exception {
        // Arrange
        RateLimitedExecutor executor = new RateLimitedExecutor(10, 2);
        executor.getRequestExecutor().stop(); // Pause the de-queueing process
        CountDownLatch latch = new CountDownLatch(1);
        // Make a test runner that will be blocked trying to enqueue the third request
        Thread testRunner = new Thread(() -> {
            try {
                executor.queue(RateLimitedExecutor.Request.create());
                executor.queue(RateLimitedExecutor.Request.create());
                RateLimitedExecutor.Request thirdRequest = RateLimitedExecutor.Request.create();
                latch.countDown();
                executor.queue(thirdRequest);
            } catch (RateLimitException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        // Act
        testRunner.start();
        latch.await();
        // Note: I am aware that this is ugly, because it's susceptible to cpu load and in some unrealistic scenario
        // it could cause a false failure because the testRunner didn't have enough time to reach and block on the
        // third insertion. However, I was not able to find a better solution in a practical amount of time, so I'll
        // take the remote possibility of false negatives here and focus on the rest of the implementation, and possibly
        // come back to this later. Anyways, 100ms seems a forgiving enough time since the test consistently succeeds
        // even when using only 1ms delay.
        Thread.sleep(100);

        // Assert
        assertThat(testRunner.getState(), is(Thread.State.WAITING));
    }
}