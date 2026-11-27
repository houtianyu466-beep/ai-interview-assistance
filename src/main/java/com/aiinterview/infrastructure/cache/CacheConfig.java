package com.aiinterview.infrastructure.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 缓存实现装配：{@code app.cache.mode=local} 使用内存，{@code redis} 使用 Redis。
 */
@Configuration
public class CacheConfig {

    @Bean
    @ConditionalOnProperty(name = "app.cache.mode", havingValue = "local", matchIfMissing = true)
    public CacheStore localCacheStore() {
        return new LocalCacheStore();
    }

    @Bean
    @ConditionalOnProperty(name = "app.cache.mode", havingValue = "redis")
    public CacheStore redisCacheStore(StringRedisTemplate stringRedisTemplate) {
        return new RedisCacheStore(stringRedisTemplate);
    }
}
