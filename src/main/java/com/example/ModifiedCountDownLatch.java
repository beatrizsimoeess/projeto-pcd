package com.example;

public class ModifiedCountDownLatch {
    private int count;
    private final int waitPeriod; 
    private final int bonusFactor;
    private int bonusCount;
    
    // Construtor conforme enunciado V2
    public ModifiedCountDownLatch(int bonusFactor, int bonusCount, int waitPeriod, int count) {
        this.bonusFactor = bonusFactor;
        this.bonusCount = bonusCount;
        this.waitPeriod = waitPeriod;
        this.count = count;
    }

    public synchronized int countdown() {
        count--;
        int currentFactor = 1;
        
        if (bonusCount > 0) {
            currentFactor = bonusFactor;
            bonusCount--;
        }
        
    
        return currentFactor;
    }

    public synchronized void await() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long remainingTime = waitPeriod;

        while (count > 0 && remainingTime > 0) {
            wait(remainingTime);
            remainingTime = waitPeriod - (System.currentTimeMillis() - startTime);
        }
    }
}