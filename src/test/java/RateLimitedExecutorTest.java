import exception.RateLimitException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class RateLimitedExecutorTest {
    private static final BiConsumer<String, Throwable> waitForever = (msg, err) -> {
        try {
            Thread.sleep(60000);
        } catch (InterruptedException e) {
            fail();
        }
    };

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
        // Ensure only one thread is in the pool in order to pause it and make the queue stuck
        executor.getRequestExecutor().setCorePoolSize(0);
        executor.getRequestExecutor().setMaximumPoolSize(1);
        CountDownLatch latch = new CountDownLatch(1);
        // Make a test runner that will be blocked trying to enqueue the third request
        Thread testRunner = new Thread(() -> {
            try {
                // This will be removed from the queue and will pause the thread
                executor.queue(RateLimitedExecutor.Request.create()).whenComplete(waitForever);
                // These two will fill up the queue
                executor.queue(RateLimitedExecutor.Request.create());
                executor.queue(RateLimitedExecutor.Request.create());
                // This should block the current thread
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

    /**
     * Check that the queue method is thread safe
     */
    @Test
    void queue_ThreadSafety() throws Exception {
        // Arrange
        final int requestsToBeAccepted = 2;
        final int requestsToBeRejected = 8;
        final int totalRequests = requestsToBeAccepted + requestsToBeRejected;
        RateLimitedExecutor executor = new RateLimitedExecutor(requestsToBeAccepted, 100);
        AtomicInteger acceptedRequests = new AtomicInteger(0);
        AtomicInteger rateLimitedRequests = new AtomicInteger(0);
        CountDownLatch countDownLatch = new CountDownLatch(totalRequests + 1);
        List<Thread> lastStopContenders = new ArrayList<>();
        IntStream.rangeClosed(1, totalRequests).forEach(i -> lastStopContenders.add(new Thread(() -> {
            try {
                countDownLatch.countDown();
                countDownLatch.await();
                executor.queue(RateLimitedExecutor.Request.create());
                acceptedRequests.getAndIncrement();
            } catch (RateLimitException e) {
                rateLimitedRequests.getAndIncrement();
            } catch (InterruptedException e) {
                fail(e);
            }
        })));

        // Act
        lastStopContenders.forEach(Thread::start);
        // Make all lastStopContenders, blocked before the queue() call, to start at approximately the same time
        countDownLatch.countDown();
        for (Thread lastStopContender : lastStopContenders) {
            lastStopContender.join();
        }

        // Assert
        assertThat(acceptedRequests.get(), is(requestsToBeAccepted));
        assertThat(rateLimitedRequests.get(), is(requestsToBeRejected));
    }
}