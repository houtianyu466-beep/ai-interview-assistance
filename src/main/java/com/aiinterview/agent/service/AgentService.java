package com.aiinterview.agent.service;

import com.aiinterview.agent.dto.AgentTraceVO;
import com.aiinterview.agent.dto.CitationVO;
import com.aiinterview.agent.dto.CoachResponse;
import com.aiinterview.agent.dto.ToolCallVO;
import com.aiinterview.agent.entity.AgentTrace;
import com.aiinterview.agent.mapper.AgentTraceMapper;
import com.aiinterview.ai.LlmProperties;
import com.aiinterview.ai.model.ParsedResume;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.entity.InterviewQuestion;
import com.aiinterview.interview.mapper.InterviewMapper;
import com.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.aiinterview.knowledge.dto.KnowledgeHitVO;
import com.aiinterview.knowledge.service.KnowledgeService;
import com.aiinterview.resume.entity.Resume;
import com.aiinterview.resume.enums.ResumeStatus;
import com.aiinterview.resume.mapper.ResumeMapper;
import com.aiinterview.security.SecurityUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试内 AI 教练服务。
 *
 * <p>核心流程：</p>
 * <ol>
 *     <li>校验练习会话归属（本人或管理员）；</li>
 *     <li>装载上下文（进度、当前题、已答得分）；</li>
 *     <li>模型可用时进入 function calling 工具循环：模型可以检索知识库、读取练习状态、读取本人简历，
 *         每次 {@code tool_calls} 都由本服务真实执行并把结果回填给模型；</li>
 *     <li>模型不可用、返回空内容或调用异常时，降级为基于 {@link KnowledgeService} 的确定性回答；</li>
 *     <li>无论成功还是降级，都写一条 {@code agent_trace} 记录本轮行为。</li>
 * </ol>
 *
 * <p>降级只发生在「模型侧」；未登录、会话不存在、越权访问仍按 401/404/403 抛给前端。</p>
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    /** 调用链场景 */
    private static final String SCENE_COACH = "COACH";
    /** 单次查询最多返回的调用链条数 */
    private static final int MAX_TRACES = 50;
    /** output 落库截断长度 */
    private static final int MAX_OUTPUT = 4000;
    /** 引用摘要截断长度 */
    private static final int MAX_SNIPPET = 120;
    /** 降级建议单条截断长度 */
    private static final int MAX_FALLBACK_ITEM = 100;
    /** 降级检索 topK */
    private static final int FALLBACK_TOP_K = 3;
    /** search_knowledge 默认 topK */
    private static final int DEFAULT_TOP_K = 3;
    /** search_knowledge 允许的最大 topK */
    private static final int MAX_TOP_K = 10;
    /** 引用条数上限 */
    private static final int MAX_CITATIONS = 3;
    /** 工具结果摘要截断长度 */
    private static final int MAX_TOOL_SUMMARY = 200;
    /** 工具结果中单条知识截断长度 */
    private static final int MAX_TOOL_KNOWLEDGE = 600;
    /** 工具结果中单条点评截断长度 */
    private static final int MAX_COMMENT = 80;
    /** 简历摘要截断长度 */
    private static final int MAX_RESUME_SUMMARY = 300;
    /** 降级回答固定抬头 */
    private static final String FALLBACK_PREFIX = "（未配置模型，以下建议基于知识库检索）";

    /** 教练人设与输出约束 */
    private static final String SYSTEM_PROMPT = """
            你是一位面试教练，服务对象是第一次参加面试的求职者。
            回答要求：
            1. 先给结论，再给 2-3 条可执行建议；
            2. 建议要具体到「今天就能做的动作」，避免空泛；
            3. 引用知识库内容时说明来源（标题或来源文件名），不要编造来源；
            4. 缺少信息时优先调用工具查询，不要臆测；
            5. 使用中文，控制在 400 字以内。
            """;

    private final LlmProperties properties;
    private final KnowledgeService knowledgeService;
    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;
    private final ResumeMapper resumeMapper;
    private final AgentTraceMapper agentTraceMapper;
    private final AgentLlmClient agentLlmClient;
    private final int maxSteps;

    /**
     * 构造器注入。
     *
     * @param properties        大模型配置（provider / apiKey / model / temperature）
     * @param knowledgeService  RAG 知识库检索服务
     * @param interviewMapper   练习会话 Mapper
     * @param questionMapper    题目 Mapper
     * @param resumeMapper      简历 Mapper
     * @param agentTraceMapper  调用链 Mapper
     * @param agentLlmClient    带 function calling 的模型客户端
     * @param maxSteps          工具循环最大轮数，取 {@code app.agent.max-steps}，默认 4
     */
    public AgentService(LlmProperties properties,
                        KnowledgeService knowledgeService,
                        InterviewMapper interviewMapper,
                        InterviewQuestionMapper questionMapper,
                        ResumeMapper resumeMapper,
                        AgentTraceMapper agentTraceMapper,
                        AgentLlmClient agentLlmClient,
                        @Value("${app.agent.max-steps:4}") int maxSteps) {
        this.properties = properties;
        this.knowledgeService = knowledgeService;
        this.interviewMapper = interviewMapper;
        this.questionMapper = questionMapper;
        this.resumeMapper = resumeMapper;
        this.agentTraceMapper = agentTraceMapper;
        this.agentLlmClient = agentLlmClient;
        this.maxSteps = maxSteps;
    }

    /**
     * 向 AI 教练提问。
     *
     * @param sessionId 练习会话 id
     * @param message   用户问题
     * @return 教练回答（含引用、工具轨迹、是否降级与耗时）
     * @throws BizException 未登录（401）、会话不存在（404）或越权（403）
     */
    public CoachResponse coach(Long sessionId, String message) {
        long start = System.currentTimeMillis();
        Long userId = SecurityUtils.currentUserId();
        Interview interview = requireOwned(sessionId, userId);
        CoachContext context;
        try {
            context = loadContext(interview, userId);
        } catch (Exception e) {
            // 题目表临时不可用时仍要回答：退化为「无当前题」的上下文，由知识库兜底
            log.warn("加载练习上下文失败，使用空上下文继续 sessionId={} reason={}",
                    sessionId, e.getMessage(), e);
            context = new CoachContext(userId, interview, null, List.of());
        }

        CoachResponse response;
        if (!modelAvailable()) {
            log.debug("AI 教练未配置真实模型，直接走知识库降级 sessionId={}", sessionId);
            response = fallback(message, context.currentQuestion(), List.of(), start);
        } else {
            List<ToolCallVO> toolCalls = new ArrayList<>();
            try {
                response = runToolLoop(context, message, toolCalls, start);
            } catch (Exception e) {
                // 工具循环里的任何异常都不抛给前端，统一降级并用同一轮已产生的轨迹记账
                log.warn("AI 教练工具循环失败，降级为知识库回答 sessionId={} reason={}",
                        sessionId, e.getMessage(), e);
                response = fallback(message, context.currentQuestion(), toolCalls, start);
            }
        }
        recordTrace(userId, sessionId, message, response);
        return response;
    }

    /**
     * 查询某个练习会话最近的教练调用链。
     *
     * @param sessionId 练习会话 id
     * @return 调用链列表，按创建时间倒序，最多 50 条
     * @throws BizException 未登录（401）、会话不存在（404）或越权（403）
     */
    public List<AgentTraceVO> traces(Long sessionId) {
        Long userId = SecurityUtils.currentUserId();
        requireOwned(sessionId, userId);
        return agentTraceMapper.selectList(Wrappers.<AgentTrace>lambdaQuery()
                        .eq(AgentTrace::getSessionId, sessionId)
                        .orderByDesc(AgentTrace::getCreatedAt)
                        .orderByDesc(AgentTrace::getId)
                        .last("LIMIT " + MAX_TRACES))
                .stream()
                .map(AgentService::toTraceVO)
                .toList();
    }

    /**
     * 确定性降级回答：不依赖模型，直接用知识库命中拼装建议。
     *
     * <p>公开出来是为了让单元测试/其他降级场景可以单独复用；正常请求请走 {@link #coach(Long, String)}。</p>
     *
     * @param message         用户问题
     * @param currentQuestion 当前题题干，可为空
     * @return 固定返回 {@code fallback=true} 的回答
     */
    public CoachResponse fallbackAnswer(String message, String currentQuestion) {
        InterviewQuestion question = null;
        if (!TextUtils.isBlank(currentQuestion)) {
            question = new InterviewQuestion();
            question.setContent(currentQuestion);
        }
        return fallback(message, question, List.of(), System.currentTimeMillis());
    }

    /**
     * 执行 function calling 工具循环，直到模型给出最终答案或用尽轮数。
     *
     * @param context   本轮只读上下文
     * @param message   用户问题
     * @param toolCalls 工具调用轨迹（可变，便于异常时保留已执行部分）
     * @param start     本轮对话开始时间
     * @return 模型回答或降级回答
     */
    private CoachResponse runToolLoop(CoachContext context, String message,
                                      List<ToolCallVO> toolCalls, long start) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(chatMessage("system", buildSystemPrompt(context)));
        messages.add(chatMessage("user", message));

        List<KnowledgeHitVO> knowledgeHits = new ArrayList<>();
        for (int step = 0; step < maxSteps; step++) {
            JsonNode root = agentLlmClient.chat(messages, toolDefinitions());
            JsonNode assistant = root.path("choices").path(0).path("message");
            JsonNode calls = assistant.path("tool_calls");
            if (calls.isArray() && !calls.isEmpty()) {
                // 保留 assistant 的 tool_calls 消息，再把每个工具结果以 role=tool 回填
                messages.add(assistantMessage(assistant.path("content").asText(""), calls));
                for (JsonNode call : calls) {
                    String callId = call.path("id").asText("call_" + toolCalls.size());
                    String name = call.path("function").path("name").asText("");
                    String arguments = call.path("function").path("arguments").asText("");
                    String result = executeTool(name, arguments, context, knowledgeHits);
                    toolCalls.add(new ToolCallVO(name, arguments,
                            TextUtils.truncate(result, MAX_TOOL_SUMMARY)));
                    messages.add(toolMessage(callId, result));
                }
                continue;
            }
            String content = assistant.path("content").asText("");
            if (TextUtils.isBlank(content)) {
                log.warn("AI 教练既无内容也无工具调用，降级 sessionId={} step={}",
                        context.interview().getId(), step + 1);
                return fallback(message, context.currentQuestion(), toolCalls, start);
            }
            return new CoachResponse(content.strip(), citations(knowledgeHits),
                    List.copyOf(toolCalls), false, System.currentTimeMillis() - start);
        }
        log.warn("AI 教练达到最大工具轮数仍未给出答案，降级 sessionId={} maxSteps={}",
                context.interview().getId(), maxSteps);
        return fallback(message, context.currentQuestion(), toolCalls, start);
    }

    /**
     * 执行一次工具调用。
     *
     * @param name    工具名
     * @param arguments 模型给出的参数 JSON
     * @param context 只读上下文
     * @param hits    知识库命中收集器（search_knowledge 写入，用于最终引用）
     * @return 回填给模型的文本结果
     */
    private String executeTool(String name, String arguments, CoachContext context,
                               List<KnowledgeHitVO> hits) {
        return switch (name) {
            case "search_knowledge" -> searchKnowledge(arguments, hits);
            case "get_practice_state" -> practiceState(context);
            case "get_my_resume" -> myResume(context.userId());
            default -> "未知工具：" + name + "，可用工具为 search_knowledge / get_practice_state / get_my_resume";
        };
    }

    /**
     * 执行 search_knowledge：检索知识库并把命中收集起来供最终引用。
     *
     * @param arguments 参数 JSON（query 必填，topK 可选）
     * @param hits      命中收集器
     * @return 供模型阅读的检索结果
     */
    private String searchKnowledge(String arguments, List<KnowledgeHitVO> hits) {
        JsonNode args = parseArguments(arguments);
        String query = args.path("query").asText("");
        if (TextUtils.isBlank(query)) {
            return "检索失败：search_knowledge 需要非空的 query 参数。";
        }
        int topK = DEFAULT_TOP_K;
        JsonNode topKNode = args.path("topK");
        if (topKNode.canConvertToInt()) {
            topK = Math.max(1, Math.min(MAX_TOP_K, topKNode.asInt()));
        }
        List<KnowledgeHitVO> found = knowledgeService.search(query, topK);
        if (found == null || found.isEmpty()) {
            return "知识库未检索到与「" + query + "」相关的内容，请基于通用面试经验回答并说明未命中知识库。";
        }
        hits.addAll(found);
        StringBuilder result = new StringBuilder("知识库检索到 ").append(found.size())
                .append(" 条内容（query=").append(query).append("）：");
        for (int i = 0; i < found.size(); i++) {
            KnowledgeHitVO hit = found.get(i);
            result.append('\n').append(i + 1).append(". 【").append(titleOf(hit)).append("】");
            if (!TextUtils.isBlank(hit.category())) {
                result.append(" 分类：").append(hit.category());
            }
            if (!TextUtils.isBlank(hit.source())) {
                result.append(" 来源：").append(hit.source());
            }
            result.append('\n').append(TextUtils.truncate(hit.content(), MAX_TOOL_KNOWLEDGE));
        }
        return result.toString();
    }

    /**
     * 执行 get_practice_state：返回进度、当前题与最近 3 题得分点评。
     *
     * @param context 只读上下文
     * @return 练习状态文本
     */
    private String practiceState(CoachContext context) {
        Interview interview = context.interview();
        StringBuilder state = new StringBuilder();
        state.append("练习方向 id：")
                .append(interview.getPositionId() == null ? "未知" : interview.getPositionId())
                .append('\n');
        state.append("状态：")
                .append(interview.getStatus() == null ? "未知" : interview.getStatus().name())
                .append('\n');
        state.append("进度：").append(progressText(interview, context.questions().size())).append('\n');
        state.append("当前题：")
                .append(context.currentQuestion() == null
                        ? "无（练习已结束或尚未开始）" : context.currentQuestion().getContent())
                .append('\n');

        List<InterviewQuestion> answered = context.questions().stream()
                .filter(question -> question.getScore() != null)
                .toList();
        state.append("最近得分（最多 3 题）：");
        if (answered.isEmpty()) {
            state.append("暂无已评分题目");
        } else {
            int from = Math.max(0, answered.size() - MAX_CITATIONS);
            for (int i = from; i < answered.size(); i++) {
                InterviewQuestion question = answered.get(i);
                state.append("\n- 第").append(question.getSeq()).append("题：")
                        .append(scoreText(question.getScore())).append(" 分");
                if (!TextUtils.isBlank(question.getComment())) {
                    state.append("，点评：").append(TextUtils.truncate(question.getComment(), MAX_COMMENT));
                }
            }
        }
        return state.toString();
    }

    /**
     * 执行 get_my_resume：返回当前用户最新一份 PARSED 简历的技能、年限、学历与摘要。
     *
     * @param userId 当前用户 id
     * @return 简历文本，未上传时给出提示
     */
    private String myResume(Long userId) {
        Resume resume = resumeMapper.selectOne(Wrappers.<Resume>lambdaQuery()
                .eq(Resume::getUserId, userId)
                .eq(Resume::getStatus, ResumeStatus.PARSED)
                .orderByDesc(Resume::getCreatedAt)
                .orderByDesc(Resume::getId)
                .last("LIMIT 1"));
        if (resume == null) {
            return "当前用户还没有解析完成（PARSED）的简历，请提示用户先上传并解析简历。";
        }
        String summary = parsedSummary(resume);
        if (TextUtils.isBlank(summary)) {
            summary = resume.getRawText();
        }
        StringBuilder result = new StringBuilder();
        result.append("简历：").append(TextUtils.defaultIfBlank(resume.getFileName(), "未命名")).append('\n');
        result.append("技能：").append(TextUtils.defaultIfBlank(resume.getSkills(), "未提取")).append('\n');
        result.append("工作年限：")
                .append(resume.getYearsOfExperience() == null
                        ? "未知" : resume.getYearsOfExperience() + " 年")
                .append('\n');
        result.append("学历：").append(TextUtils.defaultIfBlank(resume.getEducation(), "未知")).append('\n');
        result.append("摘要：")
                .append(TextUtils.isBlank(summary) ? "未提供" : TextUtils.truncate(summary, MAX_RESUME_SUMMARY));
        return result.toString();
    }

    /**
     * 从简历解析 JSON 中取摘要，解析失败时返回 {@code null} 并退化为原文。
     *
     * @param resume 简历实体
     * @return 结构化摘要，可为 {@code null}
     */
    private static String parsedSummary(Resume resume) {
        if (TextUtils.isBlank(resume.getParsedJson())) {
            return null;
        }
        try {
            ParsedResume parsed = JsonUtils.fromJson(resume.getParsedJson(), ParsedResume.class);
            return parsed == null ? null : parsed.summary();
        } catch (Exception e) {
            log.debug("简历解析 JSON 不可用，退化为原文 resumeId={} reason={}",
                    resume.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 组装 system prompt：固定人设 + 当前练习上下文。
     *
     * @param context 只读上下文
     * @return system prompt
     */
    private String buildSystemPrompt(CoachContext context) {
        Interview interview = context.interview();
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT);
        prompt.append("\n\n【当前练习上下文】\n");
        prompt.append("练习状态：")
                .append(interview.getStatus() == null ? "未知" : interview.getStatus().name())
                .append('\n');
        prompt.append("进度：").append(progressText(interview, context.questions().size())).append('\n');
        prompt.append("当前题：")
                .append(context.currentQuestion() == null ? "无" : context.currentQuestion().getContent())
                .append('\n');
        prompt.append("已答得分：").append(scoreSummary(context.questions())).append('\n');
        prompt.append("需要知识库依据时调用 search_knowledge，需要进度时调用 get_practice_state，需要简历时调用 get_my_resume。");
        return prompt.toString();
    }

    /**
     * 把检索命中组装成引用：按内容去重后取前 3 条。
     *
     * @param hits 全部知识库命中
     * @return 引用列表，最多 3 条
     */
    private static List<CitationVO> citations(List<KnowledgeHitVO> hits) {
        Map<String, KnowledgeHitVO> unique = new LinkedHashMap<>();
        for (KnowledgeHitVO hit : hits) {
            if (hit == null) {
                continue;
            }
            String key = titleOf(hit) + "|" + hit.content();
            unique.putIfAbsent(key, hit);
        }
        return unique.values().stream()
                .limit(MAX_CITATIONS)
                .map(hit -> new CitationVO(titleOf(hit),
                        TextUtils.defaultIfBlank(TextUtils.truncate(hit.content(), MAX_SNIPPET), ""),
                        hit.score()))
                .toList();
    }

    /**
     * 降级回答：用「用户问题 + 当前题」检索知识库，命中内容前 100 字逐条给出建议。
     *
     * @param message         用户问题
     * @param currentQuestion 当前题，可为空
     * @param toolCalls       本轮已执行的工具轨迹（异常降级时保留）
     * @param start           本轮对话开始时间
     * @return fallback=true 的回答
     */
    private CoachResponse fallback(String message, InterviewQuestion currentQuestion,
                                   List<ToolCallVO> toolCalls, long start) {
        // 优先用当前题题干检索：用户问「这题怎么答」时，题干才是检索意图，避免被「怎么答」等词带偏
        String questionText = currentQuestion == null ? "" : TextUtils.defaultIfBlank(currentQuestion.getContent(), "");
        List<KnowledgeHitVO> hits = TextUtils.isBlank(questionText)
                ? safeSearch(message, FALLBACK_TOP_K)
                : safeSearch(questionText, FALLBACK_TOP_K);
        if (hits.isEmpty() && !TextUtils.isBlank(message)) {
            hits = safeSearch(message, FALLBACK_TOP_K);
        }
        // 只保留与最佳命中分数接近的 1-2 条，避免把弱相关内容也拼进回答
        if (!hits.isEmpty()) {
            double topScore = hits.get(0).score();
            List<KnowledgeHitVO> relevant = new ArrayList<>();
            for (KnowledgeHitVO hit : hits) {
                if (hit.score() >= Math.max(0.05, topScore * 0.4) && relevant.size() < 2) {
                    relevant.add(hit);
                }
            }
            hits = relevant.isEmpty() ? List.of(hits.get(0)) : relevant;
        }

        List<String> items = new ArrayList<>();
        for (KnowledgeHitVO hit : hits) {
            if (items.size() >= MAX_CITATIONS) {
                break;
            }
            String content = TextUtils.defaultIfBlank(hit.content(), "").replaceAll("\\s+", " ").trim();
            if (!content.isEmpty()) {
                items.add(TextUtils.truncate(content, MAX_FALLBACK_ITEM));
            }
        }
        if (items.isEmpty()) {
            items.add("用 STAR 结构（背景、目标、动作、结果）准备一个 2 分钟的项目案例。");
            items.add("每道题先给结论，再补两个论据，最后说明取舍。");
            items.add("复盘本次卡壳点，下一次练习只改一个点，并录音回听。");
        }
        StringBuilder answer = new StringBuilder(FALLBACK_PREFIX);
        for (int i = 0; i < items.size(); i++) {
            answer.append('\n').append(i + 1).append(". ").append(items.get(i));
        }
        return new CoachResponse(answer.toString(), citations(hits), List.copyOf(toolCalls), true,
                System.currentTimeMillis() - start);
    }

    /**
     * 尽力检索：知识库不可用时返回空列表，保证降级回答仍能生成。
     *
     * @param query 检索词
     * @param topK  期望条数
     * @return 命中列表，失败时为空列表
     */
    private List<KnowledgeHitVO> safeSearch(String query, int topK) {
        if (TextUtils.isBlank(query)) {
            return List.of();
        }
        try {
            List<KnowledgeHitVO> hits = knowledgeService.search(query, topK);
            return hits == null ? List.of() : hits;
        } catch (Exception e) {
            log.warn("知识库检索失败，使用通用建议 query={} reason={}",
                    TextUtils.truncate(query, 80), e.getMessage());
            return List.of();
        }
    }

    /**
     * 写入调用链。写库失败只 warn，不影响本次回答。
     *
     * @param userId   用户 id
     * @param sessionId 练习会话 id
     * @param message  用户输入
     * @param response 本轮回答
     */
    private void recordTrace(Long userId, Long sessionId, String message, CoachResponse response) {
        try {
            AgentTrace trace = new AgentTrace();
            trace.setUserId(userId);
            trace.setSessionId(sessionId);
            trace.setScene(SCENE_COACH);
            trace.setInput(message);
            trace.setToolCalls(JsonUtils.toJson(response.toolCalls()));
            trace.setOutput(TextUtils.truncate(response.answer(), MAX_OUTPUT));
            trace.setSuccess(!response.fallback());
            trace.setLatencyMs((int) response.latencyMs());
            agentTraceMapper.insert(trace);
        } catch (Exception e) {
            log.warn("写入 AI 教练调用链失败 sessionId={}", sessionId, e);
        }
    }

    /**
     * 加载会话并校验归属：本人或管理员可访问。
     *
     * @param sessionId 练习会话 id
     * @param userId    当前用户 id
     * @return 会话实体
     * @throws BizException 不存在（404）或越权（403）
     */
    private Interview requireOwned(Long sessionId, Long userId) {
        if (sessionId == null) {
            throw BizException.badRequest("练习 id 不能为空");
        }
        Interview interview = interviewMapper.selectById(sessionId);
        if (interview == null) {
            throw BizException.notFound("练习不存在");
        }
        if (!SecurityUtils.isAdmin() && !userId.equals(interview.getUserId())) {
            throw BizException.forbidden("无权访问该练习");
        }
        return interview;
    }

    /**
     * 装载本轮上下文：全部题目按 seq 升序，并定位当前题。
     *
     * @param interview 会话
     * @param userId    当前用户 id
     * @return 只读上下文
     */
    private CoachContext loadContext(Interview interview, Long userId) {
        List<InterviewQuestion> questions = questionMapper.selectList(
                Wrappers.<InterviewQuestion>lambdaQuery()
                        .eq(InterviewQuestion::getInterviewId, interview.getId())
                        .orderByAsc(InterviewQuestion::getSeq));
        if (questions == null) {
            questions = List.of();
        }
        int currentIndex = interview.getCurrentIndex() == null ? 0 : interview.getCurrentIndex();
        InterviewQuestion currentQuestion = questions.stream()
                .filter(question -> question.getSeq() != null && question.getSeq() == currentIndex + 1)
                .findFirst()
                .orElse(null);
        return new CoachContext(userId, interview, currentQuestion, questions);
    }

    /**
     * 判断是否配置了可用的真实模型：provider 不能是 mock，且 apiKey 非空。
     *
     * @return 是否可调用真实模型
     */
    private boolean modelAvailable() {
        return properties.getProvider() != null
                && !"mock".equalsIgnoreCase(properties.getProvider())
                && !TextUtils.isBlank(properties.getApiKey());
    }

    /**
     * 构造 OpenAI 协议的一条消息。
     *
     * @param role    角色
     * @param content 内容
     * @return 消息 Map
     */
    private static Map<String, Object> chatMessage(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content == null ? "" : content);
        return message;
    }

    /**
     * 把模型返回的 assistant tool_calls 转成协议要求的消息。
     *
     * @param content 模型同轮返回的文本，可为空
     * @param calls   tool_calls 节点
     * @return assistant 消息
     */
    private static Map<String, Object> assistantMessage(String content, JsonNode calls) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (JsonNode call : calls) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", call.path("function").path("name").asText(""));
            function.put("arguments", call.path("function").path("arguments").asText(""));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", call.path("id").asText(""));
            item.put("type", "function");
            item.put("function", function);
            serialized.add(item);
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content == null ? "" : content);
        message.put("tool_calls", serialized);
        return message;
    }

    /**
     * 构造 role=tool 的回填消息。
     *
     * @param callId  对应的 tool_call id
     * @param content 工具执行结果
     * @return tool 消息
     */
    private static Map<String, Object> toolMessage(String callId, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", callId);
        message.put("content", content);
        return message;
    }

    /**
     * 安全解析工具参数；非法 JSON 按空对象处理，让模型自行纠正。
     *
     * @param arguments 参数 JSON
     * @return 解析后的节点
     */
    private static JsonNode parseArguments(String arguments) {
        if (TextUtils.isBlank(arguments)) {
            return JsonUtils.mapper().createObjectNode();
        }
        try {
            return JsonUtils.mapper().readTree(arguments);
        } catch (Exception e) {
            return JsonUtils.mapper().createObjectNode();
        }
    }

    /**
     * OpenAI tools 定义：三个工具均无副作用的只读查询。
     *
     * @return tools 数组
     */
    private static List<Map<String, Object>> toolDefinitions() {
        Map<String, Object> searchProperties = new LinkedHashMap<>();
        searchProperties.put("query", Map.of(
                "type", "string",
                "description", "要检索的知识库关键词或问题，尽量具体"));
        searchProperties.put("topK", Map.of(
                "type", "integer",
                "description", "返回条数，默认 3，最大 10"));
        return List.of(
                functionDefinition("search_knowledge",
                        "检索面试知识库，返回与 query 相关的知识片段。引用时必须说明来源。",
                        searchProperties, List.of("query")),
                functionDefinition("get_practice_state",
                        "查询当前练习的方向、进度、当前题与最近 3 题得分点评。",
                        Map.of(), List.of()),
                functionDefinition("get_my_resume",
                        "查询当前用户最新一份已解析简历的技能、工作年限、学历与摘要。",
                        Map.of(), List.of()));
    }

    /**
     * 构造单个 OpenAI function 工具定义。
     *
     * @param name        工具名
     * @param description 工具描述
     * @param properties  参数定义
     * @param required    必填参数
     * @return 工具定义
     */
    private static Map<String, Object> functionDefinition(String name, String description,
                                                          Map<String, Object> properties,
                                                          List<String> required) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        if (!required.isEmpty()) {
            parameters.put("required", required);
        }
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    /**
     * 进度文案，例如「已完成 2 题 / 共 5 题（当前第 3 题）」。
     *
     * @param interview    会话
     * @param fallbackSize 题数配置缺失时的回退值
     * @return 进度文案
     */
    private static String progressText(Interview interview, int fallbackSize) {
        int index = interview.getCurrentIndex() == null ? 0 : interview.getCurrentIndex();
        int total = interview.getTotalQuestions() == null || interview.getTotalQuestions() <= 0
                ? fallbackSize : interview.getTotalQuestions();
        if (total <= 0) {
            return "进度未知（题目尚未生成）";
        }
        if (index >= total) {
            return "已完成 " + total + " 题 / 共 " + total + " 题";
        }
        return "已完成 " + index + " 题 / 共 " + total + " 题（当前第 " + (index + 1) + " 题）";
    }

    /**
     * 已答得分摘要，例如「第1题 80 分，第2题 72 分」。
     *
     * @param questions 全部题目
     * @return 得分摘要
     */
    private static String scoreSummary(List<InterviewQuestion> questions) {
        List<String> scores = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            if (question.getScore() != null) {
                scores.add("第" + question.getSeq() + "题 " + scoreText(question.getScore()) + " 分");
            }
        }
        return scores.isEmpty() ? "暂无" : String.join("，", scores);
    }

    /**
     * 分数去尾零展示，例如 80.00 → 80。
     *
     * @param score 分数
     * @return 分数字符串
     */
    private static String scoreText(BigDecimal score) {
        return score == null ? "0" : score.stripTrailingZeros().toPlainString();
    }

    /**
     * 知识标题兜底。
     *
     * @param hit 命中
     * @return 标题
     */
    private static String titleOf(KnowledgeHitVO hit) {
        return TextUtils.defaultIfBlank(hit.title(), "未命名知识");
    }

    /**
     * 实体转调用链视图。
     *
     * @param trace 调用链实体
     * @return 视图
     */
    private static AgentTraceVO toTraceVO(AgentTrace trace) {
        return new AgentTraceVO(trace.getId(), trace.getSessionId(), trace.getScene(), trace.getInput(),
                trace.getOutput(), trace.getToolCalls(), trace.getSuccess(), trace.getLatencyMs(),
                trace.getCreatedAt());
    }

    /**
     * 一次教练对话所需的只读上下文。
     *
     * @param userId          当前用户 id
     * @param interview       练习会话
     * @param currentQuestion 当前待作答题目，可为 {@code null}
     * @param questions       全部题目（按 seq 升序）
     */
    private record CoachContext(Long userId,
                                Interview interview,
                                InterviewQuestion currentQuestion,
                                List<InterviewQuestion> questions) {
    }
}
