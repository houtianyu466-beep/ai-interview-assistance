package com.aiinterview.agent;

import com.aiinterview.agent.dto.CitationVO;
import com.aiinterview.agent.dto.CoachResponse;
import com.aiinterview.agent.entity.AgentTrace;
import com.aiinterview.agent.mapper.AgentTraceMapper;
import com.aiinterview.agent.service.AgentLlmClient;
import com.aiinterview.agent.service.AgentService;
import com.aiinterview.ai.LlmProperties;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.entity.InterviewQuestion;
import com.aiinterview.interview.enums.InterviewStatus;
import com.aiinterview.interview.mapper.InterviewMapper;
import com.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.aiinterview.knowledge.dto.KnowledgeHitVO;
import com.aiinterview.knowledge.service.KnowledgeService;
import com.aiinterview.resume.mapper.ResumeMapper;
import com.aiinterview.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AI 教练降级单元测试。
 *
 * <p>不启动 Spring：直接 new {@link AgentService}，用 Mockito 替身隔离数据库与知识库，
 * 重点验证「无 API Key 时必须返回 fallback=true 且 citations 非空」，以及模型异常时不会把错误抛给前端。</p>
 */
class AgentFallbackTest {

    private static final Long USER_ID = 2L;
    private static final Long SESSION_ID = 100L;
    private static final String QUESTION_TEXT = "HashMap 的底层结构是什么？";

    private final LlmProperties properties = new LlmProperties();
    private final KnowledgeService knowledgeService = mock(KnowledgeService.class);
    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final AgentTraceMapper agentTraceMapper = mock(AgentTraceMapper.class);
    private final AgentLlmClient llmClient = mock(AgentLlmClient.class);

    /**
     * 每个用例后清理 SecurityContext，避免线程复用导致串号。
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 无 Key 时走确定性降级：回答、引用、调用链都要可验证。
     */
    @Test
    @DisplayName("无 API Key 时 coach 返回知识库降级回答，并记录 agent_trace")
    void shouldFallbackWithoutApiKey() {
        properties.setProvider("mock");
        properties.setApiKey("");
        loginAs(USER_ID, "CANDIDATE");
        stubSession();
        when(knowledgeService.search(anyString(), anyInt())).thenReturn(List.of(
                new KnowledgeHitVO("c1", "d1", "Java 面试答题结构", "JAVA",
                        "先给结论，再用 STAR 结构展开项目背景、目标、动作与量化结果，最后说明取舍。",
                        0.91, "MANUAL"),
                new KnowledgeHitVO("c2", "d1", "HashMap 原理速记", "JAVA",
                        "数组加链表，链表过长转红黑树，扩容时重新分布节点，回答时先说结论再讲版本差异。",
                        0.87, "MANUAL"),
                new KnowledgeHitVO("c3", "d2", "面试复盘方法", "GENERAL",
                        "每场面试只挑一个最卡的题复盘，写下改进点并在下一次练习中只改这一个点。",
                        0.75, "MANUAL")));
        when(agentTraceMapper.insert(any(AgentTrace.class))).thenReturn(1);

        CoachResponse response = service().coach(SESSION_ID, "下一题怎么答才能拿高分？");

        assertThat(response.fallback()).isTrue();
        assertThat(response.answer())
                .startsWith("（未配置模型，以下建议基于知识库检索）")
                .contains("1. ")
                .contains("2. ");
        assertThat(response.citations()).hasSize(2);
        assertThat(response.citations()).extracting(CitationVO::title)
                .containsExactly("Java 面试答题结构", "HashMap 原理速记");
        assertThat(response.toolCalls()).isEmpty();
        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0);
        verifyNoInteractions(llmClient);
        verify(knowledgeService).search(anyString(), eq(3));

        ArgumentCaptor<AgentTrace> captor = ArgumentCaptor.forClass(AgentTrace.class);
        verify(agentTraceMapper).insert(captor.capture());
        AgentTrace trace = captor.getValue();
        assertThat(trace.getScene()).isEqualTo("COACH");
        assertThat(trace.getUserId()).isEqualTo(USER_ID);
        assertThat(trace.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(trace.getInput()).isEqualTo("下一题怎么答才能拿高分？");
        assertThat(trace.getOutput()).contains("知识库");
        assertThat(trace.getToolCalls()).isEqualTo("[]");
        assertThat(trace.getSuccess()).isFalse();
        assertThat(trace.getLatencyMs()).isNotNull();
    }

