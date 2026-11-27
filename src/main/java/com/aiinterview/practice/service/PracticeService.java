package com.aiinterview.practice.service;

import com.aiinterview.ai.AiService;
import com.aiinterview.common.api.PageResult;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.dto.AnswerResultVO;
import com.aiinterview.interview.dto.CurrentQuestionVO;
import com.aiinterview.interview.dto.MessageVO;
import com.aiinterview.interview.dto.ReportVO;
import com.aiinterview.interview.dto.SubmitAnswerRequest;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.entity.InterviewMessage;
import com.aiinterview.interview.entity.InterviewQuestion;
import com.aiinterview.interview.entity.InterviewReport;
import com.aiinterview.interview.enums.InterviewStatus;
import com.aiinterview.interview.enums.MessageRole;
import com.aiinterview.interview.enums.MessageType;
import com.aiinterview.interview.mapper.InterviewMapper;
import com.aiinterview.interview.mapper.InterviewMessageMapper;
import com.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.aiinterview.interview.mapper.InterviewReportMapper;
import com.aiinterview.interview.service.InterviewService;
import com.aiinterview.interview.service.InterviewStateMachine;
import com.aiinterview.interview.service.QuestionGenerator;
import com.aiinterview.interview.service.ReportService;
import com.aiinterview.knowledge.dto.KnowledgeHitVO;
import com.aiinterview.knowledge.service.KnowledgeService;
import com.aiinterview.practice.dto.CreatePracticeRequest;
import com.aiinterview.practice.dto.PracticeAnswerRequest;
import com.aiinterview.practice.dto.PracticeHintVO;
import com.aiinterview.practice.dto.PracticeQuestionReviewVO;
import com.aiinterview.practice.dto.PracticeReviewVO;
import com.aiinterview.practice.dto.PracticeSessionVO;
import com.aiinterview.practice.dto.PracticeSkipResult;
import com.aiinterview.practice.dto.PracticeTargetVO;
import com.aiinterview.practice.entity.PracticeTarget;
import com.aiinterview.practice.mapper.PracticeTargetMapper;
import com.aiinterview.resume.entity.Resume;
import com.aiinterview.resume.service.ResumeService;
import com.aiinterview.security.SecurityUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 面试练习核心服务。
 *
 * <p>设计要点：</p>
 * <ul>
 *     <li><b>复用现有面试引擎</b>：会话仍落在 {@code interview_session}，题目、消息、报告分别复用
 *         原表与 {@link InterviewService}/{@link ReportService}，不另起一套状态机；</li>
 *     <li><b>练习方向借用 position_id</b>：{@code interview_session.position_id} 存放
 *         {@code practice_target.id}，这样无需给会话表加列，也不影响已有的招聘接口；</li>
 *     <li><b>练习语义</b>：没有 HR、岗位发布与录用建议，只给出分数、等级与可执行的下一步建议。</li>
 * </ul>
 */
