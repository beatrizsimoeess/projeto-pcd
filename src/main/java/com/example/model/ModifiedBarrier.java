package com.example.model;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ModifiedBarrier {
    private final int parties;
    private int count;
    private final long timeoutMillis;
    private final Runnable barrierAction;
    private boolean broken = false;
    
    private final Lock lock = new ReentrantLock();
    private final Condition trip = lock.newCondition();

    public ModifiedBarrier(int parties, long timeoutMillis, Runnable barrierAction) {
        this.parties = parties;
        this.count = 0;
        this.timeoutMillis = timeoutMillis;
        this.barrierAction = barrierAction;
    }

    public void await() throws InterruptedException {
        lock.lock();
        try {
            if (broken) return; 
            
            count++;
            if (count == parties) {
                if (barrierAction != null) barrierAction.run();
                trip.signalAll();
            } else {
                long nanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
                while (count < parties && nanos > 0 && !broken) {
                    nanos = trip.awaitNanos(nanos);
                }
                if (nanos <= 0 && count < parties) {
                    broken = true;
                    trip.signalAll();
                }
            }
        } finally {
            lock.unlock();
        }
    }
}