package com.siruoren.buildparameterhistory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BuildParameterHistoryThreadPool {

    private static final Logger LOGGER = Logger.getLogger(BuildParameterHistoryThreadPool.class.getName());

    private static final int CORE_POOL_SIZE;
    private static final int MAX_POOL_SIZE;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final int QUEUE_CAPACITY = 500;

    static {
        int cpus = Runtime.getRuntime().availableProcessors();
        String coreStr = System.getProperty("buildParameterHistory.corePoolSize");
        String maxStr = System.getProperty("buildParameterHistory.maxPoolSize");

        int core = Math.max(2, cpus / 2);
        int max = Math.max(4, cpus);

        if (coreStr != null) {
            try {
                core = Math.max(1, Integer.parseInt(coreStr.trim()));
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Invalid buildParameterHistory.corePoolSize: " + coreStr, e);
            }
        }
        if (maxStr != null) {
            try {
                max = Math.max(core, Integer.parseInt(maxStr.trim()));
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Invalid buildParameterHistory.maxPoolSize: " + maxStr, e);
            }
        }

        CORE_POOL_SIZE = core;
        MAX_POOL_SIZE = max;

        LOGGER.log(Level.INFO, "BuildParameterHistory thread pool initialized: core={0}, max={1}",
                new Object[]{CORE_POOL_SIZE, MAX_POOL_SIZE});
    }

    private static volatile BuildParameterHistoryThreadPool instance;

    private final ThreadPoolExecutor executor;

    private BuildParameterHistoryThreadPool() {
        executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                new BphThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        executor.allowCoreThreadTimeOut(true);
    }

    public static BuildParameterHistoryThreadPool getInstance() {
        if (instance == null) {
            synchronized (BuildParameterHistoryThreadPool.class) {
                if (instance == null) {
                    instance = new BuildParameterHistoryThreadPool();
                }
            }
        }
        return instance;
    }

    public void submit(Runnable task) {
        if (executor.isShutdown()) {
            LOGGER.warning("Thread pool is shut down, running task in caller thread");
            task.run();
            return;
        }

        int queueSize = executor.getQueue().size();
        if (queueSize > QUEUE_CAPACITY * 80 / 100) {
            LOGGER.log(Level.WARNING, "Thread pool queue is {0}% full ({1}/{2}), running task in caller thread to prevent overload",
                    new Object[]{queueSize * 100 / QUEUE_CAPACITY, queueSize, QUEUE_CAPACITY});
            task.run();
            return;
        }

        executor.submit(task);
    }

    public int getActiveCount() {
        return executor.getActiveCount();
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    public int getPoolSize() {
        return executor.getPoolSize();
    }

    public void shutdown() {
        LOGGER.info("Shutting down BuildParameterHistory thread pool...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                LOGGER.warning("Thread pool forced shutdown after timeout");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class BphThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "bph-worker-" + threadNumber.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    }
}
