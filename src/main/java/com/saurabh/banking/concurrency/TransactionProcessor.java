package com.saurabh.banking.concurrency;

import java.util.concurrent.*;

public class TransactionProcessor implements AutoCloseable {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public Future<?> submit(Runnable transaction) {
        return executor.submit(transaction);
    }

    public <T> Future<T> submit(Callable<T> transaction) {
        return executor.submit(transaction);
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
