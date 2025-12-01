package com.example;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public interface map<K, V> {
    
    V put(K key, V value);
    V get(Object key);
    void clear();

    void putIfAbsent(K key, V value);
    V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction);
    
    map<K, V> cloneMap();
    
    boolean isEmpty();

    Set<Map.Entry<K, V>> entrySet();

    V remove(Object key);
}