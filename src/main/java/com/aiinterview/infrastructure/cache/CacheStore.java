package com.aiinterview.infrastructure.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * 缓存抽象。
 *
 * <p>为什么需要抽象：开发环境（H2 内存库）不启动 Redis，用 {@link LocalCacheStore}；
 * 生产环境切到 {@link RedisCacheStore}，上层代码（令牌黑名单、验证码、幂等键）零改动。</p>
 */
public interface CacheStore {

    void put(String key, String value, Duration ttl);

    Optional<String> get(String key);

    void delete(String key);

    boolean exists(String key);

    /** 自增计数，返回自增后的值；key 不存在时从 0 开始并设置过期时间。 */
    long increment(String key, Duration ttl);
}
