import lombok.AccessLevel;
import lombok.Getter;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class that enqueues requests and executes them in a dedicated Thread as fast as possible, blocking the caller if
 * trying to enqueue while the queue is full.
 */
@Getter(AccessLevel.PRIVATE)
public class RequestExecutor {
    private final BlockingQueue<Runnable> requestQueue;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final ThreadPoolExecutor threadPoolExecutor;

    /**
     * Initialises the internal queue and starts the worker Thread that submits the requests to the ThreadPoolExecutor.
     */
    public RequestExecutor(int queueCapacity) {
        this.requestQueue = new LinkedBlockingQueue<>(queueCapacity);

        // Instantiate a cached ThreadPoolExecutor that starts with no core threads, at most spawns as many threads as
        // the queue size (since we are not interested in executing more than that at the same time), de-allocates them
        // after standard 60 seconds, and uses a SynchronousQueue which will always be of size 0 since we already manage
        // the actual job queue externally.
        threadPoolExecutor = new ThreadPoolExecutor(0, queueCapacity, 60, TimeUnit.SECONDS, new SynchronousQueue<>());

        // Start the single thread that will pick up requests from the queue and submit them to the threadPoolExecutor
        new Thread(this::executeRequests).start();
    }

    /**
     * Allows the caller to stop the worker thread in a clean way.
     */
    public void stop() {
        this.cancelled.set(true);
    }

    /**
     * Returns the current number of enqueued jobs.
     */
    public int getQueuedRequests() {
        return getRequestQueue().size();
    }

    /**
     * Add the provided request to the queue, blocking the calling thread is the queue is currently full.
     *
     * @param request The request to run
     * @throws InterruptedException if the calling thread was interrupted while waiting for the queue to free up
     */
    public void enqueue(Runnable request) throws InterruptedException {
        getRequestQueue().put(request);
    }

    /**
     * Internal method that continually waits for an available job in the request queue, removes it and executes it.
     */
    private void executeRequests() {
        while (!cancelled.get()) {
            try {
                getThreadPoolExecutor().submit(getRequestQueue().take());
            } catch (InterruptedException e) {
                // Normally we would log the exception with warning level, but for simplicity we are avoiding external
                // logging configurations as they would be out of scope.
                System.err.println("Received an InterruptedException: " + e.getMessage());
            }
        }
    }
}
