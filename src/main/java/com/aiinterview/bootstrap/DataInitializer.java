package com.aiinterview.bootstrap;

import com.aiinterview.ai.model.ParsedResume;
import com.aiinterview.ai.model.ProjectExperience;
import com.aiinterview.ai.model.WorkExperience;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.job.entity.JobPosition;
import com.aiinterview.job.enums.JobLevel;
import com.aiinterview.job.enums.JobStatus;
import com.aiinterview.job.mapper.JobPositionMapper;
import com.aiinterview.knowledge.dto.KnowledgeIngestRequest;
import com.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import com.aiinterview.knowledge.service.KnowledgeService;
import com.aiinterview.practice.entity.PracticeTarget;
import com.aiinterview.practice.mapper.PracticeTargetMapper;
import com.aiinterview.question.entity.QuestionBank;
import com.aiinterview.question.mapper.QuestionBankMapper;
import com.aiinterview.resume.entity.Resume;
import com.aiinterview.resume.enums.ResumeStatus;
import com.aiinterview.resume.mapper.ResumeMapper;
import com.aiinterview.system.entity.SysUser;
import com.aiinterview.system.enums.UserRole;
import com.aiinterview.system.enums.UserStatus;
import com.aiinterview.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 演示数据初始化器。
 *
 * <p>项目启动后自动执行（可用 {@code app.demo-data.enabled=false} 关闭）。幂等：</p>
 * <ul>
 *     <li>用户表为空才创建三个演示账号；</li>
 *     <li>岗位表为空才创建岗位；</li>
 *     <li>题库为空才创建题目；</li>
 *     <li>练习方向表为空才创建 8 个练习方向；</li>
 *     <li>知识文档表为空才创建 6 篇面试资料；</li>
 *     <li>候选人没有简历时才创建一份已解析简历。</li>
 * </ul>
 *
 * <p>演示账号（密码均为 {@code 123456}）：admin / interviewer / candidate。</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEMO_PASSWORD = "123456";

    private final SysUserMapper userMapper;
    private final JobPositionMapper jobPositionMapper;
    private final QuestionBankMapper questionBankMapper;
    private final ResumeMapper resumeMapper;
    private final PracticeTargetMapper practiceTargetMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeService knowledgeService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造器注入。
     *
     * @param userMapper             用户 Mapper
     * @param jobPositionMapper      岗位 Mapper
     * @param questionBankMapper     题库 Mapper
     * @param resumeMapper           简历 Mapper
     * @param practiceTargetMapper   练习方向 Mapper
     * @param knowledgeDocumentMapper 知识文档 Mapper（仅用于判断是否需要灌库）
     * @param knowledgeService       知识库服务（负责切块与索引重建）
     * @param passwordEncoder        密码编码器
     */
    public DataInitializer(SysUserMapper userMapper, JobPositionMapper jobPositionMapper,
                           QuestionBankMapper questionBankMapper, ResumeMapper resumeMapper,
                           PracticeTargetMapper practiceTargetMapper,
                           KnowledgeDocumentMapper knowledgeDocumentMapper,
                           KnowledgeService knowledgeService,
                           PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.jobPositionMapper = jobPositionMapper;
        this.questionBankMapper = questionBankMapper;
        this.resumeMapper = resumeMapper;
        this.practiceTargetMapper = practiceTargetMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeService = knowledgeService;
        this.passwordEncoder = passwordEncoder;
    }

    private Long adminId;

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();
        seedJobs();
        seedQuestions();
        seedPracticeTargets();
        seedKnowledge();
        seedResume();
        log.info("演示数据初始化完成（账号 admin / interviewer / candidate，密码 123456）");
    }

    private void seedUsers() {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        SysUser admin = createUser("admin", "系统管理员", UserRole.ADMIN, "admin@aiinterview.com", "13800000001");
        adminId = admin.getId();
        createUser("interviewer", "李面试官", UserRole.INTERVIEWER, "interviewer@aiinterview.com", "13800000002");
        createUser("candidate", "张伟", UserRole.CANDIDATE, "candidate@aiinterview.com", "13800000003");
    }

    private SysUser createUser(String username, String realName, UserRole role, String email, String phone) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        user.setRealName(realName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        userMapper.insert(user);
        return user;
    }

    private void seedJobs() {
        if (jobPositionMapper.selectCount(null) > 0) {
            return;
        }
        createJob("Java 后端工程师", "交易中台", JobLevel.SENIOR,
                "1. 负责交易中台核心服务的架构设计与开发；\n2. 主导高并发场景下的性能优化；\n3. 参与技术方案评审与代码 Review。",
                "1. 5 年以上 Java 开发经验，熟悉 JVM、并发编程；\n2. 精通 Spring Boot / MyBatis，熟悉微服务治理；\n3. 熟悉 MySQL、Redis、Kafka，有高并发系统经验；\n4. 有交易/支付类业务经验优先。");
        createJob("Java 后端工程师（校招）", "电商研发部", JobLevel.JUNIOR,
                "1. 参与电商后台业务系统开发；\n2. 在导师指导下完成模块设计与编码。",
                "1. 计算机相关专业本科及以上；\n2. 掌握 Java 基础、数据结构与算法；\n3. 了解 Spring Boot、MySQL、Redis；\n4. 有项目或实习经历优先。");
        createJob("前端工程师", "用户增长部", JobLevel.MIDDLE,
                "1. 负责招聘平台 Web 端开发；\n2. 与设计、后端协作完成需求交付。",
                "1. 3 年以上前端经验，精通 Vue 或 React；\n2. 熟悉 TypeScript、工程化与性能优化；\n3. 有可视化或大屏项目经验优先。");
    }

    private void createJob(String title, String department, JobLevel level, String description, String requirements) {
        JobPosition job = new JobPosition();
        job.setTitle(title);
        job.setDepartment(department);
        job.setLevel(level);
        job.setDescription(description);
        job.setRequirements(requirements);
        job.setStatus(JobStatus.OPEN);
        job.setCreatedBy(adminId);
        jobPositionMapper.insert(job);
    }

    private void seedQuestions() {
        if (questionBankMapper.selectCount(null) > 0) {
            return;
        }
        createQuestion("JAVA", 3, "HashMap 的底层数据结构是什么？JDK 8 为什么引入红黑树？",
                "数组 + 链表 + 红黑树；哈希冲突时先链表，链表长度 >= 8 且数组长度 >= 64 时转为红黑树，查询复杂度从 O(n) 降为 O(log n)。",
                "数组,链表,红黑树,哈希冲突,扩容");
        createQuestion("JAVA", 4, "JVM 内存区域如何划分？常见垃圾回收算法有哪些？",
                "线程私有：程序计数器、虚拟机栈、本地方法栈；线程共享：堆、方法区（元空间）。GC 算法：标记-清除、复制、标记-整理、分代收集。",
                "堆,方法区,元空间,标记清除,复制算法,分代收集");
        createQuestion("JAVA", 3, "volatile 关键字的作用是什么？能保证原子性吗？",
                "保证可见性与有序性（禁止指令重排），不保证原子性；底层通过内存屏障实现。",
                "可见性,有序性,原子性,内存屏障");
        createQuestion("JAVA", 2, "线程池的核心参数有哪些？拒绝策略如何选择？",
                "核心线程数、最大线程数、空闲存活时间、时间单位、任务队列、线程工厂、拒绝策略；拒绝策略有 AbortPolicy、CallerRunsPolicy、DiscardPolicy、DiscardOldestPolicy。",
                "核心线程数,最大线程数,任务队列,拒绝策略");
        createQuestion("SPRING", 3, "Spring Bean 的生命周期包含哪些阶段？",
                "实例化 → 属性填充 → Aware 回调 → BeanPostProcessor 前置 → 初始化方法 → BeanPostProcessor 后置 → 使用 → 销毁。",
                "实例化,属性填充,Aware,初始化,销毁");
        createQuestion("SPRING", 4, "Spring 如何解决循环依赖？三级缓存分别存什么？",
                "通过三级缓存：singletonObjects 成品、earlySingletonObjects 早期引用、singletonFactories 对象工厂；AOP 场景下通过工厂提前暴露代理对象。",
                "三级缓存,循环依赖,早期引用,代理");
        createQuestion("SPRING", 3, "Spring AOP 的实现原理是什么？JDK 动态代理与 CGLIB 的区别？",
                "基于动态代理：目标类有接口时用 JDK Proxy，否则用 CGLIB 生成子类；CGLIB 无法代理 final 类/方法。",
                "动态代理,JDK Proxy,CGLIB,切面");
        createQuestion("MYSQL", 4, "InnoDB 为什么使用 B+ 树作为索引结构？聚簇索引与二级索引有什么区别？",
                "B+ 树矮胖、叶子节点链表适合范围查询；聚簇索引叶子存整行数据，二级索引叶子存主键，需要回表。",
                "B+树,聚簇索引,二级索引,回表,范围查询");
        createQuestion("MYSQL", 4, "MySQL 的事务隔离级别有哪些？MVCC 是如何实现的？",
                "读未提交、读已提交、可重复读、串行化；MVCC 通过 undo log 版本链 + ReadView 实现快照读。",
                "隔离级别,MVCC,undo log,ReadView,快照读");
        createQuestion("MYSQL", 3, "一条慢 SQL 你会如何排查和优化？",
                "explain 看执行计划 → 关注 type/key/rows/Extra → 加合适索引、避免索引失效、减少回表、拆分大查询、必要时分库分表。",
                "explain,索引,回表,索引失效,分库分表");
        createQuestion("REDIS", 3, "缓存穿透、击穿、雪崩分别是什么？如何解决？",
                "穿透：查不存在的数据，用布隆过滤器/空值缓存；击穿：热点 key 失效，用互斥锁/逻辑过期；雪崩：大量 key 同时失效，用过过期时间+多级缓存+熔断限流。",
                "布隆过滤器,空值缓存,互斥锁,随机过期,熔断");
        createQuestion("REDIS", 4, "如何用 Redis 实现分布式锁？需要注意哪些问题？",
                "SET key value NX PX；value 用唯一标识，释放时用 Lua 脚本校验；注意锁续期（看门狗）、可重入、主从切换导致的锁失效。",
                "SETNX,过期时间,Lua脚本,锁续期,可重入");
        createQuestion("DISTRIBUTED", 4, "分布式事务有哪些常见解决方案？各自适用场景是什么？",
                "2PC/XA、TCC、本地消息表、事务消息、Saga、Seata AT；强一致选 TCC/XA，最终一致选消息表/事务消息。",
                "2PC,TCC,本地消息表,事务消息,Saga,Seata");
        createQuestion("DISTRIBUTED", 3, "接口幂等性如何保证？请结合业务场景说明。",
                "唯一索引、Token 机制、状态机、分布式锁、去重表；需结合支付/下单等场景说明幂等键设计。",
                "唯一索引,Token,状态机,分布式锁,幂等键");
        createQuestion("ALGORITHM", 2, "如何反转一个单链表？请说明时间与空间复杂度。",
                "双指针迭代：prev/curr 逐节点反转指针，时间 O(n)、空间 O(1)；也可递归实现。",
                "双指针,迭代,递归,复杂度");
        createQuestion("BEHAVIOR", 2, "请描述你做过的最有挑战的一个项目，你承担了什么角色、如何解决困难？",
                "考察 STAR 结构：背景、任务、行动、结果；关注量化结果与反思。",
                "STAR,角色,困难,结果,复盘");
    }

    private void createQuestion(String category, int difficulty, String content,
                                String referenceAnswer, String keywords) {
        QuestionBank question = new QuestionBank();
        question.setCategory(category);
        question.setDifficulty(difficulty);
        question.setContent(content);
        question.setReferenceAnswer(referenceAnswer);
        question.setKeywords(keywords);
        question.setSource("MANUAL");
        question.setCreatedBy(adminId);
        questionBankMapper.insert(question);
    }

    /**
     * 插入 8 个演示练习方向（表为空才执行）。
     *
     * <p>练习方向是「面试练习工具」的入口：每个方向给出默认题数、难度、图标与重点考察范围，
     * 不包含任何招聘属性（部门、编制、录用建议等）。</p>
     */
    private void seedPracticeTargets() {
        if (practiceTargetMapper.selectCount(null) > 0) {
            return;
        }
        createPracticeTarget("java-backend", "Java 后端工程师", "Java / Spring Boot / MySQL / Redis",
                "BACKEND",
                "面向 Java 后端岗位的模拟面试，覆盖语言基础、框架原理以及数据库与缓存等高频考点。"
                        + "适合准备后端开发岗的求职者反复练习，逐步补齐技术深度。",
                "Java,Spring Boot,MySQL,Redis,并发编程", 5, 3, "Monitor", 1);
        createPracticeTarget("frontend", "前端工程师", "Vue / React / TypeScript / 工程化",
                "FRONTEND",
                "围绕前端工程实践展开，考察框架使用、浏览器原理与工程化能力。"
                        + "适合准备 React 或 Vue 方向岗位的求职者练习表达与项目复盘。",
                "Vue,React,TypeScript,浏览器,工程化", 5, 3, "Platform", 2);
        createPracticeTarget("product", "产品经理", "需求分析 / 优先级 / 数据指标",
                "PRODUCT",
                "模拟产品经理面试，重点考察需求分析、优先级判断与数据指标设计。"
                        + "适合没有产品岗面试经验的求职者熟悉常见问题与答题结构。",
                "需求分析,优先级,数据指标,沟通", 5, 3, "Goods", 3);
        createPracticeTarget("data", "数据分析师", "SQL / 指标体系 / A/B 实验",
                "DATA",
                "面向数据分析岗位，考察 SQL 能力、指标体系搭建与实验分析思路。"
                        + "适合想转行或刚进入数据分析领域的求职者练习案例表达。",
                "SQL,指标体系,A/B 实验,业务分析", 5, 3, "DataAnalysis", 4);
        createPracticeTarget("test", "测试工程师", "测试用例 / 自动化 / 接口测试",
                "TEST",
                "模拟测试工程师面试，覆盖测试用例设计、自动化测试与缺陷管理。"
                        + "适合准备测试岗的求职者练习系统性思考与问题定位。",
                "测试用例,自动化,接口测试,缺陷管理", 5, 2, "CircleCheck", 5);
        createPracticeTarget("devops", "运维/DevOps", "Linux / Docker / K8s / 监控",
                "DEVOPS",
                "面向运维与 DevOps 岗位，考察 Linux 基础、容器编排与线上故障排查。"
                        + "适合准备基础设施方向岗位的求职者练习场景题应答。",
                "Linux,Docker,K8s,监控,故障排查", 5, 4, "Setting", 6);
        createPracticeTarget("algorithm", "算法工程师", "数据结构 / 算法 / 机器学习",
                "ALGORITHM",
                "模拟算法工程师面试，覆盖数据结构、算法思路与机器学习基础。"
                        + "适合准备算法岗的求职者练习推导过程与复杂度分析。",
                "数据结构,算法,机器学习基础", 5, 4, "Cpu", 7);
        createPracticeTarget("hr-general", "通用 HR 面", "自我介绍 / 离职原因 / 职业规划",
                "HR",
                "通用 HR 面试练习，覆盖自我介绍、离职原因、职业规划与团队协作。"
                        + "适合所有求职者在正式面试前熟悉常见软性问题与表达方式。",
                "自我介绍,离职原因,职业规划,团队协作", 5, 2, "ChatDotRound", 8);
    }

    /**
     * 插入单个练习方向。
     *
     * @param code             唯一编码
     * @param title            方向名称
     * @param subtitle         副标题
     * @param category         分类
     * @param description      描述
     * @param focusTopics      重点考察方向，英文逗号分隔
     * @param defaultQuestions 默认题数
     * @param difficulty       默认难度 1-5
     * @param icon             前端图标名
     * @param sort             排序值
     */
    private void createPracticeTarget(String code, String title, String subtitle, String category,
                                      String description, String focusTopics,
                                      int defaultQuestions, int difficulty, String icon, int sort) {
        PracticeTarget target = new PracticeTarget();
        target.setCode(code);
        target.setTitle(title);
        target.setSubtitle(subtitle);
        target.setCategory(category);
        target.setDescription(description);
        target.setFocusTopics(focusTopics);
        target.setDefaultQuestions(defaultQuestions);
        target.setDifficulty(difficulty);
        target.setIcon(icon);
        target.setSort(sort);
        practiceTargetMapper.insert(target);
    }

    /**
     * 灌入 6 篇中文面试资料（knowledge_document 表为空才执行）。
     *
     * <p>内容直接走 {@link KnowledgeService#ingest}，因此会真实切块、建 BM25 索引；
     * 配置 embedding 后还会同时生成向量，练习创建与提示时即可检索到这些资料。</p>
     */
    private void seedKnowledge() {
        if (knowledgeDocumentMapper.selectCount(null) > 0) {
            return;
        }
        ingestKnowledge("STAR 法则：把经历讲成故事", "GUIDE", """
                很多候选人讲项目时从头到尾平铺直叙，面试官听完记不住重点。STAR 法则把一段经历压缩成四个部分：Situation（背景）、Task（任务）、Action（行动）、Result（结果）。先说清楚业务背景和难点，再说你个人承担的目标，接着讲你具体做了什么，最后用数据收尾。

                背景不要铺太久，一两句话说清「什么业务、什么规模、难在哪里」即可。例如：「我负责的交易中台每天处理 300 万订单，大促峰值 QPS 约 4000，当时订单服务是单体应用，一次发布要停服十分钟。」任务部分要区分团队目标和个人目标，重点说你负责的那一块，例如：「我负责订单查询链路的拆分，目标是把 P99 从 600 毫秒降到 200 毫秒以内。」

                行动是得分核心，要讲清技术选型和取舍，而不是罗列「我用了 Redis」。可以说：「我先用链路追踪定位到 70% 的耗时在多次回表查询，于是把订单详情改成 Redis 缓存加本地缓存两级结构，同时用本地消息表保证缓存与数据库最终一致。代价是增加了补偿任务，但换来了读性能提升。」

                结果必须量化，最好同时包含业务指标和技术指标，例如：「改造后 P99 降到 120 毫秒，数据库 QPS 下降 60%，大促期间零故障。」如果结果不理想，也要讲清复盘，例如：「方案上线后因为缓存击穿抖动过一次，我们补了互斥锁和热点探测，之后没再出现。」

                常见错误有三种：一是背景讲了五分钟还没进入正题；二是只说「我们」不说「我」，面试官无法判断你的贡献；三是结果只有「效果很好」这类形容词。练习时可以先写 200 字提纲，再口头讲 90 秒，录音回听，删掉所有与结论无关的细节。
                """);
        ingestKnowledge("30 秒自我介绍模板", "GUIDE", """
                自我介绍的目标不是复述简历，而是在 30 秒内给面试官一个「继续问下去」的理由。推荐结构：身份与年限、核心能力、一个代表性成果、求职动机。

                模板可以这样组织：「面试官您好，我是张伟，5 年 Java 后端开发经验，主要在交易和营销域做高并发系统。我比较擅长服务拆分与性能优化，最近一年主导了订单服务重构，把 P99 从 600 毫秒降到 120 毫秒，QPS 从 800 提升到 3500。我看到贵司这个岗位需要交易链路经验，所以来应聘。」

                四个部分各自控制在两句话以内。身份与年限让人快速定位你的级别；核心能力只讲两个方向，讲多了等于没有重点；代表性成果必须有数字，最好和岗位 JD 的关键词对应；求职动机一句话带过，说明为什么是这家公司、这个岗位。

                常见错误：第一，把简历从大学开始背一遍，30 秒变成 3 分钟；第二，堆砌形容词，例如「沟通能力强、抗压能力强、学习能力强」，没有任何证据；第三，过度谦虚或过度包装，例如「我只是打打杂」或者「整个系统都是我做的」；第四，完全不提岗位，听起来像群发。

                准备方法：写三个版本，分别对应 30 秒、60 秒和 2 分钟，面试时根据面试官的节奏选择。30 秒版本只保留一个成果，60 秒版本可以加一段项目细节，2 分钟版本再补充技术选型与复盘。开头和结尾要固定，中间内容可以替换，这样即使紧张也不会乱。

                最后注意语气：语速比平时慢一点，句子之间留半秒停顿，说完「所以来应聘」后停住，等面试官提问，不要自己往下补充。
                """);
        ingestKnowledge("技术面常见问题与答题结构", "TECH", """
                技术面问题大致分三类：原理类、场景类、项目类。三类题的答题结构不同，混用会显得答非所问。

                原理类问题通常以「是什么、为什么、怎么做」展开。例如问「MySQL 为什么用 B+ 树」，先给结论：「因为 B+ 树矮胖、非叶子节点只存索引，三次 IO 就能定位到千万级数据，而且叶子节点用链表连接，范围查询效率高。」再讲对比：「红黑树高度更高，磁盘 IO 次数多；哈希索引不支持范围查询。」最后补边界：「所以 B+ 树更适合磁盘存储和范围扫描，内存结构反而可以用跳表或哈希。」

                场景类问题通常是「线上接口变慢，你怎么排查」。答题时先讲方法论，再讲具体手段，最后讲验证。可以说：「我先看监控确认影响范围和开始时间，再看日志与链路追踪定位到具体下游，然后按慢 SQL、GC、线程池、连接池、下游依赖的顺序排查。比如上次大促，我发现是订单查询回表太多，用执行计划确认后加了联合索引，并给热点数据加了缓存，上线后 P99 从 800 毫秒降到 150 毫秒。」注意不要一上来就说「加缓存」，面试官更想听你的判断依据。

                项目类问题用 STAR 结构，重点讲你的动作和取舍，避免变成产品介绍。

                答题时可以用「总—分—总」的信号词帮助面试官跟上：「我先说结论」「原因有三个」「所以最终选择了方案 B」。遇到不会的问题，不要硬编，可以说：「这块我只了解基本概念，我的理解是……不确定的部分我回去查证。」诚实比编造更加分。

                常见错误：一是背八股，答案正确但和问题无关；二是只讲结论不讲原因，例如「用 Redis 就行了」；三是被追问一层就卡住，说明只记住了答案没有理解原理；四是答非所问，把「如何排查」答成「如何优化」。练习时可以让同伴连续追问三层「为什么」，检验自己的理解深度。
                """);
        ingestKnowledge("HR 面高频问题与回答原则", "HR", """
                HR 面看似聊家常，实际在考察稳定性、动机、协作方式和自我认知。回答原则只有一条：真实、具体、不贬低他人。

                「为什么离职」是高频题。不要说前公司坏话，也不要说「钱少事多」。推荐结构：客观原因加个人诉求。例如：「公司业务调整，我所在的交易团队被合并，工作内容转向偏运维。我希望继续做业务架构和性能优化，所以想找一个技术挑战更匹配的团队。」如果确实是因为薪资，也可以坦诚表达，但要说明你的市场价值和期望区间。

                「职业规划」考察的是方向感，不是口号。可以说：「未来三年我希望在分布式交易领域做深，先补齐稳定性与一致性方面的经验，再逐步承担跨团队的技术方案设计。」避免「三年当总监」这类与岗位无关的答案。

                「你的缺点」不要说「我太追求完美」。选一个真实但不致命的缺点，并说明改进行动。例如：「我以前做方案时容易忽略沟通，直接埋头写代码，导致和产品预期有偏差。后来我养成了需求评审后复述一遍的习惯，确认关键指标和对齐口径再动手。」

                「你怎么看待加班」不要直接说「完全不能加班」或「随便加」。可以说：「项目上线和故障处理需要加班我完全可以配合，同时我也希望通过合理排期和自动化减少无效加班。」

                「你还有什么问题问我」可以参考另一篇《面试收尾：反问与致谢》。

                常见错误：一是背标准答案，语速和表情都很僵硬；二是前后矛盾，比如前面说想深耕技术，后面说想转管理；三是过度打探公司隐私，例如追问加班费、裁员计划；四是把 HR 当对手，回答带情绪。练习时把自己的回答录下来，重点听有没有抱怨和空话。
                """);
        ingestKnowledge("项目经历怎么讲：背景-任务-行动-结果", "TECH", """
                面试官问项目，真正想确认三件事：这件事是不是你做的、你的技术判断是否靠谱、结果是否可验证。所以项目描述要围绕「背景、任务、行动、结果」四段展开，每段都要有信息量。

                背景要给出约束条件：业务规模、团队规模、时间窗口、技术债。例如：「库存服务是五年前的单体应用，支撑 2000 家门店，日常 QPS 3000，大促峰值 1.2 万，代码里库存扣减和订单创建在同一个事务里。」

                任务要说明你的角色和边界。例如：「我是这个专项的技术负责人，带 3 个人，负责库存扣减链路的重构，目标是把超卖率降到万分之一以下，同时把接口 P99 控制在 100 毫秒内。」

                行动要讲方案对比和落地细节。可以说：「我们评估了三种方案：强一致的分布式事务、基于 Kafka 的最终一致、以及预扣库存加异步对账。前两种对性能影响大，最终选择预扣加消息补偿，用唯一索引保证幂等，用定时对账兜底。我负责消息表设计和幂等键选型，幂等键用订单号加商品 ID，避免重复扣减。」注意把「我们」和「我」分清楚。

                结果要可验证，能说清指标口径。例如：「上线后超卖率降到十万分之三，P99 从 300 毫秒降到 80 毫秒，大促期间补偿任务日均处理 200 条，全部自动修复。」如果没有达成目标，也要说明原因和后续动作。

                常见错误：一是只讲功能不讲难点，听起来像需求文档；二是所有决策都是「领导定的」，看不出个人贡献；三是数据夸张且无法解释口径，一追问就露馅；四是忽略失败与复盘，显得不够真实。建议每个项目准备 1 分钟版和 3 分钟版，并预判三个追问：为什么不用另一个方案、遇到最大的坑是什么、如果重做会怎么改。
                """);
        ingestKnowledge("面试收尾：反问与致谢", "GUIDE", """
                面试最后「你有什么问题问我」不是客套，而是最后一次展示机会。好的反问能体现你已经在思考如何把工作做好。

                推荐问三类问题。第一类问业务与团队：「这个岗位所在的团队目前有多少人，主要负责哪条业务线，未来半年的重点是什么？」第二类问职责与评价标准：「这个岗位入职后前三个月最重要的产出是什么，团队如何衡量一个人的成长？」第三类问技术挑战：「团队目前在稳定性或性能上最大的挑战是什么，现在用什么方式解决？」这些问题都指向「我能贡献什么」，而不是「公司能给我什么」。

                要避免的问题：一上来问薪资、加班、年终奖，容易显得只看待遇，这些可以等 HR 面或 offer 阶段再谈；问「公司是做什么的」，说明你完全没有做功课；问「我表现得怎么样」，把压力推回给面试官；问过于宏大的问题，例如「公司未来的战略规划是什么」，面试官很难在几分钟内回答。

                如果面试官已经详细介绍了岗位，可以基于他的介绍追问一个具体点，例如：「您刚才提到团队正在做服务拆分，目前拆分的边界是按业务域还是按数据域划分的？」这比背问题清单更自然。

                收尾时用一句话致谢并确认后续节奏，例如：「谢谢您的时间，今天聊完我对这个岗位的理解更具体了。请问后续大概什么时候会有反馈？」面试结束后 24 小时内可以发一封简短的感谢邮件，提到面试中让你印象最深的一个点，不要复制模板，也不要催促结果。

                常见错误：说「没有问题」，显得兴趣不足；问太多问题占用时间；把反问变成辩论；面试结束后在社交平台吐槽面试官。记住，反问的目的是建立连接，不是考核对方。
                """);
        ingestKnowledge("项目深挖：架构设计与技术选型怎么讲", "TECH", """
                面试官问「介绍一个最能体现技术深度的模块，重点说明架构设计与技术选型」，真正想听的不是功能清单，而是你的判断过程。推荐按「背景约束 → 候选方案 → 选型依据 → 落地细节 → 结果与代价」讲。

                先说约束：业务规模、团队规模、时间窗口、稳定性要求。例如：「订单查询每天 300 万单，大促峰值 QPS 4000，团队 6 个人，要求三个月内上线，不能影响现有交易链路。」约束决定了方案空间，也决定了面试官会怎么评价你的取舍。

                再说候选方案与选型依据：至少给出两个方案并对比，例如「本地缓存 + Redis 两级缓存」对比「直接查库 + 读写分离」。对比维度包括一致性、延迟、成本、运维复杂度、故障影响面。技术选型要说出为什么不是另一个方案，例如「没有选读写分离，是因为主从延迟会导致用户看到旧订单状态，而交易场景对一致性更敏感」。

                落地细节讲三点：关键数据结构或表设计、并发控制与幂等、异常与降级。例如「用本地消息表保证缓存与数据库最终一致，消费端用订单号做幂等，Redis 故障时降级为直接查库并限流」。

                最后给结果和代价：P99 从 600ms 降到 120ms，成本是增加了一个补偿任务和一套监控告警。主动说代价，比只报喜更能体现技术成熟度。常见错误是全程讲「用了什么」，不讲「为什么不用什么」，也不给量化结果。
                """);
        ingestKnowledge("项目难点与亮点：怎么讲出技术深度", "TECH", """
                讲项目难点时，不要选「需求多、时间紧」这类不可验证的困难，要选技术上有取舍的点：高并发下的数据一致性、缓存与数据库双写、分布式事务、慢查询与索引、消息重复消费、服务拆分边界。

                推荐结构：现象（可观测的问题）→ 定位（用什么工具、看什么指标）→ 原因（根因分析）→ 方案（候选与取舍）→ 验证（压测或线上指标）→ 复盘（如果重做会怎么改）。例如：「大促前压测发现订单查询 P99 超过 800ms，用链路追踪定位到 70% 耗时在多次回表，根因是查询没走覆盖索引；补了联合索引并加 Redis 缓存后 P99 降到 120ms；复盘时发现缓存击穿没有兜底，后来补了互斥锁和空值缓存。」

                亮点不要写成「我负责了什么」，而要写成「我改变了什么」：指标变化、故障率下降、发布效率提升、成本下降。能量化就量化，不能量化就说清影响范围。

                面试官追问「如果数据量再涨十倍怎么办」时，不要立刻说分库分表，先判断瓶颈在存储、查询还是写入，再给出演进路径：索引与缓存 → 读写分离 → 分库分表 → 冷热分离。体现你知道每一步的代价。
                """);
        ingestKnowledge("Java 后端高频考点：原理、场景与取舍", "TECH", """
                Java 后端面试通常围绕五块：Java 基础与并发、JVM、Spring、MySQL、Redis，再加分布式与项目。

                Java 并发要能讲清线程池参数与拒绝策略、synchronized 与 ReentrantLock 的区别、volatile 的可见性与有序性、AQS 的基本思想。JVM 要能讲内存区域、对象创建过程、常见 GC 算法与垃圾收集器、线上排查思路（jstack、jmap、GC 日志）。

                Spring 重点在 Bean 生命周期、循环依赖与三级缓存、AOP 与动态代理、事务传播与失效场景。MySQL 重点在 B+ 树索引、聚簇索引与回表、执行计划、事务隔离级别与 MVCC、慢查询优化。Redis 重点在数据结构与使用场景、缓存穿透/击穿/雪崩、分布式锁、持久化与主从。

                分布式要能讲幂等、分布式事务方案对比、消息可靠性与重复消费、服务拆分边界。回答时遵循「先结论，再原理，再场景，最后取舍」，每个点尽量落到自己的项目：为什么用、遇到什么问题、怎么验证。不会的问题不要硬编，可以说「这块我只了解基本概念，我的理解是……不确定的部分是……」，然后给出排查思路。
                """);
    }

    /**
     * 通过知识库服务灌入一篇资料，保证走真实的切块与索引流程。
     *
     * @param title    标题
     * @param category 分类
     * @param content  正文
     */
    private void ingestKnowledge(String title, String category, String content) {
        knowledgeService.ingest(new KnowledgeIngestRequest(title, category, content));
    }

    private void seedResume() {
        SysUser candidate = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, "candidate"));
        if (candidate == null) {
            return;
        }
        Long count = resumeMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, candidate.getId()));
        if (count != null && count > 0) {
            return;
        }
        ParsedResume parsed = new ParsedResume(
                "张伟", "zhangwei@example.com", "13800000003", 5, "本科",
                List.of("Java", "Spring Boot", "MyBatis", "MySQL", "Redis", "Kafka", "Docker"),
                List.of(new WorkExperience("某互联网科技有限公司", "高级 Java 工程师", "2021.06-至今",
                                "负责交易中台核心链路，主导订单服务重构，QPS 从 800 提升到 3500。"),
                        new WorkExperience("某软件外包公司", "Java 工程师", "2019.07-2021.05",
                                "参与电商后台系统开发，负责商品与库存模块。")),
                List.of(new ProjectExperience("交易中台订单服务重构", "技术负责人", "2023.03-2023.12",
                                "拆分单体订单服务，引入 Redis 缓存与本地消息表解决分布式事务，P99 从 600ms 降到 120ms。"),
                        new ProjectExperience("库存一致性方案", "核心开发", "2022.05-2022.10",
                                "基于 Kafka 实现库存扣减最终一致，支持幂等与补偿。")),
                List.of("有高并发交易链路经验", "熟悉缓存与消息中间件", "有服务拆分与重构经验"),
                "5 年 Java 后端经验，擅长交易与高并发场景，具备服务拆分与性能优化实战经验。");

        Resume resume = new Resume();
        resume.setUserId(candidate.getId());
        resume.setFileName("张伟-Java后端-5年.docx");
        resume.setRawText("张伟，本科，5 年 Java 后端开发经验，熟悉 Spring Boot、MySQL、Redis、Kafka，"
                + "主导过交易中台订单服务重构，QPS 800 → 3500，P99 600ms → 120ms。");
        resume.setParsedJson(JsonUtils.toJson(parsed));
        resume.setSkills(String.join(",", parsed.skills()));
        resume.setYearsOfExperience(5);
        resume.setEducation("本科");
        resume.setStatus(ResumeStatus.PARSED);
        resumeMapper.insert(resume);
    }
}
