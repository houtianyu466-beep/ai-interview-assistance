package com.aiinterview.interview.service;

import com.aiinterview.ai.model.AnswerEvaluation;
import com.aiinterview.common.api.PageResult;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.dto.AnswerResultVO;
import com.aiinterview.interview.dto.CreateInterviewRequest;
import com.aiinterview.interview.dto.CurrentQuestionVO;
import com.aiinterview.interview.dto.InterviewDetailVO;
import com.aiinterview.interview.dto.InterviewQuery;
import com.aiinterview.interview.dto.InterviewVO;
import com.aiinterview.interview.dto.MessageVO;
import com.aiinterview.interview.dto.QuestionBriefVO;
import com.aiinterview.interview.dto.ReportVO;
import com.aiinterview.interview.dto.SubmitAnswerRequest;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.entity.InterviewMessage;
import com.aiinterview.interview.entity.InterviewQuestion;
import com.aiinterview.interview.enums.InterviewStatus;
import com.aiinterview.interview.enums.MessageRole;
import com.aiinterview.interview.enums.MessageType;
import com.aiinterview.interview.mapper.InterviewMapper;
import com.aiinterview.interview.mapper.InterviewMessageMapper;
import com.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.aiinterview.job.entity.JobPosition;
import com.aiinterview.job.mapper.JobPositionMapper;
import com.aiinterview.job.service.JobService;
import com.aiinterview.resume.entity.Resume;
import com.aiinterview.resume.service.ResumeService;
import com.aiinterview.security.LoginUser;
import com.aiinterview.security.SecurityUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 面试主流程服务：创建、开始、答题、结束、取消、报告与回放。
 *
 * <p>归属校验统一走 {@link #requireOwnedInterview(Long, Long)}，管理员可放行；
 * 状态流转统一委托 {@link InterviewStateMachine}，避免散落的 setStatus。</p>
 */
@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    /** 题数下限 */
    private static final int MIN_QUESTIONS = 3;
    /** 题数上限 */
    private static final int MAX_QUESTIONS = 15;
    /** 未指定题数时的默认值 */
    private static final int DEFAULT_QUESTIONS = 5;

    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final JobPositionMapper jobPositionMapper;
    private final QuestionGenerator questionGenerator;
    private final AnswerScoringService answerScoringService;
    private final ReportService reportService;
    private final JobService jobService;
    private final ResumeService resumeService;

    /**
     * 构造器注入。
     *
     * @param interviewMapper      面试 Mapper
     * @param questionMapper       题目 Mapper
     * @param messageMapper        消息 Mapper
     * @param jobPositionMapper    岗位 Mapper，用于补充岗位标题
     * @param questionGenerator    出题器
     * @param answerScoringService 评分服务
     * @param reportService        报告服务
     * @param jobService           岗位服务，用于校验岗位开放状态
     * @param resumeService        简历服务，用于校验简历已解析
     */
    public InterviewService(InterviewMapper interviewMapper,
                            InterviewQuestionMapper questionMapper,
                            InterviewMessageMapper messageMapper,
                            JobPositionMapper jobPositionMapper,
                            QuestionGenerator questionGenerator,
                            AnswerScoringService answerScoringService,
                            ReportService reportService,
                            JobService jobService,
                            ResumeService resumeService) {
        this.interviewMapper = interviewMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.jobPositionMapper = jobPositionMapper;
        this.questionGenerator = questionGenerator;
        this.answerScoringService = answerScoringService;
        this.reportService = reportService;
        this.jobService = jobService;
        this.resumeService = resumeService;
    }

    /**
     * 创建面试：校验岗位与简历 → 生成题目并逐条落库 → 写入系统消息。
     *
     * @param request 创建请求
     * @return 新建的面试视图
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewVO create(CreateInterviewRequest request) {
        Long userId = SecurityUtils.currentUserId();
        JobPosition position = jobService.requireOpenJob(request.positionId());

        Resume resume = null;
        if (request.resumeId() != null) {
            resume = resumeService.requireParsedResume(request.resumeId());
            if (!userId.equals(resume.getUserId())) {
                throw BizException.forbidden("无权使用他人的简历");
            }
        }

        int totalQuestions = request.totalQuestions() == null ? DEFAULT_QUESTIONS : request.totalQuestions();
        if (totalQuestions < MIN_QUESTIONS || totalQuestions > MAX_QUESTIONS) {
            throw BizException.badRequest("题数必须在 " + MIN_QUESTIONS + "-" + MAX_QUESTIONS + " 之间");
        }

        Interview interview = new Interview();
        interview.setUserId(userId);
        interview.setPositionId(position.getId());
        interview.setResumeId(resume == null ? null : resume.getId());
        interview.setMode(request.mode());
        interview.setStatus(InterviewStatus.CREATED);
        interview.setTotalQuestions(totalQuestions);
        interview.setCurrentIndex(0);
        interview.setSwitchCount(0);
        interviewMapper.insert(interview);

        List<InterviewQuestion> questions = questionGenerator.generate(interview, position, resume);
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
            log.warn("生成题数少于预期 interviewId={} expected={} actual={}",
                    interview.getId(), totalQuestions, questions.size());
        }
        for (InterviewQuestion question : questions) {
            question.setInterviewId(interview.getId());
            questionMapper.insert(question);
        }

        writeMessage(interview.getId(), MessageRole.SYSTEM, MessageType.SYSTEM,
                "面试已创建，共 " + questions.size() + " 道题");
        return InterviewVO.from(interview, position.getTitle());
    }

    /**
     * 查询我的面试分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public PageResult<InterviewVO> myInterviews(InterviewQuery query) {
        return page(SecurityUtils.currentUserId(), query);
    }

    /**
     * 查询全部面试分页，仅管理员与面试官可用。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public PageResult<InterviewVO> allInterviews(InterviewQuery query) {
        LoginUser loginUser = SecurityUtils.currentUser();
        if (!loginUser.isAdmin() && !loginUser.isInterviewer()) {
            throw BizException.forbidden("无权查看全部面试");
        }
        return page(null, query);
    }

    /**
     * 查询面试详情（含题目概要）。
     *
     * @param interviewId 面试 id
     * @return 面试详情
     */
    public InterviewDetailVO detail(Long interviewId) {
        Interview interview = requireReadableInterview(interviewId);
        List<QuestionBriefVO> questions = listQuestions(interviewId).stream()
                .map(QuestionBriefVO::from)
                .toList();
        return new InterviewDetailVO(toVO(interview), questions);
    }

    /**
     * 开始面试：状态流转到 IN_PROGRESS，写入 AI 提问消息并返回第一题。
     *
     * <p>WebSocket 通道在握手阶段已完成归属校验，因此本方法不再依赖 SecurityContext。</p>
     *
     * @param interviewId 面试 id
     * @return 第一题
     */
    @Transactional(rollbackFor = Exception.class)
    public CurrentQuestionVO start(Long interviewId) {
        Interview interview = requireInterview(interviewId);
        InterviewStateMachine.assertCanStart(interview);
        InterviewStateMachine.markStarted(interview);
        interviewMapper.updateById(interview);

        InterviewQuestion first = findQuestion(interviewId, 1);
        if (first == null) {
            throw BizException.notFound("面试题目不存在");
        }
        writeMessage(interviewId, MessageRole.AI, MessageType.QUESTION, first.getContent());
        return CurrentQuestionVO.from(first, interview.getTotalQuestions());
    }

    /**
     * 获取当前待作答的题目。
     *
     * @param interviewId 面试 id
     * @param userId      当前用户 id
     * @return 当前题目
     */
    public CurrentQuestionVO currentQuestion(Long interviewId, Long userId) {
        Interview interview = requireOwnedInterview(interviewId, userId);
        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            throw BizException.conflict("面试已取消，无法获取题目");
        }
        if (interview.getStatus() == InterviewStatus.FINISHED) {
            throw BizException.conflict("面试已结束，无法获取题目");
        }
        int currentIndex = interview.getCurrentIndex() == null ? 0 : interview.getCurrentIndex();
        int totalQuestions = interview.getTotalQuestions() == null ? 0 : interview.getTotalQuestions();
        int nextSeq = currentIndex + 1;
        if (totalQuestions > 0 && nextSeq > totalQuestions) {
            throw BizException.conflict("面试题目已全部作答");
        }
        InterviewQuestion question = findQuestion(interviewId, nextSeq);
        if (question == null) {
            throw BizException.notFound("题目不存在");
        }
        return CurrentQuestionVO.from(question, totalQuestions);
    }

    /**
     * 提交单题答案：评分 → 更新题目 → 写消息 → 乐观锁推进 → 必要时结束并生成报告。
     *
     * @param interviewId 面试 id
     * @param userId      当前用户 id
     * @param request     提交请求
     * @return 单题结果，含下一题或报告 id
     */
    @Transactional(rollbackFor = Exception.class)
    public AnswerResultVO submitAnswer(Long interviewId, Long userId, SubmitAnswerRequest request) {
        Interview interview = requireOwnedInterview(interviewId, userId);
        InterviewStateMachine.assertInProgress(interview);

        InterviewQuestion question = questionMapper.selectOne(Wrappers.<InterviewQuestion>lambdaQuery()
                .eq(InterviewQuestion::getId, request.questionId())
                .eq(InterviewQuestion::getInterviewId, interviewId)
                .last("LIMIT 1"));
        if (question == null) {
            throw BizException.notFound("题目不存在");
        }
        int currentIndex = interview.getCurrentIndex() == null ? 0 : interview.getCurrentIndex();
        if (question.getSeq() == null || question.getSeq() != currentIndex + 1) {
            throw BizException.conflict("当前不是该题，请刷新后重试");
        }
        if (question.getAnswer() != null) {
            throw BizException.conflict("该题已作答");
        }

        int thinkingSeconds = request.thinkingSeconds() == null ? 0 : Math.max(0, request.thinkingSeconds());
        AnswerEvaluation evaluation = answerScoringService.evaluate(interview, question, request.answer(), thinkingSeconds);
        BigDecimal score = BigDecimal.valueOf(round1(evaluation.score()));

        question.setAnswer(request.answer());
        question.setAnswerAt(LocalDateTime.now());
        question.setThinkingSeconds(thinkingSeconds);
        question.setScore(score);
        question.setDimensionScores(JsonUtils.toJson(evaluation.dimensions()));
        question.setComment(evaluation.comment());
        questionMapper.updateById(question);

        writeMessage(interviewId, MessageRole.CANDIDATE, MessageType.ANSWER, request.answer());
        writeMessage(interviewId, MessageRole.AI, MessageType.SCORE, "本题得分：" + formatNumber(score.doubleValue()));

        int rows = interviewMapper.update(null, Wrappers.<Interview>lambdaUpdate()
                .eq(Interview::getId, interviewId)
                .eq(Interview::getCurrentIndex, currentIndex)
                .set(Interview::getCurrentIndex, currentIndex + 1));
        if (rows == 0) {
            throw BizException.conflict("请勿重复提交");
        }
        interview.setCurrentIndex(currentIndex + 1);

        int totalQuestions = interview.getTotalQuestions() == null ? 0 : interview.getTotalQuestions();
        if (currentIndex + 1 >= totalQuestions) {
            InterviewStateMachine.markFinished(interview);
            interviewMapper.updateById(interview);
            ReportVO report = reportService.generate(interview, listQuestions(interviewId));
            return new AnswerResultVO(question.getId(), score.doubleValue(), evaluation.dimensions(),
                    evaluation.comment(), evaluation.hits(), evaluation.misses(), true, null, report.id());
        }

        InterviewQuestion next = findQuestion(interviewId, currentIndex + 2);
        if (next == null) {
            throw BizException.notFound("下一题不存在");
        }
        return new AnswerResultVO(question.getId(), score.doubleValue(), evaluation.dimensions(),
                evaluation.comment(), evaluation.hits(), evaluation.misses(), false,
                CurrentQuestionVO.from(next, totalQuestions), null);
    }

    /**
     * 结束面试并生成报告；已结束的面试直接返回已有报告。
     *
     * @param interviewId 面试 id
     * @param userId      当前用户 id
     * @return 面试报告
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportVO finish(Long interviewId, Long userId) {
        Interview interview = requireOwnedInterview(interviewId, userId);
        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            throw BizException.conflict("面试已取消，无法结束");
        }
        if (InterviewStateMachine.isFinished(interview)) {
            return reportService.getByInterviewId(interviewId);
        }
        InterviewStateMachine.markFinished(interview);
        interviewMapper.updateById(interview);
        return reportService.generate(interview, listQuestions(interviewId));
    }

    /**
     * 取消面试：已结束抛冲突，已取消直接返回。
     *
     * @param interviewId 面试 id
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long interviewId) {
        Interview interview = requireOwnedInterview(interviewId, SecurityUtils.currentUserId());
        if (InterviewStateMachine.isFinished(interview)) {
            throw BizException.conflict("面试已结束，无法取消");
        }
        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            return;
        }
        InterviewStateMachine.markCancelled(interview);
        interviewMapper.updateById(interview);
    }

    /**
     * 查询面试报告。
     *
     * @param interviewId 面试 id
     * @return 面试报告
     */
    public ReportVO report(Long interviewId) {
        requireReadableInterview(interviewId);
        return reportService.getByInterviewId(interviewId);
    }

    /**
     * 重新生成面试报告，仅已结束的面试允许。
     *
     * @param interviewId 面试 id
     * @return 新的报告
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportVO regenerateReport(Long interviewId) {
        Interview interview = requireInterview(interviewId);
        if (!SecurityUtils.isAdmin() && !SecurityUtils.isInterviewer()) {
            throw BizException.forbidden("仅面试官或管理员可以重新生成报告");
        }
        if (!InterviewStateMachine.isFinished(interview)) {
            throw BizException.conflict("仅已结束的面试可以重新生成报告");
        }
        return reportService.regenerate(interviewId);
    }

    /**
     * 查询面试对话消息（按时间升序）。
     *
     * @param interviewId 面试 id
     * @return 消息列表
     */
    public List<MessageVO> messages(Long interviewId) {
        requireReadableInterview(interviewId);
        return messageMapper.selectList(Wrappers.<InterviewMessage>lambdaQuery()
                        .eq(InterviewMessage::getInterviewId, interviewId)
                        .orderByAsc(InterviewMessage::getCreatedAt)
                        .orderByAsc(InterviewMessage::getId))
                .stream()
                .map(MessageVO::from)
                .toList();
    }

    /**
     * 记录一次切屏（前端上报，用于反作弊提示）。
     *
     * @param interviewId 面试 id
     * @param userId      当前用户 id
     */
    @Transactional(rollbackFor = Exception.class)
    public void reportSwitch(Long interviewId, Long userId) {
        requireOwnedInterview(interviewId, userId);
        interviewMapper.update(null, Wrappers.<Interview>lambdaUpdate()
                .eq(Interview::getId, interviewId)
                .setSql("switch_count = switch_count + 1"));
    }

    /**
     * 加载面试并校验归属：本人或管理员可访问。
     *
     * @param interviewId 面试 id
     * @param userId      当前用户 id
     * @return 面试实体
     * @throws BizException 面试不存在（404）或无权访问（403）
     */
    public Interview requireOwnedInterview(Long interviewId, Long userId) {
        Interview interview = requireInterview(interviewId);
        boolean admin = SecurityUtils.isAdmin();
        if (!admin && (userId == null || !userId.equals(interview.getUserId()))) {
            throw BizException.forbidden("无权访问该面试");
        }
        return interview;
    }

    /**
     * 加载面试并校验「只读权限」：本人、面试官或管理员可访问。
     *
     * <p>只用于详情、报告、对话回放等只读场景；作答/结束/取消等写操作仍走
     * {@link #requireOwnedInterview(Long, Long)}，避免面试官误改候选人数据。</p>
     *
     * @param interviewId 面试 id
     * @return 面试实体
     * @throws BizException 面试不存在（404）或无权访问（403）
     */
    public Interview requireReadableInterview(Long interviewId) {
        Interview interview = requireInterview(interviewId);
        LoginUser current = SecurityUtils.currentUser();
        boolean privileged = current.isAdmin() || current.isInterviewer();
        if (!privileged && !current.id().equals(interview.getUserId())) {
            throw BizException.forbidden("无权访问该面试");
        }
        return interview;
    }

    private Interview requireInterview(Long interviewId) {
        if (interviewId == null) {
            throw BizException.badRequest("面试 id 不能为空");
        }
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) {
            throw BizException.notFound("面试不存在");
        }
        return interview;
    }

    private PageResult<InterviewVO> page(Long userId, InterviewQuery query) {
        long current = query.pageOrDefault();
        long size = query.sizeOrDefault();
        var wrapper = Wrappers.<Interview>lambdaQuery()
                .eq(userId != null, Interview::getUserId, userId)
                .eq(query.getStatus() != null, Interview::getStatus, query.getStatus());
        if (!TextUtils.isBlank(query.getKeyword())) {
            List<JobPosition> positions = jobPositionMapper.selectList(Wrappers.<JobPosition>lambdaQuery()
                    .like(JobPosition::getTitle, query.getKeyword().trim()));
            if (positions.isEmpty()) {
                return PageResult.empty(current, size);
            }
            wrapper.in(Interview::getPositionId, positions.stream().map(JobPosition::getId).toList());
        }
        wrapper.orderByDesc(Interview::getCreatedAt).orderByDesc(Interview::getId);
        Page<Interview> page = interviewMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), toVOs(page.getRecords()));
    }

    private List<InterviewVO> toVOs(List<Interview> interviews) {
        if (interviews == null || interviews.isEmpty()) {
            return List.of();
        }
        List<Long> positionIds = interviews.stream()
                .map(Interview::getPositionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> titles = new HashMap<>();
        if (!positionIds.isEmpty()) {
            for (JobPosition position : jobPositionMapper.selectList(Wrappers.<JobPosition>lambdaQuery()
                    .in(JobPosition::getId, positionIds))) {
                titles.put(position.getId(), TextUtils.defaultIfBlank(position.getTitle(), ""));
            }
        }
        return interviews.stream()
                .map(interview -> InterviewVO.from(interview, titles.getOrDefault(interview.getPositionId(), "")))
                .toList();
    }

    private InterviewVO toVO(Interview interview) {
        String title = "";
        if (interview.getPositionId() != null) {
            JobPosition position = jobPositionMapper.selectById(interview.getPositionId());
            if (position != null) {
                title = TextUtils.defaultIfBlank(position.getTitle(), "");
            }
        }
        return InterviewVO.from(interview, title);
    }

    private List<InterviewQuestion> listQuestions(Long interviewId) {
        return questionMapper.selectList(Wrappers.<InterviewQuestion>lambdaQuery()
                .eq(InterviewQuestion::getInterviewId, interviewId)
                .orderByAsc(InterviewQuestion::getSeq));
    }

    private InterviewQuestion findQuestion(Long interviewId, int seq) {
        return questionMapper.selectOne(Wrappers.<InterviewQuestion>lambdaQuery()
                .eq(InterviewQuestion::getInterviewId, interviewId)
                .eq(InterviewQuestion::getSeq, seq)
                .last("LIMIT 1"));
    }

    private void writeMessage(Long interviewId, MessageRole role, MessageType type, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setInterviewId(interviewId);
        message.setRole(role);
        message.setType(type);
        message.setContent(content);
        messageMapper.insert(message);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(round1(value));
    }
}
