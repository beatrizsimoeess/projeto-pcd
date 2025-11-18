package com.example;


import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class Hashmap<K, V> implements map<K, V> {
    
    private final Map<K, V> internalMap;

    public Hashmap() {
        this.internalMap = new HashMap<>();
    }

    private Hashmap(Map<K, V> mapToCopy) {
        this.internalMap = new HashMap<>(mapToCopy);
    }

    @Override
    public synchronized V put(K key, V value) {
        return internalMap.put(key, value);
    }

    @Override
    public synchronized V get(Object key) {
        return internalMap.get(key);
    }

    @Override
    public synchronized void clear() {
        internalMap.clear();
    }

    @Override
    public synchronized void putIfAbsent(K key, V value) {
        internalMap.putIfAbsent(key, value);
    }

    @Override
    public synchronized V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return internalMap.merge(key, value, remappingFunction);
    }

    @Override
    public synchronized map<K, V> cloneMap() {
        return new Hashmap<>(this.internalMap);
    }
}
