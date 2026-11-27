package com.aiinterview.common.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * 全局 JSON 配置。
 *
 * <p>关键点：{@code Long} 类型统一序列化为字符串。雪花算法生成的 19 位 id 超过了
 * JavaScript {@code Number.MAX_SAFE_INTEGER}（2^53-1），直接返回数字会在前端丢精度。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer aiInterviewJacksonCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.simpleDateFormat("yyyy-MM-dd HH:mm:ss");
            builder.timeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        };
    }
}
