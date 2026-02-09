package com.systemdesigncasestudy.weeks1changha.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ExpiringCache<K, V> {

    private final ConcurrentHashMap<K, Entry<V>> store = new ConcurrentHashMap<>();

    public Optional<V> get(K key) {
        Entry<V> entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }

        if (System.currentTimeMillis() >= entry.expiresAtEpochMs) {
            store.remove(key, entry);
            return Optional.empty();
        }

        return Optional.of(entry.value);
    }

    public void put(K key, V value, Duration ttl) {
        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        store.put(key, new Entry<>(value, expiresAt));
    }

    public void invalidate(K key) {
        store.remove(key);
    }

    public void clear() {
        store.clear();
    }

    private record Entry<V>(V value, long expiresAtEpochMs) {
    }
}
