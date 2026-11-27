package com.aiinterview.resume.service;

import com.aiinterview.ai.AiService;
import com.aiinterview.ai.model.ParsedResume;
import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.infrastructure.storage.FileStorageService;
import com.aiinterview.resume.entity.Resume;
import com.aiinterview.resume.enums.ResumeStatus;
import com.aiinterview.resume.mapper.ResumeMapper;
import com.aiinterview.resume.parser.ResumeTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历解析服务：把简历文本交给 AI 结构化，失败时走规则降级。
 *
 * <p>异步解析在独立线程池中执行，所有异常都必须在此处消化并落库为 FAILED，
 * 否则异步线程的异常无法回传给调用方。</p>
 */
@Service
public class ResumeParseService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParseService.class);

    /** 规则降级使用的技能词表，命中即作为技能标签 */
    private static final List<String> SKILL_KEYWORDS = List.of(
            "Java", "Spring", "Spring Boot", "Spring Cloud", "MyBatis", "MySQL", "Oracle",
            "Redis", "MongoDB", "Kafka", "RabbitMQ", "RocketMQ", "Docker", "Kubernetes",
            "K8s", "Linux", "Nginx", "Elasticsearch", "微服务", "分布式", "高并发", "JVM",
            "Python", "Go");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern EDUCATION_PATTERN = Pattern.compile("(博士|硕士|本科|大专|专科)");
    private static final Pattern YEARS_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*年");

    private static final String FALLBACK_SUMMARY = "AI 解析不可用，已使用规则提取";
    private static final int MAX_ERROR_MESSAGE = 500;
    private static final int MAX_SKILLS_LENGTH = 1024;
    private static final int MAX_EDUCATION_LENGTH = 64;
    private static final int MAX_YEARS = 50;

    private final ResumeMapper resumeMapper;
    private final FileStorageService storage;
    private final ResumeTextExtractor textExtractor;
    private final AiService aiService;

    public ResumeParseService(ResumeMapper resumeMapper, FileStorageService storage,
                              ResumeTextExtractor textExtractor, AiService aiService) {
        this.resumeMapper = resumeMapper;
        this.storage = storage;
        this.textExtractor = textExtractor;
        this.aiService = aiService;
    }

    /**
     * 异步解析简历：PARSING → 抽取文本 → AI 解析（失败降级）→ PARSED。
     *
     * <p>必须由其他 Bean 调用才能走 Spring 代理；本方法内部吞掉所有异常，
     * 失败时把简历状态更新为 FAILED 并记录错误原因。</p>
     *
     * @param resumeId 简历 ID
     */
    @Async("taskExecutor")
    public void parseAsync(Long resumeId) {
        if (resumeId == null) {
            log.warn("简历异步解析跳过：resumeId 为空");
            return;
        }
        try {
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume == null) {
                log.warn("简历异步解析跳过：简历不存在 resumeId={}", resumeId);
                return;
            }
            updateStatus(resumeId, ResumeStatus.PARSING);
            String rawText = resolveRawText(resume);
            if (TextUtils.isBlank(rawText)) {
                throw BizException.badRequest("简历内容为空");
            }
            saveRawText(resumeId, rawText);
            ParsedResume parsed = parseWithFallback(resumeId, rawText);
            saveParsedResult(resumeId, parsed);
            log.info("简历解析完成 resumeId={} 技能数={}", resumeId, parsed.skills().size());
        } catch (Exception e) {
            log.error("简历解析失败 resumeId={}", resumeId, e);
            markFailed(resumeId, e);
        }
    }

    /**
     * 同步解析简历（供测试与其他业务复用），AI 不可用时自动走规则降级。
     *
     * @param resume 简历实体
     * @return 结构化简历
     * @throws BizException 简历为空、文件不存在或文本为空时抛出
     */
    public ParsedResume parse(Resume resume) {
        if (resume == null) {
            throw BizException.notFound("简历不存在");
        }
        String rawText = resolveRawText(resume);
        if (TextUtils.isBlank(rawText)) {
            throw BizException.badRequest("简历内容为空");
        }
        return parseWithFallback(resume.getId(), rawText);
    }

    /**
     * 规则降级解析：正则提取邮箱、手机号、学历、工作年限，并用技能词表命中技能。
     *
     * @param rawText 简历纯文本
     * @return 结构化简历，summary 标记为规则提取
     * @throws BizException 文本为空时抛出
     */
    public ParsedResume fallbackParse(String rawText) {
        if (TextUtils.isBlank(rawText)) {
            throw BizException.badRequest("简历内容为空，无法进行规则解析");
        }
        String email = firstMatch(EMAIL_PATTERN, rawText);
        String phone = firstMatch(PHONE_PATTERN, rawText);
        String education = firstMatch(EDUCATION_PATTERN, rawText);
        Integer yearsOfExperience = extractYears(rawText);
        List<String> skills = extractSkills(rawText);
        return new ParsedResume(null, email, phone, yearsOfExperience, education,
                skills, List.of(), List.of(), List.of(), FALLBACK_SUMMARY);
    }

    /**
     * 从文本中按技能词表提取技能，英文关键词按词边界匹配（避免 Go 命中 MongoDB）。
     *
     * @param text 待匹配文本
     * @return 命中的技能，按词表顺序去重
     */
    public List<String> extractSkills(String text) {
        if (TextUtils.isBlank(text)) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> skills = new LinkedHashSet<>();
        for (String keyword : SKILL_KEYWORDS) {
            String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
            if (containsKeyword(lower, lowerKeyword)) {
                skills.add(keyword);
            }
        }
        return new ArrayList<>(skills);
    }

    /**
     * 先尝试 AI 解析，异常时降级为规则提取。
     */
    private ParsedResume parseWithFallback(Long resumeId, String rawText) {
        try {
            ParsedResume parsed = aiService.parseResume(rawText);
            if (parsed == null) {
                throw new BizException(ErrorCode.AI_ERROR, "AI 未返回简历解析结果");
            }
            return parsed;
        } catch (Exception e) {
            log.warn("AI 简历解析不可用，启用规则降级 resumeId={} 原因={}", resumeId, e.getMessage());
            return fallbackParse(rawText);
        }
    }

    /**
     * 取简历文本：优先使用已保存的 rawText，否则从文件重新抽取。
     */
    private String resolveRawText(Resume resume) {
        if (!TextUtils.isBlank(resume.getRawText())) {
            return resume.getRawText();
        }
        if (TextUtils.isBlank(resume.getFilePath())) {
            return "";
        }
        Path path = storage.resolve(resume.getFilePath());
        if (!Files.exists(path)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "简历文件不存在或已被删除");
        }
        String fileName = TextUtils.isBlank(resume.getFileName()) ? resume.getFilePath() : resume.getFileName();
        return textExtractor.extract(path, fileName);
    }

    /**
     * 状态流转：只更新 status 字段。
     */
    private void updateStatus(Long resumeId, ResumeStatus status) {
        Resume patch = new Resume();
        patch.setId(resumeId);
        patch.setStatus(status);
        resumeMapper.updateById(patch);
    }

    /**
     * 把抽取出的文本回写数据库，便于详情页展示。
     */
    private void saveRawText(Long resumeId, String rawText) {
        Resume patch = new Resume();
        patch.setId(resumeId);
        patch.setRawText(rawText);
        resumeMapper.updateById(patch);
    }

    /**
     * 写入解析结果并置为 PARSED。
     */
    private void saveParsedResult(Long resumeId, ParsedResume parsed) {
        List<String> skills = parsed.skills().stream()
                .filter(skill -> !TextUtils.isBlank(skill))
                .map(String::trim)
                .distinct()
                .toList();
        Resume patch = new Resume();
        patch.setId(resumeId);
        patch.setParsedJson(JsonUtils.toJson(parsed));
        patch.setSkills(cut(TextUtils.joinCsv(skills), MAX_SKILLS_LENGTH));
        patch.setYearsOfExperience(parsed.yearsOfExperience());
        patch.setEducation(cut(parsed.education(), MAX_EDUCATION_LENGTH));
        patch.setStatus(ResumeStatus.PARSED);
        resumeMapper.updateById(patch);
    }

    /**
     * 解析失败时落库 FAILED 与错误信息（最多 500 字）。
     */
    private void markFailed(Long resumeId, Exception error) {
        String message = error == null ? null : error.getMessage();
        if (TextUtils.isBlank(message)) {
            message = error == null ? "简历解析失败" : error.getClass().getSimpleName();
        }
        try {
            Resume patch = new Resume();
            patch.setId(resumeId);
            patch.setStatus(ResumeStatus.FAILED);
            patch.setErrorMessage(cut(message, MAX_ERROR_MESSAGE));
            resumeMapper.updateById(patch);
        } catch (Exception e) {
            log.error("更新简历失败状态时发生异常 resumeId={}", resumeId, e);
        }
    }

    /**
     * 提取工作年限：取文本中最大的合法「N 年」，排除 4 位年份干扰。
     */
    private Integer extractYears(String text) {
        Matcher matcher = YEARS_PATTERN.matcher(text);
        Integer years = null;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value >= 0 && value <= MAX_YEARS) {
                years = years == null ? value : Math.max(years, value);
            }
        }
        return years;
    }

    /**
     * 是否包含关键词：英文按词边界，中文按子串。
     */
    private boolean containsKeyword(String lowerText, String lowerKeyword) {
        if (!isAscii(lowerKeyword)) {
            return lowerText.contains(lowerKeyword);
        }
        int from = 0;
        while (true) {
            int index = lowerText.indexOf(lowerKeyword, from);
            if (index < 0) {
                return false;
            }
            boolean leftOk = index == 0 || !isWordChar(lowerText.charAt(index - 1));
            int end = index + lowerKeyword.length();
            boolean rightOk = end >= lowerText.length() || !isWordChar(lowerText.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = index + 1;
        }
    }

    /**
     * 是否全为 ASCII 字符。
     */
    private boolean isAscii(String text) {
        return text.chars().allMatch(c -> c < 128);
    }

    /**
     * 词边界字符：字母、数字以及 C++/C# 这类技术词后缀。
     */
    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '+' || c == '#';
    }

    /**
     * 正则取第一个匹配分组。
     */
    private String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group().trim() : null;
    }

    /**
     * 截断字符串到指定长度。
     */
    private String cut(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
