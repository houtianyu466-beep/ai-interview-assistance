package com.aiinterview.interview.service;

import com.aiinterview.ai.AiService;
import com.aiinterview.ai.model.GeneratedQuestion;
import com.aiinterview.ai.model.ParsedResume;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 出题器：AI 优先，题库次之，最后使用内置通用题模板兜底。
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

    /**
     * 构造器注入。
     *
     * @param aiService       AI 能力门面
     * @param questionService 题库服务，用于 AI 不可用时补题
     */
    public QuestionGenerator(AiService aiService, QuestionService questionService) {
        this.aiService = aiService;
        this.questionService = questionService;
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
     * <p>生成顺序保持原逻辑：AI 优先 → 通用题库补题 → 内置模板补齐并去重。
     * 练习方向的 id 沿用 {@code interview_session.position_id} 字段，与题库的岗位 id 语义不同，
     * 因此题库补题只取通用题（position_id 为空）。</p>
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

        for (GeneratedQuestion generated : generateByAi(interview, title, description, resume, target)) {
            if (result.size() >= target) {
                break;
            }
            addGenerated(result, usedContents, generated);
        }

        if (result.size() < target) {
            int need = target - result.size();
            try {
                List<QuestionBank> bankQuestions = questionService.pickRandom(null, null, need);
                if (bankQuestions != null) {
                    for (QuestionBank bankQuestion : bankQuestions) {
                        if (result.size() >= target) {
                            break;
                        }
                        addBankQuestion(result, usedContents, bankQuestion);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("题库补题失败，继续使用内置模板 interviewId={} reason={}", interview.getId(), e.getMessage());
            }
        }

        for (QuestionTemplate template : TEMPLATES) {
            if (result.size() >= target) {
                break;
            }
            if (!usedContents.add(template.content())) {
                continue;
            }
            result.add(newQuestion(result.size() + 1, template.content(), template.category(),
                    template.difficulty(), template.referenceAnswer()));
        }

        // 极端情况下（题库与内置题大面积重复）继续用模板补齐，保证题数完整
        int extra = 0;
        while (result.size() < target) {
            QuestionTemplate template = TEMPLATES.get(extra % TEMPLATES.size());
            result.add(newQuestion(result.size() + 1, template.content() + "（进阶追问）",
                    template.category(), template.difficulty(), template.referenceAnswer()));
            extra++;
        }
        return result;
    }

    private List<GeneratedQuestion> generateByAi(Interview interview, String title, String description,
                                                 Resume resume, int target) {
        try {
            return aiService.generateQuestions(
                    TextUtils.defaultIfBlank(title, ""),
                    TextUtils.defaultIfBlank(description, ""),
                    resumeSummary(resume),
                    target,
                    interview.getMode() == null ? InterviewMode.COMPREHENSIVE.name() : interview.getMode().name());
        } catch (BizException e) {
            log.warn("AI 出题失败，降级题库/内置模板 interviewId={} reason={}", interview.getId(), e.getMessage());
            return List.of();
        }
    }

    private void addGenerated(List<InterviewQuestion> result, Set<String> usedContents, GeneratedQuestion generated) {
        if (generated == null || TextUtils.isBlank(generated.content())) {
            return;
        }
        String content = generated.content().trim();
        if (!usedContents.add(content)) {
            return;
        }
        String referenceAnswer = generated.referenceAnswer();
        if (TextUtils.isBlank(referenceAnswer) && !generated.keywords().isEmpty()) {
            referenceAnswer = TextUtils.joinCsv(generated.keywords());
        }
        result.add(newQuestion(result.size() + 1, content, generated.category(),
                parseDifficulty(generated.difficulty()), referenceAnswer));
    }

    private void addBankQuestion(List<InterviewQuestion> result, Set<String> usedContents, QuestionBank bankQuestion) {
        if (bankQuestion == null || TextUtils.isBlank(bankQuestion.getContent())) {
            return;
        }
        String content = bankQuestion.getContent().trim();
        if (!usedContents.add(content)) {
            return;
        }
        String referenceAnswer = bankQuestion.getReferenceAnswer();
        if (TextUtils.isBlank(referenceAnswer) && !TextUtils.isBlank(bankQuestion.getKeywords())) {
            referenceAnswer = TextUtils.joinCsv(TextUtils.splitCsv(bankQuestion.getKeywords()));
        }
        int difficulty = bankQuestion.getDifficulty() == null ? 3 : clampDifficulty(bankQuestion.getDifficulty());
        result.add(newQuestion(result.size() + 1, content, bankQuestion.getCategory(), difficulty, referenceAnswer));
    }

    private InterviewQuestion newQuestion(int seq, String content, String category, int difficulty, String referenceAnswer) {
        InterviewQuestion question = new InterviewQuestion();
        question.setSeq(seq);
        question.setContent(content);
        question.setCategory(TextUtils.defaultIfBlank(category, "GENERAL"));
        question.setDifficulty(clampDifficulty(difficulty));
        question.setReferenceAnswer(referenceAnswer);
        return question;
    }

    private static int normalizeTotal(Integer totalQuestions) {
        if (totalQuestions == null || totalQuestions < 1) {
            return DEFAULT_TOTAL;
        }
        return Math.min(totalQuestions, MAX_TOTAL);
    }

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

    private static int clampDifficulty(int difficulty) {
        return Math.max(1, Math.min(5, difficulty));
    }

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

    private static String resumeSummary(Resume resume) {
        if (resume == null) {
            return "";
        }
        ParsedResume parsed = null;
        if (!TextUtils.isBlank(resume.getParsedJson())) {
            try {
                parsed = JsonUtils.fromJson(resume.getParsedJson(), ParsedResume.class);
            } catch (BizException e) {
                log.debug("简历解析 JSON 反序列化失败 resumeId={}", resume.getId());
            }
        }
        StringBuilder builder = new StringBuilder();
        if (parsed != null) {
            if (!TextUtils.isBlank(parsed.summary())) {
                builder.append("简介：").append(parsed.summary()).append('\n');
            }
            if (!parsed.skills().isEmpty()) {
                builder.append("技能：").append(TextUtils.joinCsv(parsed.skills())).append('\n');
            }
            if (parsed.yearsOfExperience() != null) {
                builder.append("工作年限：").append(parsed.yearsOfExperience()).append(" 年\n");
            }
            if (!TextUtils.isBlank(parsed.education())) {
                builder.append("学历：").append(parsed.education()).append('\n');
            }
            if (!parsed.highlights().isEmpty()) {
                builder.append("亮点：").append(TextUtils.joinCsv(parsed.highlights())).append('\n');
            }
            if (!parsed.projects().isEmpty()) {
                builder.append("项目：").append(TextUtils.joinCsv(parsed.projects().stream()
                        .map(project -> project.name() == null ? "" : project.name())
                        .filter(name -> !name.isBlank())
                        .toList())).append('\n');
            }
        }
        if (!TextUtils.isBlank(resume.getSkills())) {
            builder.append("技能标签：").append(resume.getSkills()).append('\n');
        }
        if (!TextUtils.isBlank(resume.getEducation())) {
            builder.append("学历：").append(resume.getEducation());
        }
        return TextUtils.truncate(builder.toString().trim(), 2000);
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
