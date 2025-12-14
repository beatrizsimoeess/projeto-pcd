package com.example.model;

import java.util.HashSet;
import java.util.Set;

public class Hashset<E> implements set<E> {
    
    private final Set<E> internalSet;

    public Hashset() {
        this.internalSet = new HashSet<>();
    }

    @Override
    public synchronized boolean add(E e) {
        return internalSet.add(e);
    }

    @Override
    public synchronized boolean contains(Object o) {
        return internalSet.contains(o);
    }

    @Override
    public synchronized void clear() {
        internalSet.clear();
    }

    @Override
    public synchronized int size() {
        return internalSet.size();
    }
}