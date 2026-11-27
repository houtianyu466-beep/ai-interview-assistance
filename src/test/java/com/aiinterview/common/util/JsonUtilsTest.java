package com.aiinterview.common.util;

import com.aiinterview.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSON 与大模型输出清洗工具测试。
 */
class JsonUtilsTest {

    @Test
    @DisplayName("剥离 markdown 代码块并解析对象")
    void shouldStripCodeFence() {
        String raw = """
                好的，结果如下：
                ```json
                {"score": 82, "comment": "不错"}
                ```
                希望有帮助。
                """;

        Sample sample = JsonUtils.parseAiJson(raw, Sample.class);

        assertThat(sample.score).isEqualTo(82);
        assertThat(sample.comment).isEqualTo("不错");
    }

    @Test
    @DisplayName("解析数组并忽略未知字段")
    void shouldParseArrayIgnoringUnknownFields() {
        String raw = "[{\"content\":\"题目一\",\"difficulty\":3,\"extra\":true}]";

        List<Question> questions = JsonUtils.fromJson(JsonUtils.stripCodeFence(raw),
                new com.fasterxml.jackson.core.type.TypeReference<List<Question>>() {
                });

        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).content).isEqualTo("题目一");
    }

    @Test
    @DisplayName("非法 JSON 抛出 AI_ERROR")
    void shouldThrowOnInvalidJson() {
        assertThatThrownBy(() -> JsonUtils.parseAiJson("这不是 JSON", Sample.class))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("CSV 拆分去重去空")
    void shouldSplitCsv() {
        assertThat(TextUtils.splitCsv("Java, Spring Boot，Java; MySQL"))
                .containsExactly("Java", "Spring Boot", "MySQL");
    }

    static class Sample {
        public int score;
        public String comment;
    }

    static class Question {
        public String content;
        public int difficulty;
    }
}
