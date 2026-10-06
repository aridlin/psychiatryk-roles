package pl.aridlin.psychiatrykroles.io;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** FIFO file owner. Failed asynchronous saves stay first; synchronous calls never retry secretly. */
public final class OrderedIoOwner {
    private static final Logger LOGGER = LoggerFactory.getLogger("PsychiatrykSaveIo");
    private static final int MAX_PENDING = 1024;
    @FunctionalInterface public interface Action<T> { T run() throws Exception; }
    private static final class Job<T> {
        final Action<T> action;
        final boolean retryable;
        final CompletableFuture<T> result = new CompletableFuture<>();
        boolean started;
        Job(Action<T> action, boolean retryable) { this.action = action; this.retryable = retryable; }
    }
    private final String label;
    private final ScheduledExecutorService executor;
    private final ArrayDeque<Job<?>> queue = new ArrayDeque<>();
    private volatile Thread worker;
    private Throwable failure;
    private boolean scheduled;
    private boolean closed;
    private long lastErrorLog;
    private long completedJobs;

    public OrderedIoOwner(String label) {
        this.label = label;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Psychiatryk Sable IO " + label);
            thread.setDaemon(true);
            worker = thread;
            return thread;
        });
    }
    public boolean isWorker() { return Thread.currentThread() == worker; }
    public synchronized int pendingJobs() { return queue.size(); }
    public synchronized long completedJobs() { return completedJobs; }
    public synchronized boolean hasFailure() { return failure != null; }

    public void submit(Action<Void> action) throws IOException {
        enqueue(action, true);
    }
    public <T> T call(Action<T> action) throws IOException {
        if (isWorker()) {
            try { return action.run(); }
            catch (Exception error) { throw io(error); }
        }
        return await(enqueue(action, false));
    }
    public void barrier() throws IOException { call(() -> null); }

    private synchronized <T> Job<T> enqueue(Action<T> action, boolean retryable) throws IOException {
        if (closed) throw new IOException("Sable IO owner already closed: " + label);
        // Enforce the bound BEFORE accepting a snapshot; a failed full queue cannot grow.
        while (queue.size() >= MAX_PENDING) {
            if (failure != null) throw io(failure);
            try { wait(); }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted before accepting Sable IO job: " + label, error);
            }
            if (closed) throw new IOException("Sable IO owner already closed: " + label);
        }
        // A caller must never enqueue allocation/read behind a failed prefix and then leave early.
        if (!retryable && failure != null) throw io(failure);
        Job<T> job = new Job<>(action, retryable);
        queue.addLast(job);
        if (!scheduled) {
            scheduled = true;
            executor.execute(this::drain);
        }
        return job;
    }
    private <T> T await(Job<T> job) throws IOException {
        boolean interrupted = false;
        try {
            synchronized (this) {
                while (!job.result.isDone()) {
                    // Backpressure may fail while its ASYNC save remains retained for retry.
                    if (job.retryable && failure != null) throw io(failure);
                    try { wait(); }
                    catch (InterruptedException error) {
                        interrupted = true;
                        if (!job.retryable && !job.started) {
                            queue.remove(job);
                            job.result.completeExceptionally(new IOException(
                                    "Interrupted before Sable IO call started: " + label, error));
                            notifyAll();
                        }
                        // A started allocation cannot be interrupted out from under its caller.
                        // Wait to obtain its actual result, then restore the interrupt flag.
                    }
                }
            }
            try { return job.result.join(); }
            catch (CompletionException error) { throw io(error.getCause()); }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
    private void drain() {
        for (;;) {
            Job<?> job;
            synchronized (this) {
                job = queue.peekFirst();
                if (job == null) {
                    scheduled = false;
                    notifyAll();
                    return;
                }
                job.started = true;
            }
            try {
                Object value = job.action.run();
                synchronized (this) {
                    queue.removeFirst();
                    complete(job, value);
                    completedJobs++;
                    if (failure != null) {
                        LOGGER.info("Sable IO recovered for {}; retained save committed", label);
                        failure = null;
                    }
                    notifyAll();
                }
            } catch (Exception error) {
                synchronized (this) {
                    if (!job.retryable) {
                        // Its caller receives the failure, and this action will NEVER execute later.
                        queue.removeFirst();
                        job.result.completeExceptionally(error);
                        LOGGER.error("Synchronous Sable IO failed for {}; call is not retried", label, error);
                        notifyAll();
                        continue;
                    }
                    failure = error;
                    job.started = false;
                    // Cancel all unstarted calls before waking their callers; retain asynchronous saves.
                    for (Iterator<Job<?>> iterator = queue.iterator(); iterator.hasNext();) {
                        Job<?> waiting = iterator.next();
                        if (!waiting.retryable && !waiting.started) {
                            iterator.remove();
                            waiting.result.completeExceptionally(new IOException(
                                    "Sable IO call cancelled before execution because an earlier save failed: " + label,
                                    error));
                        }
                    }
                    long now = System.nanoTime();
                    if (lastErrorLog == 0 || now - lastErrorLog >= TimeUnit.SECONDS.toNanos(60)) {
                        LOGGER.error("Sable IO failed for {}; {} asynchronous jobs retained in order and will retry. "
                                + "Explicit save completion is blocked until recovery.", label, queue.size(), error);
                        lastErrorLog = now;
                    }
                    notifyAll();
                    // Failed async job remains first. DSYNC/force and payload/index order remain intact.
                    executor.schedule(this::drain, 2, TimeUnit.SECONDS);
                    return;
                }
            }
        }
    }
    @SuppressWarnings("unchecked")
    private static <T> void complete(Job<T> job, Object value) { job.result.complete((T) value); }
    private IOException io(Throwable error) {
        return new IOException("Sable ordered IO failed for " + label, error);
    }
    /** Call only after a successful close job and a completed barrier. */
    public synchronized void shutdown() throws IOException {
        if (!queue.isEmpty() || failure != null) throw new IOException("Cannot close pending Sable IO: " + label);
        closed = true;
        executor.shutdown();
    }
}
