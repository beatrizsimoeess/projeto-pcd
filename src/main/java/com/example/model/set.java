package com.example.model;

public interface set<E> extends Iterable<E> {

    boolean add(E e);
    boolean remove(E e);
    boolean contains(Object o);
    void clear();
    int size();
}
