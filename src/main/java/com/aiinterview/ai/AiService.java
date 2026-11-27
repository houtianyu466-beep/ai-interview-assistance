package com.aiinterview.ai;

import com.aiinterview.ai.model.AnswerEvaluation;
import com.aiinterview.ai.model.GeneratedQuestion;
import com.aiinterview.ai.model.InterviewReportDraft;
import com.aiinterview.ai.model.MatchResult;
import com.aiinterview.ai.model.ParsedResume;
import com.aiinterview.ai.model.QaPair;
import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI 能力门面。
 *
 * <p>职责边界：</p>
 * <ul>
 *     <li>拼装 Prompt、调用 {@link LlmClient}、把文本解析成强类型对象；</li>
 *     <li>记录调用日志（耗时 / token / 失败原因）；</li>
 *     <li><b>不</b>负责业务降级——降级策略由各业务 Service 决定，
 *         因为它们掌握题库、简历等上下文，能给出更合理的兜底结果。</li>
 * </ul>
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final int MAX_RESUME_CHARS = 12000;

    private final LlmClient llmClient;
    private final AiCallLogService aiCallLogService;
    private final LlmProperties properties;

    public AiService(LlmClient llmClient, AiCallLogService aiCallLogService, LlmProperties properties) {
        this.llmClient = llmClient;
        this.aiCallLogService = aiCallLogService;
        this.properties = properties;
        log.info("AI 能力初始化 provider={} model={}", llmClient.providerName(), llmClient.modelName());
    }

    /**
     * 解析简历文本为结构化数据。
     */
    public ParsedResume parseResume(String rawText) {
        if (TextUtils.isBlank(rawText)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "简历内容为空");
        }
        List<LlmMessage> messages = List.of(
                LlmMessage.system(PromptTemplates.resumeParseSystem()),
                LlmMessage.user(PromptTemplates.resumeParseUser(TextUtils.truncate(rawText, MAX_RESUME_CHARS))));
        String content = chat(AiScenes.RESUME_PARSE, messages);
        return JsonUtils.parseAiJson(content, ParsedResume.class);
    }

    /**
     * 生成面试题。
     */
    public List<GeneratedQuestion> generateQuestions(String positionTitle, String jobDescription,
                                                     String resumeSummary, int count, String mode) {
        List<LlmMessage> messages = List.of(
                LlmMessage.system(PromptTemplates.questionGenerateSystem()),
                LlmMessage.user(PromptTemplates.questionGenerateUser(
                        positionTitle, jobDescription, resumeSummary, count, mode)));
        String content = chat(AiScenes.QUESTION_GENERATE, messages);
        List<GeneratedQuestion> questions = JsonUtils.fromJson(JsonUtils.stripCodeFence(content),
                new TypeReference<List<GeneratedQuestion>>() {
                });
        if (questions == null) {
            throw new BizException(ErrorCode.AI_ERROR, "AI 未返回题目");
        }
        return questions;
    }

    /**
     * 单题评分。
     */
    public AnswerEvaluation evaluateAnswer(String question, String referenceAnswer, String answer, String mode) {
        List<LlmMessage> messages = List.of(
                LlmMessage.system(PromptTemplates.answerScoreSystem()),
                LlmMessage.user(PromptTemplates.answerScoreUser(question, referenceAnswer, answer, mode)));
        String content = chat(AiScenes.ANSWER_SCORE, messages);
        return JsonUtils.parseAiJson(content, AnswerEvaluation.class);
    }

    /**
     * 简历与岗位匹配分析。
     */
    public MatchResult matchResume(String resumeText, String jobTitle, String jobRequirements) {
        List<LlmMessage> messages = List.of(
                LlmMessage.system(PromptTemplates.matchSystem()),
                LlmMessage.user(PromptTemplates.matchUser(resumeText, jobTitle, jobRequirements)));
        String content = chat(AiScenes.RESUME_MATCH, messages);
        return JsonUtils.parseAiJson(content, MatchResult.class);
    }

    /**
     * 生成整场面试报告。
     */
    public InterviewReportDraft generateReport(String positionTitle, List<QaPair> qaPairs,
                                               double overallScore, Map<String, Double> dimensionScores) {
        List<LlmMessage> messages = List.of(
                LlmMessage.system(PromptTemplates.reportGenerateSystem()),
                LlmMessage.user(PromptTemplates.reportGenerateUser(positionTitle, qaPairs, overallScore, dimensionScores)));
        String content = chat(AiScenes.REPORT_GENERATE, messages);
        return JsonUtils.parseAiJson(content, InterviewReportDraft.class);
    }

    /**
     * 生成一句练习提示（无知识库参考资料）。
     *
     * @param question        当前题目
     * @param referenceAnswer 参考答案，可为空
     * @param mode            练习模式
     * @return 模型返回的提示文本
     */
    public String hint(String question, String referenceAnswer, String mode) {
        return hint(question, referenceAnswer, mode, null);
    }

    /**
     * 生成一句练习提示（可携带知识库检索到的参考资料）。
     *
     * <p>练习提示是纯文本，不做 JSON 解析；调用失败时抛出 AI_ERROR，由练习 Service 决定兜底文案。
     * {@code context} 为空时与三参版本行为一致。</p>
     *
     * @param question        当前题目
     * @param referenceAnswer 参考答案，可为空
     * @param mode            练习模式
     * @param context         知识库参考资料，可为空
     * @return 模型返回的提示文本
     */
    public String hint(String question, String referenceAnswer, String mode, String context) {
        List<LlmMessage> messages = List.of(
                LlmMessage.system(PromptTemplates.hintSystem()),
                LlmMessage.user(PromptTemplates.hintUser(question, referenceAnswer, mode, context)));
        return chat(AiScenes.HINT, messages);
    }

    /**
     * 统一调用入口：记录耗时与 token，失败转为 AI_ERROR 便于上层降级。
     */
    private String chat(String scene, List<LlmMessage> messages) {
        long start = System.currentTimeMillis();
        try {
            LlmResponse response = llmClient.chat(scene, messages, properties.getTemperature());
            aiCallLogService.record(scene, llmClient.providerName(), response.model(),
                    response.promptTokens(), response.completionTokens(),
                    (int) (System.currentTimeMillis() - start), true, false, null);
            return response.content();
        } catch (Exception e) {
            aiCallLogService.record(scene, llmClient.providerName(), llmClient.modelName(),
                    null, null, (int) (System.currentTimeMillis() - start), false, true,
                    TextUtils.truncate(e.getMessage(), 400));
            throw new BizException(ErrorCode.AI_ERROR, "AI 服务调用失败，已启用规则兜底", e);
        }
    }
}
