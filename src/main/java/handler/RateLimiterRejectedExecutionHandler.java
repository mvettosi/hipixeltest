package handler;

import lombok.Getter;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * RejectedExecutionHandler that handles rejections by waiting for the executor's queue to free up some space, and then
 * inserting the request again.
 */
@Getter
public class RateLimiterRejectedExecutionHandler implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor threadPoolExecutor) {
        if (!threadPoolExecutor.isShutdown()) {
            try {
                threadPoolExecutor.getQueue().put(runnable);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted while waiting to enqueue a request", e);
            }
        } else {
            throw new RejectedExecutionException("Executor is already shut down!");
        }
    }
}
