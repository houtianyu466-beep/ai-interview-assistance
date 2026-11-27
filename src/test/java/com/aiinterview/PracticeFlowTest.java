package com.aiinterview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 练习闭环集成测试：方向 → 创建 → 开始 → 提示 → 作答/跳过 → 复盘。
 *
 * <p>与招聘链路无关，验证「面试练习工具」的核心用户旅程。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PracticeFlowTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("练习方向内置 8 个，且包含 Java 后端")
    void shouldListPracticeTargets() throws Exception {
        String token = login();
        JsonNode targets = get("/api/v1/practice/targets", token);
        assertThat(targets.path("data").size()).isGreaterThanOrEqualTo(8);
        boolean hasJava = false;
        for (JsonNode target : targets.path("data")) {
            if ("java-backend".equals(target.path("code").asText())) {
                hasJava = true;
                assertThat(target.path("focusTopics").size()).isGreaterThan(0);
            }
        }
        assertThat(hasJava).isTrue();
    }

    @Test
    @DisplayName("完成一场 3 题练习：提示、跳过、作答、复盘")
    void shouldCompletePracticeFlow() throws Exception {
        String token = login();

        JsonNode targets = get("/api/v1/practice/targets", token);
        String targetId = targets.at("/data/0/id").asText();

        Map<String, Object> createBody = new HashMap<>();
        createBody.put("targetId", targetId);
        createBody.put("mode", "TECH");
        createBody.put("totalQuestions", 3);
        JsonNode created = post("/api/v1/practice/sessions", token, createBody);
        assertThat(created.at("/data/status").asText()).isEqualTo("CREATED");
        assertThat(created.at("/data/totalQuestions").asInt()).isEqualTo(3);
        String sessionId = created.at("/data/id").asText();

        JsonNode question = post("/api/v1/practice/sessions/" + sessionId + "/start", token, Map.of());
        assertThat(question.at("/data/seq").asInt()).isEqualTo(1);
        String firstQuestionId = question.at("/data/questionId").asText();

        JsonNode hint = post("/api/v1/practice/sessions/" + sessionId + "/hint", token, Map.of());
        assertThat(hint.at("/data/hint").asText()).isNotBlank();

        // 第 1 题跳过
        JsonNode skipped = post("/api/v1/practice/sessions/" + sessionId + "/skip", token, Map.of());
        assertThat(skipped.at("/data/finished").asBoolean()).isFalse();
        assertThat(skipped.at("/data/nextQuestion/seq").asInt()).isEqualTo(2);

        // 第 2 题正常作答
        String secondQuestionId = skipped.at("/data/nextQuestion/questionId").asText();
        JsonNode answered = answer(token, sessionId, secondQuestionId);
        assertThat(answered.at("/data/score").asDouble()).isBetween(0.0, 100.0);
        assertThat(answered.at("/data/finished").asBoolean()).isFalse();

        // 第 3 题（最后一题）作答后结束
        String thirdQuestionId = answered.at("/data/nextQuestion/questionId").asText();
        JsonNode finished = answer(token, sessionId, thirdQuestionId);
        assertThat(finished.at("/data/finished").asBoolean()).isTrue();
        assertThat(finished.at("/data/reportId").asText()).isNotBlank();

        JsonNode review = get("/api/v1/practice/sessions/" + sessionId + "/review", token);
        assertThat(review.at("/data/overallScore").asDouble()).isBetween(0.0, 100.0);
        assertThat(review.at("/data/level").asText()).isIn("EXCELLENT", "GOOD", "IMPROVING", "NEEDS_WORK");
        assertThat(review.at("/data/levelLabel").asText()).isNotBlank();
        assertThat(review.at("/data/questions").size()).isEqualTo(3);
        assertThat(review.at("/data/nextSteps").size()).isGreaterThan(0);
        assertThat(review.at("/data/questions/0/referenceAnswer").asText()).isNotBlank();

        // 跳过题的分数为空
        boolean skippedScoreNull = true;
        for (JsonNode item : review.path("data").path("questions")) {
            if (item.path("seq").asInt() == 1) {
                skippedScoreNull = item.path("score").isNull() || item.path("score").isMissingNode();
            }
        }
        assertThat(skippedScoreNull).isTrue();
        assertThat(firstQuestionId).isNotBlank();
    }

    @Test
    @DisplayName("练习接口需要登录，匿名返回 401")
    void shouldRejectAnonymous() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/practice/targets", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private JsonNode answer(String token, String sessionId, String questionId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("questionId", questionId);
        body.put("answer", "我会先给结论，再从原理、场景与取舍三个角度展开，最后补充量化结果与复盘。");
        body.put("thinkingSeconds", 30);
        return post("/api/v1/practice/sessions/" + sessionId + "/answers", token, body);
    }

    private String login() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/login",
                jsonEntity(Map.of("username", "candidate", "password", "123456"), null), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
    }

    private JsonNode get(String path, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode post(String path, String token, Map<String, Object> body) throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(path, jsonEntity(body, token), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return objectMapper.readTree(response.getBody());
    }

    private HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }
}
