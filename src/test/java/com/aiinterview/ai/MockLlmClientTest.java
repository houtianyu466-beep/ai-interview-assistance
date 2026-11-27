package com.aiinterview.ai;

import com.aiinterview.ai.model.AnswerEvaluation;
import com.aiinterview.ai.model.GeneratedQuestion;
import com.aiinterview.ai.model.ParsedResume;
import com.aiinterview.common.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock 大模型桩的离线行为测试，保证无 API Key 时全流程可用。
 */
class MockLlmClientTest {

    private final MockLlmClient client = new MockLlmClient();

    @Test
    @DisplayName("简历解析返回合法 JSON")
    void shouldReturnParsableResumeJson() {
        LlmResponse response = client.chat(AiScenes.RESUME_PARSE,
                List.of(LlmMessage.system("解析简历"), LlmMessage.user("张伟，5 年 Java 经验")), 0.3);

        ParsedResume parsed = JsonUtils.parseAiJson(response.content(), ParsedResume.class);

        assertThat(parsed.name()).isEqualTo("张伟");
        assertThat(parsed.skills()).contains("Java");
        assertThat(parsed.yearsOfExperience()).isEqualTo(5);
        assertThat(parsed.summary()).isNotBlank();
        assertThat(response.promptTokens()).isPositive();
    }

    @Test
    @DisplayName("出题数量与提示词中要求一致")
    void shouldGenerateRequestedQuestionCount() {
        LlmResponse response = client.chat(AiScenes.QUESTION_GENERATE,
                List.of(LlmMessage.user("请生成 3 道面试题。要求：题目共 3 道")), 0.3);

        List<GeneratedQuestion> questions = JsonUtils.fromJson(
                JsonUtils.stripCodeFence(response.content()), new TypeReference<>() {
                });

        assertThat(questions).hasSize(3);
        assertThat(questions.get(0).content()).isNotBlank();
        assertThat(Integer.parseInt(questions.get(0).difficulty())).isBetween(1, 5);
    }

    @Test
    @DisplayName("回答越长评分越高，未作答为 0 分")
    void shouldScoreByAnswerLength() {
        String shortRaw = client.chat(AiScenes.ANSWER_SCORE,
                List.of(LlmMessage.user("【题目】HashMap\n【候选人回答】数组加链表")), 0.3).content();
        AnswerEvaluation shortAnswer = JsonUtils.parseAiJson(shortRaw, AnswerEvaluation.class);

        String longAnswerText = "HashMap 底层是数组加链表，JDK8 引入红黑树，当链表长度超过 8 且数组长度超过 64 时转为红黑树，"
                + "扩容时通过高位运算重新分布，查询复杂度从 O(n) 降到 O(log n)，并且需要注意并发场景下的死循环问题。";
        AnswerEvaluation longAnswer = JsonUtils.parseAiJson(
                client.chat(AiScenes.ANSWER_SCORE,
                        List.of(LlmMessage.user("【题目】HashMap\n【候选人回答】" + longAnswerText)), 0.3).content(),
                AnswerEvaluation.class);

        AnswerEvaluation empty = JsonUtils.parseAiJson(
                client.chat(AiScenes.ANSWER_SCORE,
                        List.of(LlmMessage.user("【题目】HashMap\n【候选人回答】（候选人未作答）")), 0.3).content(),
                AnswerEvaluation.class);

        assertThat(shortAnswer).isNotNull();
        assertThat(shortAnswer.score()).isLessThan(longAnswer.score());
        assertThat(empty.score()).isZero();
    }
}
