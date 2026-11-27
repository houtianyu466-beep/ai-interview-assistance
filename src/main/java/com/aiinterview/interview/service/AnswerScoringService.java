package com.aiinterview.interview.service;

import com.aiinterview.ai.AiService;
import com.aiinterview.ai.model.AnswerEvaluation;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.entity.InterviewQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单题评分服务。
 *
 * <p>优先调用 AI 评分；AI 不可用时走规则降级：参考要点关键词命中 + 回答字数 + 结构词加权，
 * 保证面试流程不会因为 AI 故障而中断。</p>
 */
@Service
public class AnswerScoringService {

    private static final Logger log = LoggerFactory.getLogger(AnswerScoringService.class);

    /** 维度键，与 AI Prompt 约定一致 */
    private static final String DIM_ACCURACY = "accuracy";
    private static final String DIM_DEPTH = "depth";
    private static final String DIM_EXPRESSION = "expression";
    private static final String DIM_STRUCTURE = "structure";

    /** 结构表达词，命中越多说明表达越有条理 */
    private static final List<String> STRUCTURE_WORDS = List.of(
            "首先", "其次", "再次", "然后", "最后", "第一", "第二", "第三",
            "1.", "2.", "3.", "因为", "所以", "因此", "例如", "比如",
            "总结", "总之", "一方面", "另一方面");

    /** 技术关键词表：用于从题干/参考答案中抽取候选要点 */
    private static final List<String> TECH_KEYWORDS = List.of(
            "Java", "JVM", "GC", "Spring", "Spring Boot", "Spring Cloud", "MyBatis", "MySQL", "InnoDB",
            "B+树", "索引", "事务", "隔离级别", "MVCC", "锁", "Redis", "缓存", "持久化", "Kafka", "消息队列",
            "Docker", "Kubernetes", "K8s", "微服务", "分布式", "高可用", "限流", "熔断", "线程", "线程池",
            "并发", "HashMap", "集合", "红黑树", "算法", "时间复杂度", "空间复杂度", "动态规划", "排序",
            "设计模式", "TCP", "HTTP", "HTTPS", "REST", "接口", "数据库", "分库分表", "主从复制");

    /** 英文/数字技术词，例如 HashMap、JDK8、B+、MVCC */
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z][A-Za-z0-9+#.\\-]{1,29}");

    /** 候选关键词上限，避免长参考答案导致命中率被稀释 */
    private static final int MAX_CANDIDATES = 25;

    private final AiService aiService;

    /**
     * 构造器注入。
     *
     * @param aiService AI 能力门面
     */
    public AnswerScoringService(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 对单题回答评分。
     *
     * @param interview       面试会话
     * @param question        题目
     * @param answer          回答内容
     * @param thinkingSeconds 思考/作答耗时（秒）
     * @return 评分结果，包含总分、维度分、点评与命中/遗漏要点
     */
    public AnswerEvaluation evaluate(Interview interview, InterviewQuestion question, String answer, int thinkingSeconds) {
        if (TextUtils.isBlank(answer)) {
            return emptyEvaluation(question);
        }
        try {
            AnswerEvaluation evaluation = aiService.evaluateAnswer(
                    question.getContent(),
                    question.getReferenceAnswer(),
                    answer,
                    interview.getMode() == null ? null : interview.getMode().name());
            if (evaluation != null) {
                return evaluation;
            }
        } catch (BizException e) {
            log.warn("AI 评分失败，启用规则降级 interviewId={} seq={} reason={}",
                    interview.getId(), question.getSeq(), e.getMessage());
        }
        return ruleEvaluate(question, answer, thinkingSeconds);
    }

    /**
     * 未作答直接 0 分。
     */
    private AnswerEvaluation emptyEvaluation(InterviewQuestion question) {
        Map<String, Double> dimensions = new LinkedHashMap<>();
        dimensions.put(DIM_ACCURACY, 0.0);
        dimensions.put(DIM_DEPTH, 0.0);
        dimensions.put(DIM_EXPRESSION, 0.0);
        dimensions.put(DIM_STRUCTURE, 0.0);
        List<String> misses = candidates(question);
        if (misses.size() > 5) {
            misses = misses.subList(0, 5);
        }
        return new AnswerEvaluation(0.0, dimensions, "本题未作答，未获得分数。", List.of(), misses);
    }

    /**
     * 规则降级评分：关键词 65% + 字数 35% 得到准确性；结构词单独计分。
     */
    private AnswerEvaluation ruleEvaluate(InterviewQuestion question, String answer, int thinkingSeconds) {
        List<String> candidates = candidates(question);
        List<String> hits = TextUtils.hits(answer, candidates);

        int words = TextUtils.countWords(answer);
        double lengthScore = clamp(words * 1.2);
        double keywordScore = candidates.isEmpty()
                ? clamp(lengthScore * 0.6)
                : clamp(hits.size() * 100.0 / Math.min(candidates.size(), 6));

        int structureHits = 0;
        for (String word : STRUCTURE_WORDS) {
            if (answer.contains(word)) {
                structureHits++;
            }
        }
        double structureScore = clamp(structureHits * 20.0);

        double accuracy = clamp(keywordScore * 0.65 + lengthScore * 0.35);
        double depth = clamp(keywordScore * 0.55 + lengthScore * 0.25 + (structureHits >= 2 ? 10 : 0));
        double expression = clamp(lengthScore * 0.55 + structureScore * 0.45);
        double structure = clamp(structureScore);
        double total = clamp(accuracy * 0.4 + depth * 0.3 + expression * 0.15 + structure * 0.15);

        Map<String, Double> dimensions = new LinkedHashMap<>();
        dimensions.put(DIM_ACCURACY, round1(accuracy));
        dimensions.put(DIM_DEPTH, round1(depth));
        dimensions.put(DIM_EXPRESSION, round1(expression));
        dimensions.put(DIM_STRUCTURE, round1(structure));

        List<String> misses = candidates.stream()
                .filter(candidate -> !hits.contains(candidate))
                .limit(5)
                .toList();

        String comment = String.format(Locale.ROOT,
                "规则评分：回答约 %d 字，命中关键点 %d 个，结构表达 %d 分，思考 %d 秒。",
                words, hits.size(), (int) structureScore, thinkingSeconds);
        return new AnswerEvaluation(round1(total), dimensions, comment, hits.stream().limit(10).toList(), misses);
    }

    /**
     * 从题干与参考答案中抽取候选要点：英文技术词 + 内置技术词表 + 参考答案分句。
     */
    private List<String> candidates(InterviewQuestion question) {
        Set<String> result = new LinkedHashSet<>();
        String content = TextUtils.defaultIfBlank(question.getContent(), "");
        String reference = TextUtils.defaultIfBlank(question.getReferenceAnswer(), "");
        String context = content + " " + reference;
        String lowerContext = context.toLowerCase(Locale.ROOT);

        Matcher matcher = LATIN_WORD.matcher(context);
        while (matcher.find() && result.size() < MAX_CANDIDATES) {
            result.add(matcher.group());
        }
        for (String keyword : TECH_KEYWORDS) {
            if (result.size() >= MAX_CANDIDATES) {
                break;
            }
            if (lowerContext.contains(keyword.toLowerCase(Locale.ROOT))) {
                result.add(keyword);
            }
        }
        for (String segment : TextUtils.splitCsv(reference)) {
            if (result.size() >= MAX_CANDIDATES) {
                break;
            }
            String trimmed = segment.trim();
            if (trimmed.length() >= 2 && trimmed.length() <= 24) {
                result.add(trimmed);
            }
        }
        return new ArrayList<>(result);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
