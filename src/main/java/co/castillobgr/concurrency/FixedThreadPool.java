package co.castillobgr.concurrency;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class FixedThreadPool {

    // Convenience function to generate a pool name.
    private static String generateName() {
        return "pool-" + UUID.randomUUID();
    }

    // The pool's capacity.
    private int capacity;
    // The pool's name.
    private String name;
    // Maximum amount of time a thread can be idle.
    private long maxIdleTime;
    // Queue of pending tasks.
    private BlockingQueue<Runnable> backlog;
    // A list to keep a reference of threads.
    private Worker[] pool;
    // An atomic counter for busy threads.
    private AtomicInteger busyWorkers;

    public FixedThreadPool(int capacity, long maxIdleTime, BlockingQueue<Runnable> backlog, String name) {
        if (capacity < 0) throw new IllegalArgumentException("capacity can't be negative");
        if (maxIdleTime < 0) throw new IllegalArgumentException("maxIdleTime can't be negative");
        this.capacity = capacity;
        this.name = name;
        this.maxIdleTime = maxIdleTime;
        this.backlog = backlog;
        this.pool = new Worker[capacity];
        this.busyWorkers = new AtomicInteger();
        busyWorkers.set(0);
    }

    public FixedThreadPool(int capacity, BlockingQueue<Runnable> backlog) {
        this(capacity, 0, backlog, generateName());
    }

    // Submit a new task to the pool.
    public boolean submit(Runnable task) {
        if (task == null) throw new IllegalArgumentException("r can't be null");
        // Try and create a new worker if the pool isn't full.
        if (placeTask(task)) return true;
        // If the task wasn't placed, it's because the workers were all busy. However, it could happen that after
        // checking they become available, so we check and try again.
        if (getAvailability() > 0) return placeTask(task);
        // If there was no availability, we add the task to the backlog.
        return backlog.offer(task);
    }

    private boolean placeTask(Runnable r) {
        for (int i = 0; i < this.capacity; i++) {
            Worker current = this.pool[i];
            // TODO: Check into Thread#getState()
            if (current == null || !current.thread.isAlive()) {
                Worker w = new Worker(r);
                w.thread.start();
                this.pool[i] = w;
                return true;
            }
        }
        return false;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getName() {
        return name;
    }

    public int getAvailability() {
        int availability = 0;
        for (int i = 0; i < capacity; i++) {
            if (pool[i] == null || !pool[i].thread.isAlive()) availability += 1;
        }
        return availability;
    }

    private class Worker implements Runnable {

        private Runnable task;

        private Thread thread;

        private Exception thrown = null;

        Worker(Runnable task) {
            if (task == null) throw new IllegalArgumentException("task can't be null");
            this.task = task;
            // Look into Executors.defaultThreadFactory()
            this.thread = new Thread(this);
        }

        public void run() {
            try {
                while (this.task != null) {
                    FixedThreadPool.this.busyWorkers.incrementAndGet();
                    this.task.run();
                    FixedThreadPool.this.busyWorkers.decrementAndGet();
                    this.task = backlog.poll(maxIdleTime, TimeUnit.MILLISECONDS);
                }
            }
            catch (InterruptedException ie) {
                this.thrown = ie;
            }
        }
    }
}