    /**
     * 模型可用时必须真实执行 tool_calls，并把工具结果以 role=tool 回填给下一轮。
     */
    @Test
    @DisplayName("模型可用时执行 search_knowledge 工具循环，并记录工具轨迹")
    void shouldExecuteToolLoop() throws Exception {
        properties.setProvider("openai");
        properties.setApiKey("sk-test");
        loginAs(USER_ID, "CANDIDATE");
        stubSession();
        when(knowledgeService.search(anyString(), anyInt())).thenReturn(List.of(
                new KnowledgeHitVO("c1", "d1", "Java 面试答题结构", "JAVA",
                        "先给结论，再用 STAR 结构展开项目背景、目标、动作与量化结果。",
                        0.9, "MANUAL")));
        JsonNode toolCallTurn = JsonUtils.mapper().readTree("""
                {"choices":[{"message":{"role":"assistant","content":null,
                  "tool_calls":[{"id":"call_1","type":"function",
                    "function":{"name":"search_knowledge",
                      "arguments":"{\\"query\\":\\"Java 面试答题结构\\"}"}}]}}]}
                """);
        JsonNode finalTurn = JsonUtils.mapper().readTree("""
                {"choices":[{"message":{"role":"assistant","content":"先给结论：用 STAR 结构回答项目题。"}}]}
                """);
        when(llmClient.chat(any(), any())).thenReturn(toolCallTurn, finalTurn);
        when(agentTraceMapper.insert(any(AgentTrace.class))).thenReturn(1);

        CoachResponse response = service().coach(SESSION_ID, "项目题应该怎么答？");

        assertThat(response.fallback()).isFalse();
        assertThat(response.answer()).isEqualTo("先给结论：用 STAR 结构回答项目题。");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("search_knowledge");
        assertThat(response.toolCalls().get(0).arguments()).contains("Java 面试答题结构");
        assertThat(response.toolCalls().get(0).resultSummary()).contains("知识库");
        verify(llmClient, times(2)).chat(any(), any());
        verify(knowledgeService).search(anyString(), eq(3));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(2)).chat(messagesCaptor.capture(), any());
        List<Map<String, Object>> secondTurn = messagesCaptor.getAllValues().get(1);
        assertThat(secondTurn).anySatisfy(message ->
                assertThat(message.get("role")).isEqualTo("tool"));
        assertThat(secondTurn).anySatisfy(message ->
                assertThat(message.get("tool_call_id")).isEqualTo("call_1"));

        ArgumentCaptor<AgentTrace> traceCaptor = ArgumentCaptor.forClass(AgentTrace.class);
        verify(agentTraceMapper).insert(traceCaptor.capture());
        assertThat(traceCaptor.getValue().getSuccess()).isTrue();
        assertThat(traceCaptor.getValue().getToolCalls()).contains("search_knowledge");
    }

    /**
     * 配置了 Key 但模型不可达时，异常必须被吞掉并降级，而不是抛给前端。
     */
    @Test
    @DisplayName("模型调用异常时降级为知识库回答")
    void shouldFallbackWhenModelThrows() {
        properties.setProvider("openai");
        properties.setApiKey("sk-test");
        loginAs(USER_ID, "CANDIDATE");
        stubSession();
        when(knowledgeService.search(anyString(), anyInt())).thenReturn(List.of(
                new KnowledgeHitVO("c1", "d1", "面试复盘方法", "GENERAL",
                        "每场面试只挑一个最卡的题复盘，写下改进点并在下一次练习中只改这一个点。",
                        0.8, "MANUAL")));
        when(llmClient.chat(any(), any())).thenThrow(new IllegalStateException("timeout"));
        when(agentTraceMapper.insert(any(AgentTrace.class))).thenReturn(1);

        CoachResponse response = service().coach(SESSION_ID, "我该怎么复盘？");

        assertThat(response.fallback()).isTrue();
        assertThat(response.citations()).isNotEmpty();
        assertThat(response.answer()).contains("知识库");
        verify(llmClient).chat(any(), any());
    }

    /**
     * 越权访问必须在进入模型/知识库之前被拦截，异常类型是 403。
     */
    @Test
    @DisplayName("访问他人练习抛 403，不触发降级")
    void shouldRejectOtherUsersSession() {
        properties.setProvider("mock");
        properties.setApiKey("");
        loginAs(99L, "CANDIDATE");
        stubSession();

        assertThatThrownBy(() -> service().coach(SESSION_ID, "帮我看看这道题"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权");
        verifyNoInteractions(knowledgeService);
        verifyNoInteractions(llmClient);
    }

    /**
     * 构造被测服务，maxSteps 固定为 4。
     *
     * @return 教练服务
     */
    private AgentService service() {
        return new AgentService(properties, knowledgeService, interviewMapper, questionMapper,
                resumeMapper, agentTraceMapper, llmClient, 4);
    }

    /**
     * 模拟一场进行中的练习：已完成第 1 题，当前第 2 题。
     */
    private void stubSession() {
        Interview interview = new Interview();
        interview.setId(SESSION_ID);
        interview.setUserId(USER_ID);
        interview.setPositionId(3L);
        interview.setStatus(InterviewStatus.IN_PROGRESS);
        interview.setCurrentIndex(1);
        interview.setTotalQuestions(3);
        when(interviewMapper.selectById(SESSION_ID)).thenReturn(interview);

        InterviewQuestion answered = new InterviewQuestion();
        answered.setSeq(1);
        answered.setContent("介绍一下你最有代表性的项目");
        answered.setScore(new BigDecimal("82"));
        answered.setComment("结构清晰，缺少量化结果");

        InterviewQuestion current = new InterviewQuestion();
        current.setSeq(2);
        current.setContent(QUESTION_TEXT);

        InterviewQuestion next = new InterviewQuestion();
        next.setSeq(3);
        next.setContent("Redis 缓存穿透、击穿、雪崩怎么解决？");

        when(questionMapper.selectList(any())).thenReturn(List.of(answered, current, next));
    }

    /**
     * 把当前用户放入 SecurityContext，模拟 JWT 过滤器登录后的状态。
     *
     * @param userId 用户 id
     * @param role   角色
     */
    private static void loginAs(Long userId, String role) {
        LoginUser loginUser = new LoginUser(userId, "candidate", role);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                loginUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
