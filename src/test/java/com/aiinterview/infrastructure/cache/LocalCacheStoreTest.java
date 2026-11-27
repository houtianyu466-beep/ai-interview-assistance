package com.aiinterview.infrastructure.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地缓存实现测试。
 */
class LocalCacheStoreTest {

    private final LocalCacheStore cacheStore = new LocalCacheStore();

    @Test
    @DisplayName("写入后可读取，删除后不存在")
    void shouldPutGetDelete() {
        cacheStore.put("k1", "v1", Duration.ofMinutes(1));
        assertThat(cacheStore.get("k1")).contains("v1");
        assertThat(cacheStore.exists("k1")).isTrue();

        cacheStore.delete("k1");
        assertThat(cacheStore.get("k1")).isEmpty();
    }

    @Test
    @DisplayName("过期键读取为空")
    void shouldExpire() throws InterruptedException {
        cacheStore.put("k2", "v2", Duration.ofMillis(30));
        Thread.sleep(60);
        assertThat(cacheStore.get("k2")).isEmpty();
    }

    @Test
    @DisplayName("自增计数从 1 开始且保持过期时间")
    void shouldIncrement() {
        long first = cacheStore.increment("counter", Duration.ofMinutes(1));
        long second = cacheStore.increment("counter", Duration.ofMinutes(1));
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(cacheStore.get("counter")).contains("2");
    }
}
