package com.aiinterview.interview.service;

import com.aiinterview.ai.AiService;
import com.aiinterview.ai.PromptTemplates;
import com.aiinterview.ai.model.GeneratedQuestion;
import com.aiinterview.ai.model.ParsedResume;
import com.aiinterview.ai.model.ProjectExperience;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.entity.InterviewQuestion;
import com.aiinterview.interview.enums.InterviewMode;
import com.aiinterview.question.entity.QuestionBank;
import com.aiinterview.question.service.QuestionService;
import com.aiinterview.resume.entity.Resume;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 出题器单元测试：验证简历优先、历史去重、无简历题库优先与随机洗牌后的题号连续性。
 *
 * <p>不启动 Spring，也不访问真实模型与数据库：AI、题库、历史查询都用 Mockito 替身，
 * 因此无网络、无真实 Key 也能稳定运行。</p>
 */
class QuestionGeneratorTest {

    private static final Long USER_ID = 2L;
    private static final Long INTERVIEW_ID = 101L;

    private final AiService aiService = mock(AiService.class);
    private final QuestionService questionService = mock(QuestionService.class);
    private final QuestionHistoryService questionHistoryService = mock(QuestionHistoryService.class);

    /**
     * 有简历时，传给 AiService 的简历摘要必须包含姓名、年限、学历、技能、项目名与成果，
     * 历史题干也必须一并传入，并出现在最终 Prompt 的「禁止重复」区块中。
     */
    @Test
    @DisplayName("有简历时 AI 入参带简历摘要与历史题干")
    void shouldPassResumeSummaryAndHistoryToAi() {
        Interview interview = interview(2, InterviewMode.TECH);
        Resume resume = sampleResume();
        when(questionHistoryService.recentQuestionContents(USER_ID, INTERVIEW_ID))
                .thenReturn(List.of("你最有代表性的项目是什么？", "Redis 缓存穿透如何解决？"));
        when(aiService.generateQuestions(anyString(), anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(List.of(
                        question("你在交易中台订单服务重构中，如何用 Redis 把 P99 从 600ms 降到 120ms？"),
                        question("库存一致性方案里，Kafka 消息重复消费你如何保证幂等？")));

        List<InterviewQuestion> questions = generator().generate(interview, "Java 后端工程师", "重点考察项目与缓存", resume);

        assertThat(questions).hasSize(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> historyCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> resumeCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).generateQuestions(anyString(), anyString(), resumeCaptor.capture(),
                eq(2), anyString(), historyCaptor.capture());

        String resumeSummary = resumeCaptor.getValue();
        assertThat(resumeSummary)
                .contains("姓名：张伟")
                .contains("工作年限：5 年")
                .contains("学历：本科")
                .contains("技能：Java,Spring Boot,Redis,Kafka")
                .contains("项目：交易中台订单服务重构")
                .contains("成果：拆分单体订单服务")
                .contains("P99 从 600ms 降到 120ms");

        assertThat(historyCaptor.getValue())
                .containsExactly("你最有代表性的项目是什么？", "Redis 缓存穿透如何解决？");

        String prompt = PromptTemplates.questionGenerateUser("Java 后端工程师", "重点考察项目与缓存",
                resumeSummary, 2, "TECH", historyCaptor.getValue());
        assertThat(prompt)
                .contains("必须引用简历里的具体项目名、技术栈或成果数字")
                .contains("历史已问题目")
                .contains("禁止重复")
                .contains("Redis 缓存穿透如何解决？");
    }

    /**
     * AI 返回与历史题干相同（仅标点/空白不同）的题目必须被丢弃，最终题数仍由题库补齐到达标。
     */
    @Test
    @DisplayName("历史题干重复的 AI 题目会被过滤并补题")
    void shouldFilterHistoryDuplicateQuestions() {
        Interview interview = interview(3, InterviewMode.TECH);
        Resume resume = sampleResume();
        when(questionHistoryService.recentQuestionContents(USER_ID, INTERVIEW_ID))
                .thenReturn(List.of("历史：Java 题目？"));
        when(aiService.generateQuestions(anyString(), anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(List.of(
                        question("历史 Java 题目"),
                        question("请结合交易中台订单服务重构，说明 Redis 缓存与本地消息表如何保证一致性？"),
                        question("库存一致性方案中，Kafka 重复消费如何设计幂等键？")));
        when(questionService.pickRandom(any(), any(), anyInt()))
                .thenReturn(List.of(bankQuestion("Spring Boot 应用如何优雅停机并保证请求不丢失？")));

        List<InterviewQuestion> questions = generator().generate(interview, "Java 后端工程师", "重点考察项目", resume);

        assertThat(questions).hasSize(3);
        assertThat(questions).extracting(InterviewQuestion::getSeq).containsExactly(1, 2, 3);
        assertThat(questions).extracting(InterviewQuestion::getContent)
                .noneMatch(content -> normalize(content).equals(normalize("历史：Java 题目？")));
        Set<String> normalizedContents = questions.stream()
                .map(question -> normalize(question.getContent()))
                .collect(Collectors.toSet());
        assertThat(normalizedContents).hasSize(3);
    }

    /**
     * 无简历时先查题库；题库题数足够时不允许调用 AI。
     */
    @Test
    @DisplayName("无简历时优先使用题库且不调用 AI")
    void shouldPreferQuestionBankWithoutResume() {
        Interview interview = interview(3, InterviewMode.TECH);
        when(questionHistoryService.recentQuestionContents(USER_ID, INTERVIEW_ID)).thenReturn(List.of());
        when(questionService.pickRandom(any(), any(), anyInt())).thenReturn(List.of(
                bankQuestion("HashMap 的底层结构与扩容机制是什么？"),
                bankQuestion("Spring Bean 的生命周期包含哪些阶段？"),
                bankQuestion("MySQL 索引失效的常见场景有哪些？")));

        List<InterviewQuestion> questions = generator().generate(interview, "Java 后端工程师", "重点考察基础", null);

        assertThat(questions).hasSize(3);
        assertThat(questions).allSatisfy(question -> {
            assertThat(question.getContent()).isNotBlank();
            assertThat(question.getCategory()).isNotBlank();
        });
        verify(questionService).pickRandom(null, null, 3);
        verify(aiService, never()).generateQuestions(anyString(), anyString(), anyString(),
                anyInt(), anyString(), any());
    }

    /**
     * 题库不足时用 AI 补题，最终洗牌后 seq 仍必须严格为 1..n。
     */
    @Test
    @DisplayName("题库不足时 AI 补题，洗牌后 seq 仍连续递增")
    void shouldKeepSequentialSeqAfterShuffle() {
        Interview interview = interview(5, InterviewMode.COMPREHENSIVE);
        when(questionHistoryService.recentQuestionContents(USER_ID, INTERVIEW_ID)).thenReturn(List.of());
        when(questionService.pickRandom(any(), any(), anyInt())).thenReturn(List.of(
                bankQuestion("题库题一：HashMap 的底层结构是什么？"),
                bankQuestion("题库题二：线程池的核心参数有哪些？")));
        when(aiService.generateQuestions(anyString(), anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(List.of(
                        question("AI 题一：如何设计高并发下的幂等接口？"),
                        question("AI 题二：分布式事务有哪些落地方案？"),
                        question("AI 题三：线上接口变慢的排查思路是什么？")));

        List<InterviewQuestion> questions = generator().generate(interview, "Java 后端工程师", "综合考察", null);

        assertThat(questions).hasSize(5);
        List<Integer> seqs = new ArrayList<>(questions.stream().map(InterviewQuestion::getSeq).toList());
        assertThat(seqs).containsExactly(1, 2, 3, 4, 5);
        assertThat(questions).extracting(InterviewQuestion::getContent).doesNotHaveDuplicates();
        verify(aiService, times(1)).generateQuestions(anyString(), anyString(), anyString(),
                eq(3), anyString(), any());
    }

    /**
     * 构造被测出题器。
     *
     * @return 出题器实例
     */
    private QuestionGenerator generator() {
        return new QuestionGenerator(aiService, questionService, questionHistoryService);
    }

    /**
     * 构造一场练习会话。
     *
     * @param totalQuestions 题数
     * @param mode           面试模式
     * @return 会话实体
     */
    private static Interview interview(int totalQuestions, InterviewMode mode) {
        Interview interview = new Interview();
        interview.setId(INTERVIEW_ID);
        interview.setUserId(USER_ID);
        interview.setPositionId(1L);
        interview.setMode(mode);
        interview.setTotalQuestions(totalQuestions);
        interview.setCurrentIndex(0);
        return interview;
    }

    /**
     * 构造一份带项目与成果的已解析简历。
     *
     * @return 简历实体
     */
    private static Resume sampleResume() {
        ParsedResume parsed = new ParsedResume(
                "张伟", "zhangwei@example.com", "13800000003", 5, "本科",
                List.of("Java", "Spring Boot", "Redis", "Kafka"),
                List.of(),
                List.of(new ProjectExperience("交易中台订单服务重构", "技术负责人", "2023.03-2023.12",
                                "拆分单体订单服务，引入 Redis 缓存与本地消息表解决分布式事务，P99 从 600ms 降到 120ms。"),
                        new ProjectExperience("库存一致性方案", "核心开发", "2022.05-2022.10",
                                "基于 Kafka 实现库存扣减最终一致，支持幂等与补偿。")),
                List.of("有高并发交易链路经验"),
                "5 年 Java 后端经验，擅长交易与高并发场景。");
        Resume resume = new Resume();
        resume.setId(9L);
        resume.setUserId(USER_ID);
        resume.setParsedJson(JsonUtils.toJson(parsed));
        resume.setSkills(String.join(",", parsed.skills()));
        resume.setYearsOfExperience(5);
        resume.setEducation("本科");
        return resume;
    }

    /**
     * 构造一条 AI 题目。
     *
     * @param content 题干
     * @return AI 题目
     */
    private static GeneratedQuestion question(String content) {
        return new GeneratedQuestion(content, "JAVA", "3",
                "说明原理、场景与取舍，并给出量化结果。", List.of("原理", "场景", "取舍"));
    }

    /**
     * 构造一条题库题目。
     *
     * @param content 题干
     * @return 题库题目
     */
    private static QuestionBank bankQuestion(String content) {
        QuestionBank question = new QuestionBank();
        question.setCategory("JAVA");
        question.setDifficulty(3);
        question.setContent(content);
        question.setReferenceAnswer("先给结论，再解释原理与适用场景。");
        question.setKeywords("原理,场景");
        return question;
    }

    /**
     * 与出题器一致的归一化：去掉空白/标点并转小写，用于断言历史重复题已被过滤。
     *
     * @param content 题干
     * @return 归一化题干
     */
    private static String normalize(String content) {
        StringBuilder builder = new StringBuilder();
        for (char c : content.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }
}
