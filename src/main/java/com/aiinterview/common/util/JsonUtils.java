package com.aiinterview.common.util;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;
import java.util.Map;

/**
 * JSON 工具类。
 *
 * <p>两类用途：</p>
 * <ul>
 *     <li>业务序列化：维度得分、解析结果等以 JSON 字符串落库；</li>
 *     <li>大模型输出解析：{@link #stripCodeFence(String)} 负责剥离 ```json 代码块，
 *         {@link #parseAiJson(String, Class)} 在解析失败时抛出可读的业务异常。</li>
 * </ul>
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(longToStringModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /**
     * 与 Web 层保持一致：Long 序列化为字符串，避免雪花 id 在 JavaScript 中丢失精度。
     * WebSocket 下行消息使用本工具类序列化，必须与 HTTP 响应的行为统一。
     */
    private static SimpleModule longToStringModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return module;
    }

    private JsonUtils() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "JSON 序列化失败", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "JSON 反序列化失败", e);
        }
    }

    /**
     * 泛型反序列化，例如 {@code fromJson(json, new TypeReference<List<Foo>>(){})}。
     */
    public static <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "JSON 反序列化失败", e);
        }
    }

    public static Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static List<String> toStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 去掉大模型常见的 ```json 包裹，并截取第一个 JSON 对象/数组。
     */
    public static String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        int start;
        if (objectStart < 0) {
            start = arrayStart;
        } else if (arrayStart < 0) {
            start = objectStart;
        } else {
            start = Math.min(objectStart, arrayStart);
        }
        if (start < 0) {
            return text;
        }
        char open = text.charAt(start);
        char close = open == '{' ? '}' : ']';
        int end = text.lastIndexOf(close);
        if (end > start) {
            return text.substring(start, end + 1);
        }
        return text.substring(start);
    }

    /**
     * 解析大模型返回的 JSON；失败时抛出 AI_ERROR，由调用方决定是否走规则降级。
     */
    public static <T> T parseAiJson(String raw, Class<T> type) {
        String cleaned = stripCodeFence(raw);
        try {
            return MAPPER.readValue(cleaned, type);
        } catch (Exception e) {
            throw new BizException(ErrorCode.AI_ERROR, "AI 返回内容不是合法 JSON", e);
        }
    }

    public static JsonNode readTree(String raw) {
        try {
            return MAPPER.readTree(stripCodeFence(raw));
        } catch (Exception e) {
            throw new BizException(ErrorCode.AI_ERROR, "AI 返回内容不是合法 JSON", e);
        }
    }
}
