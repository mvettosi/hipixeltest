//import javax.annotation.concurrent.ThreadSafe;

import exception.RateLimitException;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rate limits are an important tool to prevent API users from causing denial-of-service for other users. This class is
 * supposed to guarantee that we do not perform too many requests for our specified rate. Please make use of the Java
 * standard library and implement a thread-safe rate limiter.
 * <p>
 * Implement a thread-safe rate limiting executor service. Configuration parameters are accepted in the constructor and
 * should be immutable over the lifetime of the instance. The rate limitation should be implemented via a rolling window
 * to ensure minimal delay between task execution. Accepted requests should be queued until the maximum queue size is
 * reached. Queueing a request while the maximum queue size is reached will block the calling thread until the request
 * can be accepted. Once a request was executed the completable future should either complete or exceptionally fail. All
 * requests should be handled in a dedicated thread.
 * <p>
 * Bonus Objective: Execute requests in parallel without violate the original contract by changing the dedicated thread
 * to a threadpool.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Rate_limiting">Rate limiting on Wikipedia</a>
 */
//@ThreadSafe
@Getter
public class RateLimitedExecutor {
    private final int requestsPerMinute;
    private final int maxQueueSize;
    private final Map<Long, Integer> acceptedRequests;
    private final RequestExecutor requestExecutor;

    /**
     * Constructs a new RateLimitedExecutor and start accepting Requests immediately.
     *
     * @param requestsPerMinute Requests per minute. This should be a rolling window to ensure minimal request delay.
     * @param maxQueueSize      The maximum size of the task queue.
     */
    public RateLimitedExecutor(int requestsPerMinute, int maxQueueSize) {
        this.requestsPerMinute = requestsPerMinute;
        this.maxQueueSize = maxQueueSize;
        this.acceptedRequests = new ConcurrentHashMap<>();
        this.requestExecutor = new RequestExecutor(maxQueueSize);
    }

    /**
     * @return Amount of currently queued requests
     */
    public int getQueuedRequests() {
        return getRequestExecutor().getQueuedRequests();
    }

    /**
     * This method should queue a request to be processed as soon as possible (FIFO).
     * <p>
     * Processing a request should not be done in the calling thread. If the call would be exceeding the maximum size of
     * the queue we want the call to block the current thread.
     * <p>
     * Processing requests may not exceed the rolling window defined in the constructor.
     *
     * @param request Request to queue
     * @return CompletableFuture which is completed once the requests executed.
     */
    public CompletableFuture<String> queue(Request request) throws RateLimitException, InterruptedException {
        final CompletableFuture<String> future = new CompletableFuture<>();

        // Retrieve the current timestamp in seconds
        long timeStampSeconds = Instant.now().getEpochSecond();

        // Remove all logs older than one minute ago
        getAcceptedRequests().entrySet().removeIf(entry -> entry.getKey() < timeStampSeconds - 60);

        // Get the sum of all the requests accepted in the last minute
        Integer acceptedRequestNumber = getAcceptedRequests().values().stream().reduce(0, Integer::sum);

        if (acceptedRequestNumber < getRequestsPerMinute()) {
            // This request can be accepted, increment the accepted counter for this timestamp
            getAcceptedRequests().put(timeStampSeconds, getAcceptedRequests().getOrDefault(timeStampSeconds, 0) + 1);

            getRequestExecutor().enqueue(() -> {
                // Execute request
                System.out.println("About to execute");
                try {
                    future.complete(request.execute());
                    System.out.println("Completed");
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    System.out.println("Future exception");
                }
            });
        } else {
            // Rate limit reached! Cannot satisfy this request
            throw new RateLimitException();
        }
        return future;
    }

    /**
     * This request emulates a HTTP request. Do not change this class.
     */
    public static class Request {
        /**
         * Use this static method to generate a new request.
         *
         * @return Generated Request
         */
        public static Request create() {
            return new Request(UUID.randomUUID());
        }

        private final UUID id;

        /**
         * Private constructor. Please use <code>Request.create()</code>.
         *
         * @param id Unique ID
         */
        private Request(UUID id) {
            this.id = id;
        }

        /**
         * @return UUID of this Request
         */
        public UUID getId() {
            return id;
        }

        /**
         * Sleeps for 1-4 seconds
         */
        public String execute() throws Exception {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Thread.sleep(rnd.nextLong(3000) + 1000);
            // 10% chance to fail and result in an error
            if (rnd.nextDouble() < 0.1) {
                throw new RuntimeException("failed to execute!");
            } else {
                return "Success! o7";
            }
        }
    }

    /**
     * Generic main method to test the class. You can change this as you desire.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        final RateLimitedExecutor executor = new RateLimitedExecutor(100, 8);

        //noinspection InfiniteLoopStatement
        while (true) {
            Request request = Request.create();
            try {
                executor.queue(request).whenComplete((msg, err) -> {
                    if (err != null) {
                        System.out.printf("[%s] Error: %s\n", request.getId(), err.getMessage());
                    } else {
                        System.out.printf("[%s] %s\n", request.getId(), msg);
                    }
                });
            } catch (RateLimitException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}