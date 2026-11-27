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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 应用级集成测试：启动真实 Tomcat（随机端口），验证「登录 → 查看岗位/简历 → 创建面试 → 开始 → 作答」主链路。
 *
 * <p>使用 H2 + Mock 大模型，不依赖外部服务，可在 CI 中稳定运行。
 * 采用 RANDOM_PORT 而非 MockMvc，是因为 WebSocket 端点需要真实的 Servlet 容器。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("登录成功返回令牌与用户信息")
    void shouldLogin() throws Exception {
        JsonNode body = login("candidate", "123456");
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.at("/data/accessToken").asText()).isNotBlank();
        assertThat(body.at("/data/user/username").asText()).isEqualTo("candidate");
        assertThat(body.at("/data/user/role").asText()).isEqualTo("CANDIDATE");
    }

    @Test
    @DisplayName("未携带令牌访问受保护接口返回 401")
    void shouldRejectAnonymous() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/resumes/my", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("候选人访问管理端返回 403")
    void shouldRejectCandidateOnAdminApi() throws Exception {
        String token = login("candidate", "123456").at("/data/accessToken").asText();
        ResponseEntity<String> response = exchange("/api/v1/admin/stats/overview", token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("完整面试链路：创建 → 开始 → 作答 → 下一题")
    void shouldRunInterviewFlow() throws Exception {
        String token = login("candidate", "123456").at("/data/accessToken").asText();

        JsonNode jobs = read(exchange("/api/v1/jobs?page=1&size=20", token));
        String jobId = null;
        for (JsonNode record : jobs.at("/data/records")) {
            if ("OPEN".equals(record.path("status").asText())) {
                jobId = record.path("id").asText();
                break;
            }
        }
        assertThat(jobId).as("演示数据中应存在已开放岗位").isNotNull();

        JsonNode resumes = read(exchange("/api/v1/resumes/my", token));
        String resumeId = null;
        for (JsonNode resume : resumes.path("data")) {
            if ("PARSED".equals(resume.path("status").asText())) {
                resumeId = resume.path("id").asText();
                break;
            }
        }
        assertThat(resumeId).as("演示数据中应存在已解析简历").isNotNull();

        JsonNode created = read(postJson("/api/v1/interviews", token,
                Map.of("positionId", jobId, "resumeId", resumeId, "mode", "TECH", "totalQuestions", 3)));
        assertThat(created.at("/data/status").asText()).isEqualTo("CREATED");
        assertThat(created.at("/data/totalQuestions").asInt()).isEqualTo(3);
        String interviewId = created.at("/data/id").asText();

        JsonNode started = read(postJson("/api/v1/interviews/" + interviewId + "/start", token, Map.of()));
        assertThat(started.at("/data/seq").asInt()).isEqualTo(1);
        String questionId = started.at("/data/questionId").asText();

        JsonNode answered = read(postJson("/api/v1/interviews/" + interviewId + "/answers", token,
                Map.of("questionId", questionId,
                        "answer", "HashMap 底层是数组加链表，JDK8 引入红黑树，"
                                + "链表长度超过 8 且数组长度超过 64 时树化，查询复杂度为 O(log n)。",
                        "thinkingSeconds", 30)));
        assertThat(answered.at("/data/score").asDouble()).isBetween(0.0, 100.0);
        assertThat(answered.at("/data/finished").asBoolean()).isFalse();
        assertThat(answered.at("/data/nextQuestion/seq").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("面试官可以查看候选人的面试详情与报告，并重新生成报告")
    void interviewerShouldReadCandidateInterview() throws Exception {
        String candidateToken = login("candidate", "123456").at("/data/accessToken").asText();
        String interviewId = createAndFinishInterview(candidateToken);

        String interviewerToken = login("interviewer", "123456").at("/data/accessToken").asText();

        JsonNode detail = read(exchange("/api/v1/interviews/" + interviewId, interviewerToken));
        assertThat(detail.at("/data/interview/status").asText()).isEqualTo("FINISHED");

        JsonNode report = read(exchange("/api/v1/interviews/" + interviewId + "/report", interviewerToken));
        assertThat(report.at("/data/overallScore").asDouble()).isBetween(0.0, 100.0);

        ResponseEntity<String> regenerated = restTemplate.exchange(
                "/api/v1/interviews/" + interviewId + "/report/regenerate",
                HttpMethod.POST, new HttpEntity<>(jsonHeaders(interviewerToken)), String.class);
        assertThat(regenerated.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * 创建 3 题面试、开始、作答第一题后提前结束，返回面试 id。
     */
    private String createAndFinishInterview(String token) throws Exception {
        JsonNode jobs = read(exchange("/api/v1/jobs?page=1&size=20", token));
        String jobId = null;
        for (JsonNode record : jobs.at("/data/records")) {
            if ("OPEN".equals(record.path("status").asText())) {
                jobId = record.path("id").asText();
                break;
            }
        }
        assertThat(jobId).isNotNull();

        JsonNode resumes = read(exchange("/api/v1/resumes/my", token));
        String resumeId = null;
        for (JsonNode resume : resumes.path("data")) {
            if ("PARSED".equals(resume.path("status").asText())) {
                resumeId = resume.path("id").asText();
                break;
            }
        }
        assertThat(resumeId).isNotNull();

        JsonNode created = read(postJson("/api/v1/interviews", token,
                Map.of("positionId", jobId, "resumeId", resumeId, "mode", "TECH", "totalQuestions", 3)));
        String interviewId = created.at("/data/id").asText();

        JsonNode started = read(postJson("/api/v1/interviews/" + interviewId + "/start", token, Map.of()));
        String questionId = started.at("/data/questionId").asText();
        read(postJson("/api/v1/interviews/" + interviewId + "/answers", token,
                Map.of("questionId", questionId, "answer", "HashMap 使用数组加链表，JDK8 引入红黑树。", "thinkingSeconds", 20)));

        read(restTemplate.exchange("/api/v1/interviews/" + interviewId + "/finish",
                HttpMethod.POST, new HttpEntity<>(jsonHeaders(token)), String.class));
        return interviewId;
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private JsonNode login(String username, String password) throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/login",
                jsonEntity(Map.of("username", username, "password", password), null), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read(response);
    }

    private ResponseEntity<String> exchange(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> postJson(String path, String token, Map<String, Object> body) {
        return restTemplate.postForEntity(path, jsonEntity(body, token), String.class);
    }

    private HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }

    private JsonNode read(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return objectMapper.readTree(response.getBody());
    }
}
