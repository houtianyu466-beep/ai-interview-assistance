package com.aiinterview.infrastructure.cache;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 缓存实现（生产环境）。键值均为字符串，便于多实例共享登录态与限流计数。
 */
public class RedisCacheStore implements CacheStore {

    private final StringRedisTemplate redisTemplate;

    public RedisCacheStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        if (ttl == null || ttl.isZero()) {
            redisTemplate.opsForValue().set(key, value);
        } else {
            redisTemplate.opsForValue().set(key, value, ttl);
        }
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L && ttl != null && !ttl.isZero()) {
            redisTemplate.expire(key, ttl);
        }
        return value == null ? 0 : value;
    }
}
