package com.aiinterview.ai;

import com.aiinterview.ai.model.ParsedResume;
import com.aiinterview.common.util.JsonUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 离线大模型桩实现（{@code app.ai.provider=mock}，默认）。
 *
 * <p>为什么需要它：源码阅读 / 本地演示时不应强依赖 API Key 与外网。
 * 它按场景返回结构合法的 JSON，并做了一点「伪智能」：</p>
 * <ul>
 *     <li>出题：从内置题库模板中按要求的数量返回；</li>
 *     <li>评分：根据回答长度与关键词粗略打分，保证流程有真实波动。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private static final Pattern COUNT_PATTERN = Pattern.compile("共\\s*(\\d+)\\s*道");

    /** 常见技术栈词表：命中的关键词会作为结构化技能返回，让 Mock 解析结果与输入相关。 */
    private static final List<String> SKILL_KEYWORDS = List.of(
            "Spring Boot", "Spring Cloud", "Spring", "MyBatis", "Java", "MySQL", "Oracle",
            "Redis", "MongoDB", "Kafka", "RabbitMQ", "RocketMQ", "Docker", "Kubernetes",
            "Linux", "Nginx", "Elasticsearch", "JVM", "Python", "Go", "Vue", "React",
            "TypeScript", "微服务", "分布式", "高并发");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern EDUCATION_PATTERN = Pattern.compile("(博士|硕士|本科|大专|专科)");
    private static final Pattern YEARS_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*年");

    private static final List<String> QUESTION_TEMPLATES = List.of(
            "请介绍一下你在简历中提到的项目里，最能体现你技术深度的一个模块，重点说明架构设计与技术选型。",
            "Java 中 HashMap 的底层结构是什么？JDK 8 为什么引入红黑树？",
            "Spring Bean 的生命周期包含哪些阶段？循环依赖是如何解决的？",
            "MySQL InnoDB 的索引结构为什么选择 B+ 树？聚簇索引与二级索引有什么区别？",
            "Redis 缓存穿透、击穿、雪崩分别是什么？你在项目中如何解决？",
            "如何保证接口幂等性？请结合具体业务场景说明你的实现方案。",
            "线程池的核心参数有哪些？拒绝策略如何选择？线上如何排查线程池打满？",
            "分布式事务有哪些常见方案？Seata 的 AT 模式原理是什么？",
            "你在项目中做过哪些性能优化？优化前后的指标如何量化？",
            "当你和产品经理对需求理解不一致时，你会怎么处理？请举例说明。"
    );

    @Override
    public LlmResponse chat(String scene, List<LlmMessage> messages, double temperature) {
        long start = System.currentTimeMillis();
        String all = String.join("\n", messages.stream().map(LlmMessage::content).toList());
        // 简历解析只取最后一条 user 消息，避免把 system 提示里的字眼当成候选人信息
        String resumeText = messages.stream()
                .filter(message -> "user".equals(message.role()))
                .reduce((first, second) -> second)
                .map(LlmMessage::content)
                .orElse(all);
        String content = switch (scene) {
            case AiScenes.RESUME_PARSE -> resumeJson(resumeText);
            case AiScenes.QUESTION_GENERATE -> questionsJson(all);
            case AiScenes.ANSWER_SCORE -> scoreJson(all);
            case AiScenes.REPORT_GENERATE -> reportJson();
            case AiScenes.RESUME_MATCH -> matchJson();
            case AiScenes.HINT -> hintText(all);
            default -> "{}";
        };
        return new LlmResponse(content, modelName(), estimateTokens(all), estimateTokens(content),
                System.currentTimeMillis() - start);
    }

    /**
     * 解析简历：从输入文本里提取姓名、联系方式、学历、年限与技能，返回与输入相关的结构化 JSON。
     *
     * <p>真实模型会理解语义；Mock 用规则模拟，保证本地演示「输入什么就解析出什么」，
     * 而不是永远返回同一份样例。</p>
     */
    private String resumeJson(String prompt) {
        String text = extractResumeText(prompt);
        String name = extractName(text);
        String email = firstMatch(EMAIL_PATTERN, text);
        String phone = firstMatch(PHONE_PATTERN, text);
        String education = firstMatch(EDUCATION_PATTERN, text);
        Integer years = extractYears(text);
        List<String> skills = extractSkills(text);
        List<String> highlights = skills.stream().limit(3).map(skill -> "熟悉 " + skill).toList();
        String summary = text.replaceAll("\\s+", " ").trim();
        if (summary.length() > 100) {
            summary = summary.substring(0, 100) + "…";
        }
        ParsedResume parsed = new ParsedResume(name, email, phone, years, education,
                skills, List.of(), List.of(), highlights, summary);
        return JsonUtils.toJson(parsed);
    }

    /** 取出 prompt 中 <resume> 包裹的原文；没有标签时退化为整段文本。 */
    private String extractResumeText(String prompt) {
        int start = prompt.indexOf("<resume>");
        int end = prompt.indexOf("</resume>");
        if (start >= 0 && end > start) {
            return prompt.substring(start + "<resume>".length(), end).trim();
        }
        return prompt.trim();
    }

    private String extractName(String text) {
        Matcher matcher = Pattern.compile("^\\s*([\\u4e00-\\u9fa5]{2,4})(?=[，,、\\s]|$)").matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        Matcher fallback = Pattern.compile("([\\u4e00-\\u9fa5]{2,3})(?=[，,])").matcher(text);
        return fallback.find() ? fallback.group(1) : "候选人";
    }

    private Integer extractYears(String text) {
        Matcher matcher = YEARS_PATTERN.matcher(text);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value > 0 && value <= 40) {
                return value;
            }
        }
        return null;
    }

    private List<String> extractSkills(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> result = new LinkedHashSet<>();
        for (String skill : SKILL_KEYWORDS) {
            String lowerSkill = skill.toLowerCase(Locale.ROOT);
            if (!lower.contains(lowerSkill)) {
                continue;
            }
            boolean coveredByLonger = result.stream()
                    .anyMatch(existing -> existing.toLowerCase(Locale.ROOT).contains(lowerSkill));
            if (!coveredByLonger) {
                result.add(skill);
            }
        }
        return new ArrayList<>(result);
    }

    private String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private String questionsJson(String prompt) {
        int count = 5;
        Matcher matcher = COUNT_PATTERN.matcher(prompt);
        if (matcher.find()) {
            count = Math.min(10, Math.max(1, Integer.parseInt(matcher.group(1))));
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            String template = QUESTION_TEMPLATES.get(i % QUESTION_TEMPLATES.size());
            if (i > 0) {
                builder.append(',');
            }
            builder.append("""
                    {"content":"%s","category":"JAVA","difficulty":%d,"referenceAnswer":"%s","keywords":["原理","场景","实践"]}
                    """.formatted(escape(template), 2 + (i % 3),
                    escape("需要说明核心原理、适用场景，并结合项目经验给出实践细节。")));
        }
        builder.append(']');
        return builder.toString();
    }

    private String scoreJson(String prompt) {
        String answer = prompt.contains("【候选人回答】")
                ? prompt.substring(prompt.indexOf("【候选人回答】") + "【候选人回答】".length())
                : "";
        int length = answer.replaceAll("\\s", "").length();
        double score = Math.min(94, 50 + Math.min(40, length / 6.0));
        if (answer.contains("（候选人未作答）")) {
            score = 0;
        }
        if (prompt.contains("索引") && answer.contains("B+")) {
            score = Math.min(96, score + 8);
        }
        if (prompt.contains("缓存") && (answer.contains("Redis") || answer.contains("过期"))) {
            score = Math.min(96, score + 6);
        }
        return """
                {"score":%.0f,
                 "dimensions":{"accuracy":%.0f,"depth":%.0f,"expression":%.0f,"structure":%.0f},
                 "comment":"回答%s，建议补充原理细节与量化结果。",
                 "hits":["覆盖了核心概念"],"misses":["缺少边界场景分析"]}
                """.formatted(score, score, Math.max(0, score - 5), Math.min(100, score + 3),
                Math.max(0, score - 2), length > 120 ? "较完整" : "偏简短");
    }

    private String reportJson() {
        return """
                {"overallScore":76,
                 "dimensionScores":{"accuracy":78,"depth":72,"expression":80,"structure":74},
                 "strengths":["技术基础扎实，能准确说出核心概念","项目描述有条理，能讲清职责与结果"],
                 "weaknesses":["原理深度不足，部分问题停留在使用层面","缺少量化指标与失败案例复盘"],
                 "suggestion":"HOLD",
                 "summary":"整体达到中级 Java 工程师水平，建议二面重点考察分布式与性能优化深度。"}
                """;
    }

    private String matchJson() {
        return """
                {"score":82,
                 "matchedSkills":["Java","Spring Boot","MySQL","Redis"],
                 "missingSkills":["Kubernetes","消息中间件深度"],
                 "advice":"技术栈匹配度较高，建议补充容器化与高可用相关经验。"}
                """;
    }

    /**
     * 练习提示：根据题干关键词返回一句与题目相关的思考方向（不含完整答案）。
     */
    private String hintText(String prompt) {
        if (prompt.contains("缓存") || prompt.contains("Redis")) {
            return "先区分穿透、击穿、雪崩三类问题，再说各自的应对手段。";
        }
        if (prompt.contains("索引") || prompt.contains("MySQL") || prompt.contains("SQL")) {
            return "先讲索引的数据结构，再解释它为什么适合范围查询。";
        }
        if (prompt.contains("HashMap") || prompt.contains("JVM")) {
            return "先给出结论，再从底层结构、版本变化和适用场景展开。";
        }
        if (prompt.contains("线程池") || prompt.contains("并发") || prompt.contains("volatile")) {
            return "先梳理核心概念与参数，再按执行顺序说明处理流程。";
        }
        if (prompt.contains("项目")) {
            return "用 STAR 结构组织：背景、目标、你的动作、量化结果。";
        }
        if (prompt.contains("团队") || prompt.contains("分歧") || prompt.contains("规划")) {
            return "先讲你如何处理，再用一个真实例子佐证你的说法。";
        }
        return "先给结论，再从原理、场景、取舍三个角度展开。";
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 3);
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public String modelName() {
        return "mock-interviewer-v1";
    }
}