@Service
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    /** 题数下限 */
    private static final int MIN_QUESTIONS = 3;
    /** 题数上限 */
    private static final int MAX_QUESTIONS = 15;
    /** 练习方向未配置默认题数时的兜底值 */
    private static final int DEFAULT_QUESTIONS = 5;
    /** 分页默认大小 */
    private static final int DEFAULT_PAGE_SIZE = 10;
    /** 分页大小上限 */
    private static final int MAX_PAGE_SIZE = 100;
    /** 练习方向查不到时的兜底名称 */
    private static final String DEFAULT_TARGET_TITLE = "练习方向";
    /** AI 提示不可用时的兜底文案 */
    private static final String FALLBACK_HINT = "先给结论，再从原理、场景、取舍三个角度展开。";
    /** 无任何短板时的通用建议 */
    private static final String DEFAULT_NEXT_STEP = "保持练习节奏，下一次尝试用 STAR 结构完整讲清一个项目经历。";
    /** 出题时知识库参考资料的召回条数 */
    private static final int KNOWLEDGE_CREATE_TOP_K = 5;
    /** 提示时知识库参考资料的召回条数 */
    private static final int KNOWLEDGE_HINT_TOP_K = 3;
    /** 出题上下文里单条参考资料的最大字符数，避免挤占题目生成的 Prompt 预算 */
    private static final int KNOWLEDGE_HIT_MAX_CHARS = 300;
    /** 出题上下文里参考资料的总字符预算（QuestionGenerator 侧还会截断到 2000 字符） */
    private static final int KNOWLEDGE_CONTEXT_MAX_CHARS = 1600;
    /** 提示上下文里单条资料的最大字符数 */
    private static final int KNOWLEDGE_HINT_MAX_CHARS = 240;
    /** 提示上下文的总字符预算 */
    private static final int KNOWLEDGE_HINT_CONTEXT_MAX_CHARS = 900;
    /** 检索兜底提示的最大字符数 */
    private static final int RETRIEVAL_HINT_MAX_CHARS = 80;

    private final PracticeTargetMapper targetMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewReportMapper reportMapper;
    private final QuestionGenerator questionGenerator;
    private final InterviewService interviewService;
    private final ReportService reportService;
    private final ResumeService resumeService;
    private final AiService aiService;
    private final KnowledgeService knowledgeService;

    /**
     * 构造器注入。
     *
     * @param targetMapper       练习方向 Mapper
     * @param interviewMapper    面试会话 Mapper（练习会话复用该表）
     * @param questionMapper     题目 Mapper
     * @param messageMapper      消息 Mapper
     * @param reportMapper       报告 Mapper
     * @param questionGenerator  出题器
     * @param interviewService   面试主流程服务
     * @param reportService      报告服务
     * @param resumeService      简历服务
     * @param aiService          AI 能力门面，用于生成练习提示
     * @param knowledgeService   知识库服务，为出题与提示提供真实检索到的参考资料
     */
    public PracticeService(PracticeTargetMapper targetMapper,
                           InterviewMapper interviewMapper,
                           InterviewQuestionMapper questionMapper,
                           InterviewMessageMapper messageMapper,
                           InterviewReportMapper reportMapper,
                           QuestionGenerator questionGenerator,
                           InterviewService interviewService,
                           ReportService reportService,
                           ResumeService resumeService,
                           AiService aiService,
                           KnowledgeService knowledgeService) {
        this.targetMapper = targetMapper;
        this.interviewMapper = interviewMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.reportMapper = reportMapper;
        this.questionGenerator = questionGenerator;
        this.interviewService = interviewService;
        this.reportService = reportService;
        this.resumeService = resumeService;
        this.aiService = aiService;
        this.knowledgeService = knowledgeService;
    }

    /**
     * 查询全部练习方向，按排序值与 id 升序。
     *
     * @return 练习方向列表
     */
    public List<PracticeTargetVO> targets() {
        return targetMapper.selectList(Wrappers.<PracticeTarget>lambdaQuery()
                        .orderByAsc(PracticeTarget::getSort)
                        .orderByAsc(PracticeTarget::getId))
                .stream()
                .map(PracticeTargetVO::from)
                .toList();
    }

    /**
     * 查询练习方向详情。
     *
     * @param id 方向 id
     * @return 练习方向
     * @throws BizException 方向不存在时抛出 404
     */
    public PracticeTargetVO target(Long id) {
        return PracticeTargetVO.from(requireTarget(id));
    }

    /**
     * 创建一场练习：校验方向与简历 → 生成题目并落库 → 写系统消息。
     *
     * @param request 创建请求
     * @return 新建的练习会话
     */
    @Transactional(rollbackFor = Exception.class)
    public PracticeSessionVO create(CreatePracticeRequest request) {
        Long userId = SecurityUtils.currentUserId();
        PracticeTarget target = requireTarget(request.targetId());

        Resume resume = null;
        if (request.resumeId() != null) {
            resume = resumeService.requireParsedResume(request.resumeId());
            if (!userId.equals(resume.getUserId())) {
                throw BizException.forbidden("无权使用他人的简历");
            }
        }

        int totalQuestions = request.totalQuestions() == null
                ? defaultQuestionCount(target)
                : request.totalQuestions();
        if (totalQuestions < MIN_QUESTIONS || totalQuestions > MAX_QUESTIONS) {
            throw BizException.badRequest("题数必须在 " + MIN_QUESTIONS + "-" + MAX_QUESTIONS + " 之间");
        }

        Interview interview = new Interview();
        interview.setUserId(userId);
        // 复用 position_id 存放练习方向 id：会话表不新增列，招聘接口也不受影响
        interview.setPositionId(target.getId());
        interview.setResumeId(resume == null ? null : resume.getId());
        interview.setMode(request.mode());
        interview.setStatus(InterviewStatus.CREATED);
        interview.setTotalQuestions(totalQuestions);
        interview.setCurrentIndex(0);
        interview.setSwitchCount(0);
        interviewMapper.insert(interview);

        List<InterviewQuestion> questions = questionGenerator.generate(
                interview, target.getTitle(), practiceDescription(target), resume);
        if (questions.size() > totalQuestions) {
            questions = List.copyOf(questions.subList(0, totalQuestions));
        }
        if (questions.isEmpty()) {
            throw new BizException("题目生成失败，请稍后重试");
        }
        if (questions.size() < totalQuestions) {
            // 极端降级场景下题数不足，以实际生成题数为准，避免流程卡死
            interview.setTotalQuestions(questions.size());
            interviewMapper.updateById(interview);
            log.warn("练习生成题数少于预期 interviewId={} expected={} actual={}",
                    interview.getId(), totalQuestions, questions.size());
        }
        for (InterviewQuestion question : questions) {
            question.setInterviewId(interview.getId());
            questionMapper.insert(question);
        }

        writeMessage(interview.getId(), MessageRole.SYSTEM, MessageType.SYSTEM,
                "练习已创建，共 " + questions.size() + " 道题");
        return toSessionVO(interview, target.getTitle(), target.getSubtitle(), null);
    }

    /**
     * 分页查询当前用户的练习记录，按创建时间倒序。
     *
     * @param page   页码，从 1 开始，默认 1
     * @param size   每页大小，默认 10
     * @param status 练习状态（枚举名），可选；非法值按「不筛选」处理
     * @return 分页结果
     */
    public PageResult<PracticeSessionVO> mySessions(Integer page, Integer size, String status) {
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        InterviewStatus statusEnum = parseStatus(status);

        Page<Interview> result = interviewMapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<Interview>lambdaQuery()
                        .eq(Interview::getUserId, SecurityUtils.currentUserId())
                        .eq(statusEnum != null, Interview::getStatus, statusEnum)
                        .orderByDesc(Interview::getCreatedAt)
                        .orderByDesc(Interview::getId));

        List<Interview> interviews = result.getRecords();
        Map<Long, Double> scores = scoresByInterview(interviews);
        Map<Long, PracticeTarget> targets = targetsByIds(interviews);
        List<PracticeSessionVO> records = interviews.stream()
                .map(interview -> {
                    PracticeTarget target = targets.get(interview.getPositionId());
                    return toSessionVO(interview,
                            target == null ? DEFAULT_TARGET_TITLE : target.getTitle(),
                            target == null ? null : target.getSubtitle(),
                            scores.get(interview.getId()));
                })
                .toList();
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /**
     * 查询练习详情（仅本人或管理员）。
     *
     * @param id 练习 id
     * @return 练习会话
     */
    public PracticeSessionVO session(Long id) {
        Interview interview = requireOwned(id);
        PracticeTarget target = findTarget(interview.getPositionId());
        return toSessionVO(interview,
                target == null ? DEFAULT_TARGET_TITLE : target.getTitle(),
                target == null ? null : target.getSubtitle(),
                findScore(interview.getId()));
    }

    /**
     * 开始练习并返回第一题。
     *
     * @param id 练习 id
     * @return 第一题
     */
    public CurrentQuestionVO start(Long id) {
        requireOwned(id);
        return interviewService.start(id);
    }

    /**
     * 查询当前待作答的题目。
     *
     * @param id 练习 id
     * @return 当前题目
     */
    public CurrentQuestionVO current(Long id) {
        return interviewService.currentQuestion(id, SecurityUtils.currentUserId());
    }

    /**
     * 提交单题答案，复用面试引擎的评分与推进逻辑。
     *
     * <p>{@link PracticeAnswerRequest#audioPath()} 当前仅做预留：评分只使用文本答案，
     * 面试引擎也不落库语音文件。</p>
     *
     * @param id      练习 id
     * @param request 作答请求
     * @return 单题结果
     */
    public AnswerResultVO answer(Long id, PracticeAnswerRequest request) {
        SubmitAnswerRequest submit = new SubmitAnswerRequest(
                request.questionId(), request.answer(), request.thinkingSeconds());
        return interviewService.submitAnswer(id, SecurityUtils.currentUserId(), submit);
    }

    /**
     * 获取当前题的一句话提示：先检索知识库，再让 AI 基于真实资料给方向，不写库、不计分。
     *
     * <p>降级链：知识库检索失败 → 忽略参考资料继续调用 AI；AI 不可用 → 返回基于检索结果的提示
     * （取第一条命中的前 80 字）；两者都不可用 → 返回通用提示，保证练习流程不中断。</p>
     *
     * @param id 练习 id
     * @return 提示
     */
    public PracticeHintVO hint(Long id) {
        Interview interview = requireOwned(id);
        int index = interview.getCurrentIndex() == null ? 0 : interview.getCurrentIndex();
        InterviewQuestion question = questionMapper.selectOne(Wrappers.<InterviewQuestion>lambdaQuery()
                .eq(InterviewQuestion::getInterviewId, id)
                .eq(InterviewQuestion::getSeq, index + 1)
                .last("LIMIT 1"));
        if (question == null) {
            throw BizException.notFound("题目不存在");
        }
        String mode = interview.getMode() == null ? null : interview.getMode().name();
        List<KnowledgeHitVO> references = searchReferences(question.getContent(), KNOWLEDGE_HINT_TOP_K, id);
        String hint;
        try {
            hint = aiService.hint(question.getContent(), question.getReferenceAnswer(), mode,
                    hintContext(references));
        } catch (BizException e) {
            log.warn("AI 提示生成失败，返回检索提示 interviewId={} reason={}", id, e.getMessage());
            hint = retrievalHint(references);
        }
        return new PracticeHintVO(TextUtils.isBlank(hint) ? retrievalHint(references) : hint.trim());
    }

    /**
     * 跳过当前题。
     *
     * <p>跳过的题目保留 {@code score = null}，报告均分逻辑会忽略未评分题目；
     * 若跳过的是最后一题，则直接结束练习并生成报告。</p>
     *
     * @param id 练习 id
     * @return 跳过结果，含下一题或报告 id
     */
    @Transactional(rollbackFor = Exception.class)
    public PracticeSkipResult skip(Long id) {
        Interview interview = requireOwned(id);
        InterviewStateMachine.assertInProgress(interview);
        int index = interview.getCurrentIndex() == null ? 0 : interview.getCurrentIndex();
        InterviewQuestion question = questionMapper.selectOne(Wrappers.<InterviewQuestion>lambdaQuery()
                .eq(InterviewQuestion::getInterviewId, id)
                .eq(InterviewQuestion::getSeq, index + 1)
                .last("LIMIT 1"));
        if (question == null) {
            throw BizException.notFound("题目不存在");
        }
        question.setAnswer("（已跳过）");
        question.setAnswerAt(LocalDateTime.now());
        question.setThinkingSeconds(0);
        question.setComment("本题已跳过");
        questionMapper.updateById(question);
        writeMessage(id, MessageRole.CANDIDATE, MessageType.ANSWER, "（已跳过本题）");
        writeMessage(id, MessageRole.AI, MessageType.SCORE, "本题已跳过");

        int updated = interviewMapper.update(null, Wrappers.<Interview>lambdaUpdate()
                .eq(Interview::getId, id)
                .eq(Interview::getCurrentIndex, index)
                .set(Interview::getCurrentIndex, index + 1));
        if (updated == 0) {
            throw BizException.conflict("请勿重复提交");
        }
        int totalQuestions = interview.getTotalQuestions() == null ? 0 : interview.getTotalQuestions();
        if (index + 1 >= totalQuestions) {
            ReportVO report = interviewService.finish(id, SecurityUtils.currentUserId());
            return new PracticeSkipResult(true, null, report.id());
        }
        return new PracticeSkipResult(false,
                interviewService.currentQuestion(id, SecurityUtils.currentUserId()), null);
    }

    /**
     * 结束练习并生成报告。
     *
     * @param id 练习 id
     * @return 练习报告
     */
    public ReportVO finish(Long id) {
        return interviewService.finish(id, SecurityUtils.currentUserId());
    }

    /**
     * 查询练习复盘：练习必须已结束。
     *
     * <p>与招聘报告不同，这里不返回录用建议，而是给出等级、优势短板、单题参考答案
     * 与最多 4 条可执行的下一步建议。</p>
     *
     * @param id 练习 id
     * @return 练习复盘
     */
    public PracticeReviewVO review(Long id) {
        Interview interview = requireOwned(id);
        if (interview.getStatus() != InterviewStatus.FINISHED) {
            throw BizException.conflict("练习尚未结束");
        }
        ReportVO report = reportService.getByInterviewId(id);
        List<PracticeQuestionReviewVO> questions = questionMapper.selectList(Wrappers.<InterviewQuestion>lambdaQuery()
                        .eq(InterviewQuestion::getInterviewId, id)
                        .orderByAsc(InterviewQuestion::getSeq))
                .stream()
                .map(PracticeQuestionReviewVO::from)
                .toList();

        double overallScore = report.overallScore() == null ? 0.0 : report.overallScore();
        Map<String, Double> dimensionScores = report.dimensionScores() == null
                ? Map.of() : report.dimensionScores();
        String level = levelOf(overallScore);
        List<String> nextSteps = buildNextSteps(report.weaknesses(), dimensionScores, overallScore);
        PracticeTarget target = findTarget(interview.getPositionId());
        PracticeSessionVO session = toSessionVO(interview,
                target == null ? DEFAULT_TARGET_TITLE : target.getTitle(),
                target == null ? null : target.getSubtitle(),
                overallScore);
        return new PracticeReviewVO(
                session,
                overallScore,
                dimensionScores,
                level,
                levelLabelOf(level),
                report.strengths() == null ? List.of() : report.strengths(),
                report.weaknesses() == null ? List.of() : report.weaknesses(),
                report.summary(),
                questions,
                nextSteps);
    }

    /**
     * 查询练习对话消息（按时间升序）。
     *
     * @param id 练习 id
     * @return 消息列表
     */
    public List<MessageVO> messages(Long id) {
        return interviewService.messages(id);
    }

    /**
     * 取消练习。
     *
     * @param id 练习 id
     */
    public void cancel(Long id) {
        interviewService.cancel(id);
    }

    /**
     * 加载练习并校验归属：本人或管理员可访问。
     *
     * @param id 练习 id
     * @return 练习会话实体
     * @throws BizException 练习不存在（404）或无权访问（403）
     */
    private Interview requireOwned(Long id) {
        if (id == null) {
            throw BizException.badRequest("练习 id 不能为空");
        }
        Interview interview = interviewMapper.selectById(id);
        if (interview == null) {
            throw BizException.notFound("练习不存在");
        }
        Long userId = SecurityUtils.currentUserId();
        if (!SecurityUtils.isAdmin() && !userId.equals(interview.getUserId())) {
            throw BizException.forbidden("无权访问该练习");
        }
        return interview;
    }

    /**
     * 按 id 取练习方向，不存在抛 404。
     *
     * @param id 方向 id
     * @return 练习方向实体
     */
    private PracticeTarget requireTarget(Long id) {
        if (id == null) {
            throw BizException.badRequest("练习方向 id 不能为空");
        }
        PracticeTarget target = targetMapper.selectById(id);
        if (target == null) {
            throw BizException.notFound("练习方向不存在");
        }
        return target;
    }

    /**
     * 查询练习方向，查不到返回 {@code null}（列表展示兜底用）。
     *
     * @param targetId 方向 id
     * @return 练习方向实体或 {@code null}
     */
    private PracticeTarget findTarget(Long targetId) {
        return targetId == null ? null : targetMapper.selectById(targetId);
    }

    /**
     * 组装出题上下文：方向描述 + 重点考察方向。
     *
     * @param target 练习方向
     * @return 出题描述文本
     */
    private String targetDescription(PracticeTarget target) {
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isBlank(target.getDescription())) {
            builder.append(target.getDescription().trim());
        }
        List<String> topics = TextUtils.splitCsv(target.getFocusTopics());
        if (!topics.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("重点考察：").append(String.join("、", topics));
        }
        return builder.toString();
    }

    /**
     * 组装出题上下文：方向描述 + 重点考察方向 + 知识库参考资料。
     *
     * <p>参考资料来自真实检索：查询词由「方向标题 + 重点考察方向 + 常见面试题 答题要点」组成。
     * 检索失败或没有命中时退化为原始描述，不影响练习创建。</p>
     *
     * @param target 练习方向
     * @return 出题描述文本
     */
    private String practiceDescription(PracticeTarget target) {
        String description = targetDescription(target);
        String reference = knowledgeReference(target);
        if (TextUtils.isBlank(reference)) {
            return description;
        }
        return description + "\n\n参考资料（来自知识库，可用于出题）：\n" + reference;
    }

    /**
     * 检索知识库并拼装成出题用的参考片段。
     *
     * <p>单条命中最多保留 {@value #KNOWLEDGE_HIT_MAX_CHARS} 字，总量控制在
     * {@value #KNOWLEDGE_CONTEXT_MAX_CHARS} 字以内：QuestionGenerator 侧还会把描述截断到
     * 2000 字，这里提前控制可以避免参考资料挤掉方向描述。</p>
     *
     * @param target 练习方向
     * @return 拼接好的参考资料，没有命中时返回空串
     */
    private String knowledgeReference(PracticeTarget target) {
        String query = target.getTitle() + " "
                + String.join(" ", TextUtils.splitCsv(target.getFocusTopics()))
                + " 常见面试题 答题要点";
        List<KnowledgeHitVO> hits = searchReferences(query, KNOWLEDGE_CREATE_TOP_K, null);
        StringBuilder builder = new StringBuilder();
        for (KnowledgeHitVO hit : hits) {
            if (builder.length() >= KNOWLEDGE_CONTEXT_MAX_CHARS) {
                break;
            }
            String content = hit.content() == null ? "" : hit.content().replaceAll("\\s+", " ").trim();
            if (content.isEmpty()) {
                continue;
            }
            builder.append("- [")
                    .append(TextUtils.defaultIfBlank(hit.title(), "知识库"))
                    .append("] ")
                    .append(TextUtils.truncate(content, KNOWLEDGE_HIT_MAX_CHARS))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    /**
     * 安全检索知识库：任何异常都降级为空结果，不影响练习主流程。
     *
     * @param query       查询文本
     * @param topK        返回条数
     * @param interviewId 练习 id，仅用于日志定位，可为空
     * @return 命中列表，失败时为空列表
     */
    private List<KnowledgeHitVO> searchReferences(String query, int topK, Long interviewId) {
        if (TextUtils.isBlank(query)) {
            return List.of();
        }
        try {
            return knowledgeService.search(query, topK);
        } catch (RuntimeException e) {
            log.warn("知识库检索失败，忽略参考资料 interviewId={} reason={}", interviewId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 把检索结果拼成提示用的参考资料片段。
     *
     * @param references 检索命中
     * @return 拼接文本，没有命中时返回空串
     */
    private static String hintContext(List<KnowledgeHitVO> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (KnowledgeHitVO reference : references) {
            if (builder.length() >= KNOWLEDGE_HINT_CONTEXT_MAX_CHARS) {
                break;
            }
            if (TextUtils.isBlank(reference.content())) {
                continue;
            }
            String content = reference.content().replaceAll("\\s+", " ").trim();
            if (content.isEmpty()) {
                continue;
            }
            builder.append("- [")
                    .append(TextUtils.defaultIfBlank(reference.title(), "知识库"))
                    .append("] ")
                    .append(TextUtils.truncate(content, KNOWLEDGE_HINT_MAX_CHARS))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    /**
     * AI 不可用时，用检索结果的第一条命中前 80 字作为提示。
     *
     * @param references 检索命中
     * @return 提示文本，没有命中时返回通用提示
     */
    private static String retrievalHint(List<KnowledgeHitVO> references) {
        if (references == null || references.isEmpty()) {
            return FALLBACK_HINT;
        }
        for (KnowledgeHitVO reference : references) {
            if (TextUtils.isBlank(reference.content())) {
                continue;
            }
            String content = reference.content().replaceAll("\\s+", " ").trim();
            if (content.isEmpty()) {
                continue;
            }
            return content.length() <= RETRIEVAL_HINT_MAX_CHARS
                    ? content
                    : content.substring(0, RETRIEVAL_HINT_MAX_CHARS) + "…";
        }
        return FALLBACK_HINT;
    }

    /**
     * 计算默认题数：取方向配置并夹到 3-15 之间。
     *
     * @param target 练习方向
     * @return 默认题数
     */
    private static int defaultQuestionCount(PracticeTarget target) {
        Integer configured = target.getDefaultQuestions();
        if (configured == null) {
            return DEFAULT_QUESTIONS;
        }
        return Math.max(MIN_QUESTIONS, Math.min(MAX_QUESTIONS, configured));
    }

    /**
     * 解析状态参数：空值或非法枚举名统一返回 {@code null}（表示不筛选）。
     *
     * @param status 状态字符串
     * @return 状态枚举或 {@code null}
     */
    private static InterviewStatus parseStatus(String status) {
        if (TextUtils.isBlank(status)) {
            return null;
        }
        try {
            return InterviewStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 批量查询练习的综合得分，未生成报告的不放入 Map。
     *
     * @param interviews 练习会话列表
     * @return 练习 id → 综合得分
     */
    private Map<Long, Double> scoresByInterview(List<Interview> interviews) {
        List<Long> ids = interviews.stream()
                .map(Interview::getId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> scores = new HashMap<>();
        for (InterviewReport report : reportMapper.selectList(Wrappers.<InterviewReport>lambdaQuery()
                .in(InterviewReport::getInterviewId, ids))) {
            if (report.getOverallScore() != null) {
                scores.put(report.getInterviewId(), report.getOverallScore().doubleValue());
            }
        }
        return scores;
    }

    /**
     * 查询单场练习的综合得分。
     *
     * @param interviewId 练习 id
     * @return 综合得分，未生成报告时返回 {@code null}
     */
    private Double findScore(Long interviewId) {
        InterviewReport report = reportMapper.selectOne(Wrappers.<InterviewReport>lambdaQuery()
                .eq(InterviewReport::getInterviewId, interviewId));
        return report == null || report.getOverallScore() == null
                ? null : report.getOverallScore().doubleValue();
    }

    /**
     * 批量查询练习方向。
     *
     * @param interviews 练习会话列表
     * @return 方向 id → 练习方向
     */
    private Map<Long, PracticeTarget> targetsByIds(List<Interview> interviews) {
        List<Long> ids = interviews.stream()
                .map(Interview::getPositionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, PracticeTarget> targets = new HashMap<>();
        for (PracticeTarget target : targetMapper.selectList(Wrappers.<PracticeTarget>lambdaQuery()
                .in(PracticeTarget::getId, ids))) {
            targets.put(target.getId(), target);
        }
        return targets;
    }

    /**
     * 由会话实体组装练习视图。
     *
     * @param interview      会话实体
     * @param targetTitle    方向名称
     * @param targetSubtitle 方向副标题
     * @param score          综合得分，可为空
     * @return 练习视图
     */
    private static PracticeSessionVO toSessionVO(Interview interview, String targetTitle,
                                                 String targetSubtitle, Double score) {
        return new PracticeSessionVO(
                interview.getId(),
                interview.getPositionId(),
                TextUtils.defaultIfBlank(targetTitle, DEFAULT_TARGET_TITLE),
                targetSubtitle,
                interview.getMode() == null ? null : interview.getMode().name(),
                interview.getStatus() == null ? null : interview.getStatus().name(),
                interview.getTotalQuestions(),
                interview.getCurrentIndex(),
                interview.getCurrentIndex(),
                score,
                interview.getCreatedAt(),
                interview.getFinishedAt(),
                interview.getDurationSeconds());
    }

    /**
     * 生成下一步练习建议：短板前 2 条 + 最低维度 + 低分补充，去重后最多 4 条。
     *
     * @param weaknesses      报告中的待改进点
     * @param dimensionScores 维度均分
     * @param overallScore    综合得分
     * @return 建议列表，至少 1 条
     */
    private static List<String> buildNextSteps(List<String> weaknesses,
                                               Map<String, Double> dimensionScores,
                                               double overallScore) {
        Set<String> steps = new LinkedHashSet<>();
        if (weaknesses != null) {
            weaknesses.stream()
                    .filter(weakness -> !TextUtils.isBlank(weakness))
                    .limit(2)
                    .forEach(weakness -> steps.add("重点改进：" + weakness.trim()));
        }
        if (dimensionScores != null && !dimensionScores.isEmpty()) {
            Map.Entry<String, Double> lowest = dimensionScores.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                    .min(Map.Entry.comparingByValue())
                    .orElse(null);
            if (lowest != null) {
                steps.add("加强" + dimensionLabel(lowest.getKey())
                        + "：复述答案时先给结论，再补 2 个论据");
            }
        }
        if (overallScore < 70) {
            steps.add("建议先阅读《面试指南》的答题结构，再用同方向练习一次");
        }
        if (steps.isEmpty()) {
            steps.add(DEFAULT_NEXT_STEP);
        }
        return steps.stream().limit(4).toList();
    }

    /**
     * 维度英文名转中文。
     *
     * @param dimension 维度名
     * @return 中文维度名，未知维度原样返回
     */
    private static String dimensionLabel(String dimension) {
        return switch (dimension) {
            case "accuracy" -> "准确性";
            case "depth" -> "深度";
            case "expression" -> "表达";
            case "structure" -> "结构";
            default -> dimension;
        };
    }

    /**
     * 根据综合得分映射等级编码。
     *
     * @param score 综合得分
     * @return 等级编码
     */
    private static String levelOf(double score) {
        if (score >= 85) {
            return "EXCELLENT";
        }
        if (score >= 70) {
            return "GOOD";
        }
        if (score >= 55) {
            return "IMPROVING";
        }
        return "NEEDS_WORK";
    }

    /**
     * 等级编码转中文名。
     *
     * @param level 等级编码
     * @return 等级中文名
     */
    private static String levelLabelOf(String level) {
        return switch (level) {
            case "EXCELLENT" -> "优秀";
            case "GOOD" -> "良好";
            case "IMPROVING" -> "待提升";
            default -> "需加强";
        };
    }

    /**
     * 写入一条练习对话消息。
     *
     * @param interviewId 练习 id
     * @param role        发送方
     * @param type        消息类型
     * @param content     消息内容
     */
    private void writeMessage(Long interviewId, MessageRole role, MessageType type, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setInterviewId(interviewId);
        message.setRole(role);
        message.setType(type);
        message.setContent(content);
        messageMapper.insert(message);
    }
}
