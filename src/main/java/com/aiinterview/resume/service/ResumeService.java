package com.aiinterview.resume.service;

import com.aiinterview.ai.AiService;
import com.aiinterview.ai.model.MatchResult;
import com.aiinterview.ai.model.ParsedResume;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.infrastructure.storage.FileStorageService;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.mapper.InterviewMapper;
import com.aiinterview.job.entity.JobPosition;
import com.aiinterview.job.mapper.JobPositionMapper;
import com.aiinterview.resume.dto.MatchVO;
import com.aiinterview.resume.dto.ResumeDetailVO;
import com.aiinterview.resume.dto.ResumeTextRequest;
import com.aiinterview.resume.dto.ResumeVO;
import com.aiinterview.resume.entity.Resume;
import com.aiinterview.resume.enums.ResumeStatus;
import com.aiinterview.resume.mapper.ResumeMapper;
import com.aiinterview.security.SecurityUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 简历业务服务：上传、文本录入、查询、删除与岗位匹配。
 *
 * <p>上传/录入只落库并触发异步解析，解析结果由 {@link ResumeParseService} 回写；
 * 对外返回的 {@link ResumeVO} 反映调用瞬间的状态（通常为 UPLOADED）。</p>
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
    private static final String MANUAL_FILE_NAME = "手动输入简历";
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_RAW_TEXT_LENGTH = 20000;

    private final ResumeMapper resumeMapper;
    private final InterviewMapper interviewMapper;
    private final JobPositionMapper jobPositionMapper;
    private final FileStorageService storage;
    private final AiService aiService;
    private final ResumeParseService resumeParseService;

    public ResumeService(ResumeMapper resumeMapper, InterviewMapper interviewMapper,
                         JobPositionMapper jobPositionMapper, FileStorageService storage,
                         AiService aiService, ResumeParseService resumeParseService) {
        this.resumeMapper = resumeMapper;
        this.interviewMapper = interviewMapper;
        this.jobPositionMapper = jobPositionMapper;
        this.storage = storage;
        this.aiService = aiService;
        this.resumeParseService = resumeParseService;
    }

    /**
     * 上传简历文件：存储 → 落库（UPLOADED）→ 触发异步解析。
     *
     * @param file 上传的 txt / md / docx 文件
     * @return 简历摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public ResumeVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("上传文件不能为空");
        }
        Long userId = SecurityUtils.currentUserId();
        String storedPath = storage.store(file, "resume");

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(cut(TextUtils.defaultIfBlank(file.getOriginalFilename(), "未命名简历"),
                MAX_FILE_NAME_LENGTH));
        resume.setFilePath(storedPath);
        resume.setStatus(ResumeStatus.UPLOADED);
        resumeMapper.insert(resume);

        triggerAsyncParse(resume.getId());
        return ResumeVO.from(resume);
    }

    /**
     * 手工录入简历文本：以文本建简历并触发异步解析。
     *
     * @param request 简历文本请求
     * @return 简历摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public ResumeVO submitText(ResumeTextRequest request) {
        String content = request == null ? null : request.content();
        if (TextUtils.isBlank(content)) {
            throw BizException.badRequest("简历内容为空");
        }
        Long userId = SecurityUtils.currentUserId();

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(MANUAL_FILE_NAME);
        resume.setRawText(cut(content, MAX_RAW_TEXT_LENGTH));
        resume.setStatus(ResumeStatus.UPLOADED);
        resumeMapper.insert(resume);

        triggerAsyncParse(resume.getId());
        return ResumeVO.from(resume);
    }

    /**
     * 查询当前用户的全部简历，按创建时间倒序。
     *
     * @return 简历摘要列表
     */
    public List<ResumeVO> myResumes() {
        Long userId = SecurityUtils.currentUserId();
        List<Resume> resumes = resumeMapper.selectList(Wrappers.<Resume>lambdaQuery()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getCreatedAt));
        return resumes.stream().map(ResumeVO::from).toList();
    }

    /**
     * 简历详情：本人或管理员可查看。
     *
     * @param id 简历 ID
     * @return 详情（含原文与结构化解析结果）
     */
    public ResumeDetailVO detail(Long id) {
        Resume resume = requireResume(id);
        SecurityUtils.requireOwner(resume.getUserId());
        ParsedResume parsed = parseParsedJson(resume.getParsedJson());
        return new ResumeDetailVO(ResumeVO.from(resume), resume.getRawText(), parsed);
    }

    /**
     * 删除简历：存在面试引用时拒绝；否则同时删除文件与记录。
     *
     * @param id 简历 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Resume resume = requireResume(id);
        SecurityUtils.requireOwner(resume.getUserId());

        Long references = interviewMapper.selectCount(Wrappers.<Interview>lambdaQuery()
                .eq(Interview::getResumeId, id));
        if (references != null && references > 0) {
            throw BizException.conflict("该简历已被面试引用，无法删除");
        }
        if (!TextUtils.isBlank(resume.getFilePath())) {
            storage.delete(resume.getFilePath());
        }
        resumeMapper.deleteById(id);
    }

    /**
     * 简历与岗位匹配：优先调用 AI，失败时按技能关键词规则降级。
     *
     * @param resumeId   简历 ID
     * @param positionId 岗位 ID
     * @return 匹配结果
     */
    public MatchVO match(Long resumeId, Long positionId) {
        if (positionId == null) {
            throw BizException.badRequest("岗位 ID 不能为空");
        }
        Resume resume = requireResume(resumeId);
        SecurityUtils.requireOwner(resume.getUserId());
        if (resume.getStatus() != ResumeStatus.PARSED) {
            throw BizException.conflict("简历尚未解析完成");
        }
        JobPosition position = jobPositionMapper.selectById(positionId);
        if (position == null) {
            throw BizException.notFound("岗位不存在");
        }
        try {
            MatchResult result = aiService.matchResume(buildResumeText(resume),
                    position.getTitle(), position.getRequirements());
            return MatchVO.from(result);
        } catch (Exception e) {
            log.warn("AI 简历匹配失败，启用规则降级 resumeId={} positionId={} 原因={}",
                    resumeId, positionId, e.getMessage());
            return fallbackMatch(resume, position);
        }
    }

    /**
     * 获取已解析完成的简历实体，供面试模块创建面试时校验。
     *
     * @param id 简历 ID
     * @return 简历实体
     */
    public Resume requireParsedResume(Long id) {
        Resume resume = requireResume(id);
        if (resume.getStatus() != ResumeStatus.PARSED) {
            throw BizException.conflict("简历尚未解析完成");
        }
        return resume;
    }

    /**
     * 触发异步解析：事务内注册 afterCommit，避免异步线程读不到未提交的记录。
     * 触发失败只记录日志，不影响上传结果。
     */
    private void triggerAsyncParse(Long resumeId) {
        Runnable task = () -> {
            try {
                resumeParseService.parseAsync(resumeId);
            } catch (Exception e) {
                log.warn("触发简历异步解析失败 resumeId={}", resumeId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * 规则匹配：简历技能与岗位要求技能的交集占比，分数夹在 40-95。
     */
    private MatchVO fallbackMatch(Resume resume, JobPosition position) {
        List<String> resumeSkills = resolveResumeSkills(resume);
        String requirements = TextUtils.defaultIfBlank(position.getRequirements(), "");
        List<String> jobSkills = new ArrayList<>(resumeParseService.extractSkills(requirements));
        if (jobSkills.isEmpty() && !requirements.isBlank()) {
            for (String skill : resumeSkills) {
                if (containsIgnoreCase(requirements, skill)) {
                    jobSkills.add(skill);
                }
            }
        }
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String jobSkill : jobSkills) {
            boolean hit = resumeSkills.stream().anyMatch(skill -> skillMatches(skill, jobSkill));
            if (hit) {
                matched.add(jobSkill);
            } else {
                missing.add(jobSkill);
            }
        }
        double base = jobSkills.isEmpty() ? 60.0 : matched.size() * 100.0 / jobSkills.size();
        double score = Math.max(40.0, Math.min(95.0, Math.round(base * 10.0) / 10.0));
        String advice = "AI 匹配服务暂时不可用，已基于技能关键词给出粗略匹配结果，建议人工复核。";
        return new MatchVO(score, matched, missing, advice);
    }

    /**
     * 解析简历技能：优先使用 skills 字段，其次 parsedJson，最后从原文按词表提取。
     */
    private List<String> resolveResumeSkills(Resume resume) {
        List<String> skills = TextUtils.splitCsv(resume.getSkills());
        if (!skills.isEmpty()) {
            return skills;
        }
        ParsedResume parsed = parseParsedJson(resume.getParsedJson());
        if (parsed != null && !parsed.skills().isEmpty()) {
            return parsed.skills();
        }
        return resumeParseService.extractSkills(resume.getRawText());
    }

    /**
     * 构造 AI 匹配用的简历文本：原文优先，没有原文时用技能与摘要拼装。
     */
    private String buildResumeText(Resume resume) {
        if (!TextUtils.isBlank(resume.getRawText())) {
            return resume.getRawText();
        }
        StringBuilder text = new StringBuilder();
        if (!TextUtils.isBlank(resume.getSkills())) {
            text.append("技能：").append(resume.getSkills()).append('\n');
        }
        ParsedResume parsed = parseParsedJson(resume.getParsedJson());
        if (parsed != null) {
            if (!TextUtils.isBlank(parsed.summary())) {
                text.append(parsed.summary()).append('\n');
            }
            if (!TextUtils.isBlank(parsed.education())) {
                text.append("学历：").append(parsed.education()).append('\n');
            }
        }
        return text.toString();
    }

    /**
     * 反序列化 parsedJson，失败时返回 null 并记录日志（不阻断详情展示）。
     */
    private ParsedResume parseParsedJson(String parsedJson) {
        if (TextUtils.isBlank(parsedJson)) {
            return null;
        }
        try {
            return JsonUtils.fromJson(parsedJson, ParsedResume.class);
        } catch (Exception e) {
            log.warn("简历 parsedJson 反序列化失败", e);
            return null;
        }
    }

    /**
     * 按 ID 取简历，不存在抛 404。
     */
    private Resume requireResume(Long id) {
        if (id == null) {
            throw BizException.badRequest("简历 ID 不能为空");
        }
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw BizException.notFound("简历不存在");
        }
        return resume;
    }

    /**
     * 技能模糊匹配：忽略大小写与空白后互相包含即视为匹配。
     */
    private boolean skillMatches(String resumeSkill, String jobSkill) {
        String left = normalizeSkill(resumeSkill);
        String right = normalizeSkill(jobSkill);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        return left.contains(right) || right.contains(left);
    }

    /**
     * 技能名归一化：去空白并转小写。
     */
    private String normalizeSkill(String skill) {
        return skill == null ? "" : skill.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * 忽略大小写的包含判断。
     */
    private boolean containsIgnoreCase(String text, String keyword) {
        if (TextUtils.isBlank(text) || TextUtils.isBlank(keyword)) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
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
