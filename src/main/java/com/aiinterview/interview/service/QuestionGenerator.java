package com.aiinterview.interview.service;

import com.aiinterview.ai.AiService;
import com.aiinterview.ai.model.GeneratedQuestion;
import com.aiinterview.ai.model.ParsedResume;
import com.aiinterview.ai.model.ProjectExperience;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.entity.InterviewQuestion;
import com.aiinterview.interview.enums.InterviewMode;
import com.aiinterview.job.entity.JobPosition;
import com.aiinterview.question.entity.QuestionBank;
import com.aiinterview.question.service.QuestionService;
import com.aiinterview.resume.entity.Resume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 出题器：有简历时 AI 优先并强制结合简历；无简历时题库优先，AI 与内置模板依次补题。
 *
 * <p>生成结果只填充 seq/content/category/difficulty/referenceAnswer，不落库；
 * 这样面试创建失败时可以整体回滚，也不会产生半成品题目。</p>
 */
@Service
public class QuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(QuestionGenerator.class);

    /** 未指定题数时的默认值 */
    private static final int DEFAULT_TOTAL = 5;
    /** 题数上限 */
    private static final int MAX_TOTAL = 15;
    /** 传给 Prompt 的历史题干条数上限 */
    private static final int PROMPT_HISTORY_LIMIT = 30;
    /** 传给 Prompt 的单条历史题干最大字符数 */
    private static final int PROMPT_HISTORY_CONTENT_MAX_CHARS = 60;
    /** 简历相关性校验失败时，允许让 AI 重出的次数 */
    private static final int RESUME_RETRY_MAX = 1;

    /** 内置兜底题模板，至少覆盖 Java / Spring / MySQL / Redis / 算法 / 软素质 */
    private static final List<QuestionTemplate> TEMPLATES = List.of(
            new QuestionTemplate("JAVA", 3,
                    "请谈谈 Java 中 HashMap 的实现原理，以及 JDK 8 做了哪些优化？",
                    "数组+链表+红黑树；哈希扰动；扩容为 2 倍；链表长度达到 8 且容量不小于 64 时转红黑树；非线程安全。"),
            new QuestionTemplate("JAVA", 4,
                    "JVM 的内存区域如何划分？垃圾回收如何判断对象可以被回收？",
                    "堆、方法区/元空间、虚拟机栈、本地方法栈、程序计数器；可达性分析，GC Roots 包括栈中引用、静态变量、常量等。"),
            new QuestionTemplate("JAVA", 4,
                    "线程池的核心参数有哪些？一个任务提交后线程池是如何处理的？",
                    "核心线程数、最大线程数、空闲存活时间、任务队列、拒绝策略；先建核心线程，再入队列，队列满后扩容到最大线程数，仍满则拒绝。"),
            new QuestionTemplate("SPRING", 4,
                    "Spring Bean 的生命周期包含哪些阶段？",
                    "实例化、属性填充、Aware 回调、BeanPostProcessor 前置、初始化方法、BeanPostProcessor 后置、使用、销毁。"),
            new QuestionTemplate("SPRING", 3,
                    "Spring 如何解决单例 Bean 的循环依赖？",
                    "三级缓存：singletonObjects、earlySingletonObjects、singletonFactories；提前暴露对象引用，配合 AOP 时通过 ObjectFactory 生成代理。"),
            new QuestionTemplate("SPRING", 3,
                    "Spring 事务传播行为有哪些？@Transactional 在什么情况下会失效？",
                    "REQUIRED、REQUIRES_NEW、NESTED、SUPPORTS 等；自调用、非 public 方法、异常被捕获、非运行时异常未配置 rollbackFor 时会失效。"),
            new QuestionTemplate("MYSQL", 4,
                    "MySQL InnoDB 的索引为什么选择 B+ 树？联合索引的最左前缀原则是什么？",
                    "B+ 树非叶子节点只存索引、叶子节点存数据并用链表连接，IO 次数少且范围查询高效；联合索引查询必须从最左列开始匹配。"),
            new QuestionTemplate("MYSQL", 4,
                    "数据库事务的隔离级别有哪些？MVCC 如何实现可重复读？",
                    "读未提交、读已提交、可重复读、串行化；InnoDB 通过隐藏列、undo log 版本链与 ReadView 实现一致性读。"),
            new QuestionTemplate("REDIS", 3,
                    "Redis 的缓存穿透、击穿、雪崩分别是什么？如何应对？",
                    "穿透：查询不存在的数据，用布隆过滤器或空值缓存；击穿：热点 key 失效，用互斥锁或逻辑过期；雪崩：大量 key 同时失效，加随机过期时间并做多级缓存。"),
            new QuestionTemplate("REDIS", 3,
                    "Redis 常见的数据结构及使用场景是什么？RDB 与 AOF 有什么区别？",
                    "String、Hash、List、Set、ZSet；RDB 为快照、恢复快但可能丢数据，AOF 记录写命令、更安全但文件更大，可混合持久化。"),
            new QuestionTemplate("ALGORITHM", 2,
                    "给定一个整数数组，如何找出两数之和等于目标值的下标？请分析时间复杂度。",
                    "用哈希表记录已遍历元素的值与下标，遍历时查找 target-num，时间复杂度 O(n)，空间复杂度 O(n)。"),
            new QuestionTemplate("ALGORITHM", 3,
                    "请解释快速排序的思想、平均与最坏时间复杂度，以及如何避免最坏情况。",
                    "选基准分区后递归排序，平均 O(n log n)、最坏 O(n^2)；随机选基准或三数取中可以避免最坏情况。"),
            new QuestionTemplate("MICROSERVICE", 4,
                    "微服务架构下如何做服务拆分？服务间通信与容错一般如何处理？",
                    "按业务领域拆分；通过 HTTP/RPC 或消息队列通信；使用注册中心、负载均衡、超时重试、熔断限流与链路追踪保障稳定性。"),
            new QuestionTemplate("COMPREHENSIVE", 3,
                    "如果线上接口响应变慢，你会如何排查和定位问题？",
                    "从监控与日志入手，确认影响范围，再排查慢 SQL、GC、线程池、下游依赖与网络，结合火焰图或链路追踪定位，最后灰度验证。"),
            new QuestionTemplate("BEHAVIOR", 2,
                    "请介绍一个你主导或深度参与的项目，说明你的职责、遇到的挑战和最终成果。",
                    "按 STAR 描述背景、目标、个人职责、关键动作与量化结果，突出技术难点与复盘。"),
            new QuestionTemplate("BEHAVIOR", 1,
                    "当你和同事在技术方案上产生分歧时，你会如何处理？请举一个真实例子。",
                    "先对齐目标与事实，再列出方案优缺点与风险，用数据或小规模验证支撑决策，必要时请上级决策并执行共识。"));

    private final AiService aiService;
    private final QuestionService questionService;
    private final QuestionHistoryService questionHistoryService;

    /**
     * 构造器注入。
     *
     * @param aiService              AI 能力门面
     * @param questionService        题库服务，无简历时优先取题、有简历时用于补题
     * @param questionHistoryService 历史题干查询服务，用于 Prompt 与二次去重
     */
    public QuestionGenerator(AiService aiService,
                             QuestionService questionService,
                             QuestionHistoryService questionHistoryService) {
        this.aiService = aiService;
        this.questionService = questionService;
        this.questionHistoryService = questionHistoryService;
    }

    /**
     * 为一场「招聘面试」生成题目。
     *
     * <p>招聘场景只是把岗位标题与 JD 作为出题上下文，内部统一委托给
     * {@link #generate(Interview, String, String, Resume)}，避免两套出题逻辑分叉。</p>
     *
     * @param interview 面试会话（需已确定题数与模式）
     * @param position  岗位，可为空
     * @param resume    已解析简历，可为空
     * @return 题目列表，seq 从 1 连续递增，数量等于面试总题数
     */
    public List<InterviewQuestion> generate(Interview interview, JobPosition position, Resume resume) {
        return generate(interview, position == null ? "" : position.getTitle(), jobDescription(position), resume);
    }

    /**
     * 为一场「面试练习」生成题目：直接用练习方向的标题与描述作为出题上下文。
     *
     * <p>出题路径：</p>
     * <ol>
     *     <li><b>有简历</b>：AI 优先，并要求每道题引用简历中的项目、技能或成果；题库只补缺口，
     *         最后用内置模板兜底。AI 题目若与简历毫无词汇重叠，会重出一次，仍失败则保留原题并记录；</li>
     *     <li><b>无简历</b>：题库优先（按模式尽量匹配分类，取不到再取通用题），题库不足才用 AI 补题，
     *         AI 仍失败则使用内置模板兜底；</li>
     *     <li>两条路径都会携带最近练习的历史题干，并在结果上做归一化二次去重；</li>
     *     <li>最终题目在 seq 连续递增的前提下随机洗牌，避免每次顺序相同。</li>
     * </ol>
     *
     * @param interview   练习会话（需已确定题数与模式）
     * @param title       练习方向名称，可为空
     * @param description 练习方向描述与重点考察方向，可为空
     * @param resume      已解析简历，可为空
     * @return 题目列表，seq 从 1 连续递增，数量等于练习总题数
     */
    public List<InterviewQuestion> generate(Interview interview, String title, String description, Resume resume) {
        int target = normalizeTotal(interview.getTotalQuestions());
        List<InterviewQuestion> result = new ArrayList<>();
        Set<String> usedContents = new LinkedHashSet<>();

        // 历史题干先进入去重集合，后续 AI / 题库 / 模板题一旦重复会被直接丢弃
        List<String> historyContents = loadHistory(interview);
        for (String historyContent : historyContents) {
            usedContents.add(normalize(historyContent));
        }
        // 无简历时不能把历史题干喂给模型：历史题里含用户简历的项目/数字，
        // 模型会「换角度复述」它们，导致明明没选简历却出了一堆简历题（实测缺陷）。
        List<String> promptHistory = resume == null ? List.of() : promptHistory(historyContents);

        // 无简历：题库优先；有简历：AI 优先，题库只在后面补缺口
        if (resume == null) {
            String category = bankCategory(interview.getMode());
            fillFromBank(interview, result, usedContents, target, category);
            if (category != null && result.size() < target) {
                fillFromBank(interview, result, usedContents, target, null);
            }
        }

        if (result.size() < target) {
            int need = target - result.size();
            List<GeneratedQuestion> candidates = prepareAiCandidates(
                    generateByAi(interview, title, description, resume, need, promptHistory),
                    usedContents, need);

            if (resume != null && !candidates.isEmpty()) {
                candidates = ensureResumeRelevant(interview, title, description, resume,
                        candidates, usedContents, promptHistory, need);
            }
            for (GeneratedQuestion generated : candidates) {
                if (result.size() >= target) {
                    break;
                }
                addGenerated(result, usedContents, generated);
            }
        }

        // 有简历时题库只做补齐；无简历时走到这里说明题库不足，同样用题库继续补齐
        if (result.size() < target) {
            fillFromBank(interview, result, usedContents, target, null);
        }

        for (QuestionTemplate template : TEMPLATES) {
            if (result.size() >= target) {
                break;
            }
            if (!usedContents.add(normalize(template.content()))) {
                continue;
            }
            result.add(newQuestion(result.size() + 1, template.content(), template.category(),
                    template.difficulty(), template.referenceAnswer()));
        }

        // 极端情况下（题库与内置题大面积重复）继续用模板补齐，保证题数完整
        int extra = 0;
        while (result.size() < target) {
            QuestionTemplate template = TEMPLATES.get(extra % TEMPLATES.size());
            String content = template.content() + "（进阶追问）";
            usedContents.add(normalize(content));
            result.add(newQuestion(result.size() + 1, content,
                    template.category(), template.difficulty(), template.referenceAnswer()));
            extra++;
        }

        // 洗牌后重新编号，保证顺序随机但 seq 仍从 1 连续递增
        Collections.shuffle(result, ThreadLocalRandom.current());
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setSeq(i + 1);
        }
        return result;
    }

    /**
     * 查询最近练习的历史题干，异常时降级为空列表。
     *
     * @param interview 当前会话
     * @return 历史题干列表，失败时为空列表
     */
    private List<String> loadHistory(Interview interview) {
        try {
            return questionHistoryService.recentQuestionContents(interview.getUserId(), interview.getId());
        } catch (RuntimeException e) {
            log.warn("历史题干加载失败，按无历史处理 interviewId={} reason={}",
                    interview.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 把历史题干裁剪为 Prompt 可用的列表：最多 30 条，每条最多 60 字。
     *
     * @param historyContents 历史题干
     * @return Prompt 用的历史题干列表
     */
    private static List<String> promptHistory(List<String> historyContents) {
        List<String> promptHistory = new ArrayList<>();
        if (historyContents == null) {
            return promptHistory;
        }
        for (String content : historyContents) {
            if (promptHistory.size() >= PROMPT_HISTORY_LIMIT) {
                break;
            }
            if (TextUtils.isBlank(content)) {
                continue;
            }
            promptHistory.add(TextUtils.truncate(content.trim(), PROMPT_HISTORY_CONTENT_MAX_CHARS));
        }
        return promptHistory;
    }

    /**
     * 调用 AI 出题，失败时降级为空列表。
     *
     * @param interview     会话
     * @param title         方向/岗位名称
     * @param description   方向描述/岗位 JD
     * @param resume        简历，可为空
     * @param target        需要生成的题数
     * @param promptHistory 历史题干（已裁剪）
     * @return AI 题目列表，失败时为空列表
     */
    private List<GeneratedQuestion> generateByAi(Interview interview, String title, String description,
                                                 Resume resume, int target, List<String> promptHistory) {
        try {
            return aiService.generateQuestions(
                    TextUtils.defaultIfBlank(title, ""),
                    TextUtils.defaultIfBlank(description, ""),
                    resumeSummary(resume),
                    target,
                    interview.getMode() == null ? InterviewMode.COMPREHENSIVE.name() : interview.getMode().name(),
                    promptHistory);
        } catch (BizException e) {
            log.warn("AI 出题失败，降级题库/内置模板 interviewId={} reason={}", interview.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 过滤 AI 题目：去空、去重（含历史题干），并按缺口截断。
     *
     * @param generatedQuestions AI 返回的题目
     * @param usedContents       已占用的归一化题干集合
     * @param need              当前还缺的题数
     * @return 可接受的 AI 题目
     */
    private List<GeneratedQuestion> prepareAiCandidates(List<GeneratedQuestion> generatedQuestions,
                                                        Set<String> usedContents, int need) {
        List<GeneratedQuestion> candidates = new ArrayList<>();
        if (generatedQuestions == null || need <= 0) {
            return candidates;
        }
        Set<String> seen = new LinkedHashSet<>(usedContents);
        for (GeneratedQuestion generated : generatedQuestions) {
            if (candidates.size() >= need) {
                break;
            }
            if (generated == null || TextUtils.isBlank(generated.content())) {
                continue;
            }
            String normalized = normalize(generated.content());
            if (!seen.add(normalized)) {
                log.debug("AI 题目与历史题干或本次题目重复，已过滤 content={}", generated.content());
                continue;
            }
            candidates.add(generated);
        }
        return candidates;
    }

    /**
     * 校验 AI 题目是否结合简历：与简历关键词毫无重叠的题会触发一次重出。
     *
     * <p>重出仍无法给出结合简历的替换题时，保留原题并记录 warn，保证题数与流程稳定。</p>
     *
     * @param interview     会话
     * @param title         方向/岗位名称
     * @param description   方向描述/岗位 JD
     * @param resume        简历
     * @param candidates    已通过去重的 AI 题目
     * @param usedContents  已占用的归一化题干集合
     * @param promptHistory 历史题干（已裁剪）
     * @param need          需要的题数
     * @return 校验后的题目列表，长度不变
     */
    private List<GeneratedQuestion> ensureResumeRelevant(Interview interview, String title, String description,
                                                         Resume resume, List<GeneratedQuestion> candidates,
                                                         Set<String> usedContents, List<String> promptHistory,
                                                         int need) {
        List<String> keywords = resumeKeywords(resume);
        if (keywords.isEmpty()) {
            return candidates;
        }
        List<Integer> irrelevantIndexes = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            String content = candidates.get(i).content();
            if (TextUtils.hits(content, keywords).isEmpty()) {
                irrelevantIndexes.add(i);
            }
        }
        if (irrelevantIndexes.isEmpty()) {
            return candidates;
        }
        for (Integer index : irrelevantIndexes) {
            log.warn("AI 题目与简历缺少词汇重叠，尝试重出一次 interviewId={} content={}",
                    interview.getId(), TextUtils.truncate(candidates.get(index).content(), 120));
        }

        List<GeneratedQuestion> merged = new ArrayList<>(candidates);
        Set<String> occupied = new LinkedHashSet<>(usedContents);
        for (GeneratedQuestion candidate : candidates) {
            occupied.add(normalize(candidate.content()));
        }
        List<GeneratedQuestion> replacements = new ArrayList<>();
        for (int attempt = 0; attempt < RESUME_RETRY_MAX; attempt++) {
            List<GeneratedQuestion> retryQuestions = generateByAi(
                    interview, title, description, resume, need, promptHistory);
            for (GeneratedQuestion retryQuestion : retryQuestions) {
                if (retryQuestion == null || TextUtils.isBlank(retryQuestion.content())) {
                    continue;
                }
                if (TextUtils.hits(retryQuestion.content(), keywords).isEmpty()) {
                    continue;
                }
                if (occupied.add(normalize(retryQuestion.content()))) {
                    replacements.add(retryQuestion);
                }
            }
            if (!replacements.isEmpty()) {
                break;
            }
        }

        int replacementIndex = 0;
        for (Integer index : irrelevantIndexes) {
            if (replacementIndex >= replacements.size()) {
                log.warn("AI 重出后仍无结合简历的替换题，保留原题 interviewId={} content={}",
                        interview.getId(), TextUtils.truncate(merged.get(index).content(), 120));
                continue;
            }
            merged.set(index, replacements.get(replacementIndex));
            replacementIndex++;
        }
        return merged;
    }

    /**
     * 从题库按分类补题，失败只记录 warn。
     *
     * @param interview   会话
     * @param result      当前题目列表
     * @param usedContents 已占用的归一化题干集合
     * @param target      目标题数
     * @param category    题目分类，为空表示通用题
     */
    private void fillFromBank(Interview interview, List<InterviewQuestion> result,
                              Set<String> usedContents, int target, String category) {
        int need = target - result.size();
        if (need <= 0) {
            return;
        }
        try {
            // 练习方向的 id 与题库岗位 id 语义不同，这里只按分类与通用题匹配，不误用 positionId
            List<QuestionBank> bankQuestions = questionService.pickRandom(null, category, need);
            if (bankQuestions == null) {
                return;
            }
            for (QuestionBank bankQuestion : bankQuestions) {
                if (result.size() >= target) {
                    break;
                }
                addBankQuestion(result, usedContents, bankQuestion);
            }
        } catch (RuntimeException e) {
            log.warn("题库补题失败，继续使用 AI/内置模板 interviewId={} category={} reason={}",
                    interview.getId(), category, e.getMessage());
        }
    }

    /**
     * 把 AI 题目加入结果集，重复题干会被丢弃。
     *
     * @param result       结果列表
     * @param usedContents 已占用的归一化题干集合
     * @param generated    AI 题目
     */
    private void addGenerated(List<InterviewQuestion> result, Set<String> usedContents, GeneratedQuestion generated) {
        if (generated == null || TextUtils.isBlank(generated.content())) {
            return;
        }
        String content = generated.content().trim();
        if (!usedContents.add(normalize(content))) {
            log.debug("AI 题目重复，已丢弃 content={}", content);
            return;
        }
        String referenceAnswer = generated.referenceAnswer();
        if (TextUtils.isBlank(referenceAnswer) && !generated.keywords().isEmpty()) {
            referenceAnswer = TextUtils.joinCsv(generated.keywords());
        }
        result.add(newQuestion(result.size() + 1, content, generated.category(),
                parseDifficulty(generated.difficulty()), referenceAnswer));
    }

    /**
     * 把题库题目加入结果集，重复题干会被丢弃。
     *
     * @param result       结果列表
     * @param usedContents 已占用的归一化题干集合
     * @param bankQuestion 题库题目
     */
    private void addBankQuestion(List<InterviewQuestion> result, Set<String> usedContents, QuestionBank bankQuestion) {
        if (bankQuestion == null || TextUtils.isBlank(bankQuestion.getContent())) {
            return;
        }
        String content = bankQuestion.getContent().trim();
        if (!usedContents.add(normalize(content))) {
            log.debug("题库题目与历史题干或本次题目重复，已丢弃 content={}", content);
            return;
        }
        String referenceAnswer = bankQuestion.getReferenceAnswer();
        if (TextUtils.isBlank(referenceAnswer) && !TextUtils.isBlank(bankQuestion.getKeywords())) {
            referenceAnswer = TextUtils.joinCsv(TextUtils.splitCsv(bankQuestion.getKeywords()));
        }
        int difficulty = bankQuestion.getDifficulty() == null ? 3 : clampDifficulty(bankQuestion.getDifficulty());
        result.add(newQuestion(result.size() + 1, content, bankQuestion.getCategory(), difficulty, referenceAnswer));
    }

    /**
     * 创建一条题目快照，不落库。
     *
     * @param seq             题号
     * @param content         题干
     * @param category        分类
     * @param difficulty      难度 1-5
     * @param referenceAnswer 参考答案
     * @return 题目实体
     */
    private InterviewQuestion newQuestion(int seq, String content, String category, int difficulty, String referenceAnswer) {
        InterviewQuestion question = new InterviewQuestion();
        question.setSeq(seq);
        question.setContent(content);
        question.setCategory(TextUtils.defaultIfBlank(category, "GENERAL"));
        question.setDifficulty(clampDifficulty(difficulty));
        question.setReferenceAnswer(referenceAnswer);
        return question;
    }

    /**
     * 归一化题干：去掉空白与标点、统一小写，用于「相同或高度相似」判断。
     *
     * @param content 题干
     * @return 归一化后的题干，空值返回空串
     */
    private static String normalize(String content) {
        if (TextUtils.isBlank(content)) {
            return "";
        }
        StringBuilder builder = new StringBuilder(content.length());
        for (char c : content.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    /**
     * 按面试模式选择题库分类：HR 面优先行为题，其余不限制分类。
     *
     * @param mode 面试模式，可为空
     * @return 题库分类，{@code null} 表示不限分类
     */
    private static String bankCategory(InterviewMode mode) {
        return mode == InterviewMode.HR ? "BEHAVIOR" : null;
    }

    /**
     * 汇总简历关键词，用于判断题目是否结合简历。
     *
     * @param resume 简历，可为空
     * @return 简历技能、项目名等关键词，没有可用关键词时返回空列表
     */
    private static List<String> resumeKeywords(Resume resume) {
        if (resume == null) {
            return List.of();
        }
        Set<String> keywords = new LinkedHashSet<>();
        ParsedResume parsed = parseResume(resume);
        if (parsed != null) {
            addKeywords(keywords, parsed.skills());
            for (ProjectExperience project : parsed.projects()) {
                if (project != null) {
                    addKeywords(keywords, List.of(project.name() == null ? "" : project.name()));
                }
            }
        }
        addKeywords(keywords, TextUtils.splitCsv(resume.getSkills()));
        return new ArrayList<>(keywords);
    }

    /**
     * 把候选关键词加入集合，过滤空值与过短词，避免「Go」这类短词误命中。
     *
     * @param keywords 目标集合
     * @param values   候选关键词
     */
    private static void addKeywords(Set<String> keywords, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.length() >= 2) {
                keywords.add(trimmed);
            }
        }
    }

    /**
     * 解析简历结构化 JSON，失败返回 {@code null}。
     *
     * @param resume 简历
     * @return 结构化简历或 {@code null}
     */
    private static ParsedResume parseResume(Resume resume) {
        if (resume == null || TextUtils.isBlank(resume.getParsedJson())) {
            return null;
        }
        try {
            return JsonUtils.fromJson(resume.getParsedJson(), ParsedResume.class);
        } catch (BizException e) {
            log.debug("简历解析 JSON 反序列化失败 resumeId={}", resume.getId());
            return null;
        }
    }

    /**
     * 组装简历摘要，供 Prompt 使用。
     *
     * <p>摘要包含姓名、工作年限、学历、技能、项目名与成果（项目描述）以及亮点；
     * 结构化解析缺失时退化为简历原文节选，保证 Prompt 里始终有简历信息。</p>
     *
     * @param resume 简历，可为空
     * @return 简历摘要，没有简历时返回空串
     */
    private static String resumeSummary(Resume resume) {
        if (resume == null) {
            return "";
        }
        ParsedResume parsed = parseResume(resume);
        StringBuilder builder = new StringBuilder();
        if (parsed != null) {
            if (!TextUtils.isBlank(parsed.name())) {
                builder.append("姓名：").append(parsed.name()).append('\n');
            }
            if (parsed.yearsOfExperience() != null) {
                builder.append("工作年限：").append(parsed.yearsOfExperience()).append(" 年\n");
            }
            if (!TextUtils.isBlank(parsed.education())) {
                builder.append("学历：").append(parsed.education()).append('\n');
            }
            if (!parsed.skills().isEmpty()) {
                builder.append("技能：").append(TextUtils.joinCsv(parsed.skills())).append('\n');
            }
            if (!TextUtils.isBlank(parsed.summary())) {
                builder.append("简介：").append(parsed.summary()).append('\n');
            }
            if (!parsed.projects().isEmpty()) {
                builder.append("项目经历：\n");
                for (ProjectExperience project : parsed.projects()) {
                    if (project == null || TextUtils.isBlank(project.name())) {
                        continue;
                    }
                    builder.append("- 项目：").append(project.name());
                    if (!TextUtils.isBlank(project.role())) {
                        builder.append("（角色：").append(project.role()).append("）");
                    }
                    if (!TextUtils.isBlank(project.description())) {
                        builder.append("；成果：").append(TextUtils.truncate(project.description().trim(), 200));
                    }
                    builder.append('\n');
                }
            }
            if (!parsed.highlights().isEmpty()) {
                builder.append("亮点：").append(TextUtils.joinCsv(parsed.highlights())).append('\n');
            }
        }
        if (parsed == null && !TextUtils.isBlank(resume.getRawText())) {
            builder.append("简历原文节选：")
                    .append(TextUtils.truncate(resume.getRawText().replaceAll("\\s+", " ").trim(), 1200))
                    .append('\n');
        }
        if (parsed == null || parsed.yearsOfExperience() == null) {
            if (resume.getYearsOfExperience() != null) {
                builder.append("工作年限：").append(resume.getYearsOfExperience()).append(" 年\n");
            }
        }
        if (parsed == null || TextUtils.isBlank(parsed.education())) {
            if (!TextUtils.isBlank(resume.getEducation())) {
                builder.append("学历：").append(resume.getEducation()).append('\n');
            }
        }
        if (!TextUtils.isBlank(resume.getSkills())) {
            builder.append("技能标签：").append(resume.getSkills()).append('\n');
        }
        return TextUtils.truncate(builder.toString().trim(), 2000);
    }

    /**
     * 归一化题数：空值或非法值取默认值，并夹到上限。
     *
     * @param totalQuestions 会话题数
     * @return 有效题数
     */
    private static int normalizeTotal(Integer totalQuestions) {
        if (totalQuestions == null || totalQuestions < 1) {
            return DEFAULT_TOTAL;
        }
        return Math.min(totalQuestions, MAX_TOTAL);
    }

    /**
     * 解析 AI 难度：支持数字与中英文关键词。
     *
     * @param difficulty AI 返回的难度
     * @return 难度 1-5
     */
    private static int parseDifficulty(String difficulty) {
        if (TextUtils.isBlank(difficulty)) {
            return 3;
        }
        String text = difficulty.trim().toUpperCase(Locale.ROOT);
        try {
            return clampDifficulty(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            // 非数字难度继续按关键词判断
        }
        if (text.contains("EXPERT") || text.contains("专家") || text.contains("资深")) {
            return 5;
        }
        if (text.contains("HARD") || text.contains("困难") || text.contains("高级") || text.contains("较难")) {
            return 4;
        }
        if (text.contains("EASY") || text.contains("简单") || text.contains("初级") || text.contains("基础")) {
            return 2;
        }
        return 3;
    }

    /**
     * 难度夹取到 1-5。
     *
     * @param difficulty 难度
     * @return 夹取后的难度
     */
    private static int clampDifficulty(int difficulty) {
        return Math.max(1, Math.min(5, difficulty));
    }

    /**
     * 组装岗位 JD：岗位描述 + 任职要求。
     *
     * @param position 岗位，可为空
     * @return 岗位 JD 文本，岗位为空时返回空串
     */
    private static String jobDescription(JobPosition position) {
        if (position == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isBlank(position.getDescription())) {
            builder.append(position.getDescription()).append('\n');
        }
        if (!TextUtils.isBlank(position.getRequirements())) {
            builder.append("任职要求：").append(position.getRequirements());
        }
        return TextUtils.truncate(builder.toString().trim(), 4000);
    }

    /**
     * 内置题模板。
     *
     * @param category        分类
     * @param difficulty      难度 1-5
     * @param content         题干
     * @param referenceAnswer 参考答案
     */
    private record QuestionTemplate(String category, int difficulty, String content, String referenceAnswer) {
    }
}
