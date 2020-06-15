import lombok.Getter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class that enqueues requests and executes them in a dedicated Thread as fast as possible, blocking the caller if
 * trying to enqueue while the queue is full.
 */
@Getter
public class RequestExecutor {
    private final BlockingQueue<Runnable> requestQueue;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Initialises the internal queue and starts the worker Thread that executes the requests.
     */
    public RequestExecutor(int queueCapacity) {
        this.requestQueue = new LinkedBlockingQueue<>(queueCapacity);
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
                getRequestQueue().take().run();
            } catch (InterruptedException e) {
                // Normally we would log the exception with warning level, but for simplicity we are avoiding external
                // logging configurations as they would be out of scope.
                System.err.println("Received an InterruptedException: " + e.getMessage());
            }
        }
    }
}
