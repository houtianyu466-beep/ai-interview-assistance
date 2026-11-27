package com.aiinterview.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机内存缓存实现（开发/测试默认）。使用惰性过期，读取时判断是否过期。
 */
public class LocalCacheStore implements CacheStore {

    private static final Logger log = LoggerFactory.getLogger(LocalCacheStore.class);

    private record Entry(String value, long expireAtMillis) {
        boolean expired() {
            return expireAtMillis > 0 && System.currentTimeMillis() > expireAtMillis;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value, Duration ttl) {
        long expireAt = ttl == null || ttl.isZero() ? 0 : System.currentTimeMillis() + ttl.toMillis();
        store.put(key, new Entry(value, expireAt));
    }

    @Override
    public Optional<String> get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expired()) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return get(key).isPresent();
    }

    @Override
    public long increment(String key, Duration ttl) {
        Entry updated = store.compute(key, (k, old) -> {
            long current = 0;
            long expireAt = ttl == null || ttl.isZero() ? 0 : System.currentTimeMillis() + ttl.toMillis();
            if (old != null && !old.expired()) {
                try {
                    current = Long.parseLong(old.value());
                    expireAt = old.expireAtMillis();
                } catch (NumberFormatException ignored) {
                    log.warn("缓存值不是数字，重置计数 key={}", key);
                }
            }
            return new Entry(String.valueOf(current + 1), expireAt);
        });
        return Long.parseLong(updated.value());
    }
}
