package com.aiinterview.interview.service;

import com.aiinterview.ai.AiService;
import com.aiinterview.ai.model.InterviewReportDraft;
import com.aiinterview.ai.model.QaPair;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.JsonUtils;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.dto.QuestionReviewVO;
import com.aiinterview.interview.dto.ReportVO;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.entity.InterviewQuestion;
import com.aiinterview.interview.entity.InterviewReport;
import com.aiinterview.interview.enums.InterviewStatus;
import com.aiinterview.interview.enums.Recommendation;
import com.aiinterview.interview.mapper.InterviewMapper;
import com.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.aiinterview.interview.mapper.InterviewReportMapper;
import com.aiinterview.job.entity.JobPosition;
import com.aiinterview.job.mapper.JobPositionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 面试报告服务：维度/整体分计算、AI 总评生成、报告落库与查询。
 *
 * <p>分数口径由服务端固定计算，AI 只负责文字性的优势、短板、建议与总评；
 * AI 不可用时全部走规则降级，保证报告始终可生成。</p>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** 参与统计的维度，顺序与 AI Prompt 一致 */
    private static final List<String> DIMENSIONS = List.of("accuracy", "depth", "expression", "structure");

    private final InterviewReportMapper reportMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMapper interviewMapper;
    private final JobPositionMapper jobPositionMapper;
    private final AiService aiService;

    /**
     * 构造器注入。
     *
     * @param reportMapper       报告 Mapper
     * @param questionMapper     题目 Mapper
     * @param interviewMapper    面试 Mapper
     * @param jobPositionMapper  岗位 Mapper，用于补充岗位标题
     * @param aiService          AI 能力门面
     */
    public ReportService(InterviewReportMapper reportMapper,
                         InterviewQuestionMapper questionMapper,
                         InterviewMapper interviewMapper,
                         JobPositionMapper jobPositionMapper,
                         AiService aiService) {
        this.reportMapper = reportMapper;
        this.questionMapper = questionMapper;
        this.interviewMapper = interviewMapper;
        this.jobPositionMapper = jobPositionMapper;
        this.aiService = aiService;
    }

    /**
     * 生成（或覆盖）面试报告。
     *
     * @param interview 面试会话
     * @param questions 该场面试的全部题目（可包含未作答题目）
     * @return 报告视图
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportVO generate(Interview interview, List<InterviewQuestion> questions) {
        List<InterviewQuestion> ordered = sortedQuestions(questions);
        double overallScore = averageScore(ordered);
        Map<String, Double> dimensionScores = averageDimensions(ordered);
        String positionTitle = positionTitle(interview.getPositionId());

        Recommendation suggestion;
        List<String> strengths;
        List<String> weaknesses;
        String summary;
        try {
            List<QaPair> qaPairs = ordered.stream()
                    .map(question -> new QaPair(
                            question.getSeq() == null ? 0 : question.getSeq(),
                            question.getContent(),
                            question.getAnswer(),
                            question.getScore() == null ? null : question.getScore().doubleValue(),
                            question.getComment()))
                    .toList();
            InterviewReportDraft draft = aiService.generateReport(positionTitle, qaPairs, overallScore, dimensionScores);
            strengths = normalizeList(draft.strengths());
            weaknesses = normalizeList(draft.weaknesses());
            suggestion = parseSuggestion(draft.suggestion());
            if (suggestion == null) {
                suggestion = suggestionByScore(overallScore);
            }
            summary = TextUtils.isBlank(draft.summary()) ? defaultSummary(overallScore, dimensionScores) : draft.summary().trim();
        } catch (BizException e) {
            log.warn("AI 报告生成失败，启用规则降级 interviewId={} reason={}", interview.getId(), e.getMessage());
            strengths = deriveStrengths(ordered);
            weaknesses = deriveWeaknesses(ordered);
            suggestion = suggestionByScore(overallScore);
            summary = defaultSummary(overallScore, dimensionScores);
        }
        if (strengths.isEmpty()) {
            strengths = deriveStrengths(ordered);
        }
        if (weaknesses.isEmpty()) {
            weaknesses = deriveWeaknesses(ordered);
        }

        InterviewReport report = reportMapper.selectOne(Wrappers.<InterviewReport>lambdaQuery()
                .eq(InterviewReport::getInterviewId, interview.getId()));
        boolean insert = report == null;
        if (insert) {
            report = new InterviewReport();
        }
        report.setInterviewId(interview.getId());
        report.setOverallScore(BigDecimal.valueOf(overallScore));
        report.setDimensionScores(JsonUtils.toJson(dimensionScores));
        report.setStrengths(JsonUtils.toJson(strengths));
        report.setWeaknesses(JsonUtils.toJson(weaknesses));
        report.setSuggestion(suggestion);
        report.setSummary(summary);
        if (insert) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }
        return ReportVO.from(report, ordered.stream().map(QuestionReviewVO::from).toList());
    }

    /**
     * 按面试 id 查询报告。
     *
     * @param interviewId 面试 id
     * @return 报告视图
     * @throws BizException 报告不存在时抛出 404
     */
    public ReportVO getByInterviewId(Long interviewId) {
        InterviewReport report = reportMapper.selectOne(Wrappers.<InterviewReport>lambdaQuery()
                .eq(InterviewReport::getInterviewId, interviewId));
        if (report == null) {
            throw BizException.notFound("面试报告不存在");
        }
        return ReportVO.from(report, listQuestions(interviewId).stream().map(QuestionReviewVO::from).toList());
    }

    /**
     * 重新生成报告：仅已结束的面试允许。
     *
     * @param interviewId 面试 id
     * @return 新的报告视图
     * @throws BizException 面试不存在（404）或未结束（409）
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportVO regenerate(Long interviewId) {
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) {
            throw BizException.notFound("面试不存在");
        }
        if (interview.getStatus() != InterviewStatus.FINISHED) {
            throw BizException.conflict("仅已结束的面试可以重新生成报告");
        }
        return generate(interview, listQuestions(interviewId));
    }

    private List<InterviewQuestion> sortedQuestions(List<InterviewQuestion> questions) {
        List<InterviewQuestion> ordered = new ArrayList<>(questions == null ? List.of() : questions);
        ordered.sort(Comparator.comparing(InterviewQuestion::getSeq,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return ordered;
    }

    private List<InterviewQuestion> listQuestions(Long interviewId) {
        return questionMapper.selectList(Wrappers.<InterviewQuestion>lambdaQuery()
                .eq(InterviewQuestion::getInterviewId, interviewId)
                .orderByAsc(InterviewQuestion::getSeq));
    }

    /**
     * 已评分题目的平均分；无评分返回 0。
     */
    private static double averageScore(List<InterviewQuestion> questions) {
        double sum = 0;
        int count = 0;
        for (InterviewQuestion question : questions) {
            if (question.getScore() != null) {
                sum += question.getScore().doubleValue();
                count++;
            }
        }
        return count == 0 ? 0.0 : round1(sum / count);
    }

    /**
     * 逐题解析 dimensionScores JSON，对四个维度求平均，缺失维度忽略。
     */
    private static Map<String, Double> averageDimensions(List<InterviewQuestion> questions) {
        Map<String, Double> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (InterviewQuestion question : questions) {
            Map<String, Object> raw = JsonUtils.toMap(question.getDimensionScores());
            if (raw.isEmpty()) {
                continue;
            }
            for (String dimension : DIMENSIONS) {
                Double value = toDouble(raw.get(dimension));
                if (value != null) {
                    sums.merge(dimension, value, Double::sum);
                    counts.merge(dimension, 1, Integer::sum);
                }
            }
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (String dimension : DIMENSIONS) {
            Integer count = counts.get(dimension);
            if (count != null && count > 0) {
                result.put(dimension, round1(sums.get(dimension) / count));
            }
        }
        return result;
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String positionTitle(Long positionId) {
        if (positionId == null) {
            return "";
        }
        JobPosition position = jobPositionMapper.selectById(positionId);
        return position == null || position.getTitle() == null ? "" : position.getTitle();
    }

    private static Recommendation parseSuggestion(String raw) {
        if (TextUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Recommendation.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Recommendation suggestionByScore(double score) {
        if (score >= 80) {
            return Recommendation.PASS;
        }
        if (score >= 60) {
            return Recommendation.HOLD;
        }
        return Recommendation.REJECT;
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> !TextUtils.isBlank(value))
                .map(String::trim)
                .distinct()
                .limit(4)
                .toList();
    }

    private static List<String> deriveStrengths(List<InterviewQuestion> questions) {
        List<String> result = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            if (question.getScore() == null || question.getScore().doubleValue() < 75) {
                continue;
            }
            result.add(TextUtils.isBlank(question.getComment())
                    ? "第" + question.getSeq() + "题表现较好"
                    : question.getComment().trim());
            if (result.size() >= 4) {
                break;
            }
        }
        return result;
    }

    private static List<String> deriveWeaknesses(List<InterviewQuestion> questions) {
        List<String> result = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            if (question.getScore() == null || question.getScore().doubleValue() >= 60) {
                continue;
            }
            result.add(TextUtils.isBlank(question.getComment())
                    ? "第" + question.getSeq() + "题掌握不足"
                    : question.getComment().trim());
            if (result.size() >= 4) {
                break;
            }
        }
        return result;
    }

    private static String defaultSummary(double overallScore, Map<String, Double> dimensionScores) {
        StringBuilder builder = new StringBuilder("整体得分 ").append(formatNumber(overallScore)).append(" 分");
        if (!dimensionScores.isEmpty()) {
            builder.append("；维度均分：");
            boolean first = true;
            for (Map.Entry<String, Double> entry : dimensionScores.entrySet()) {
                if (!first) {
                    builder.append("、");
                }
                builder.append(dimensionLabel(entry.getKey())).append(" ").append(formatNumber(entry.getValue()));
                first = false;
            }
        }
        builder.append("。");
        return builder.toString();
    }

    private static String dimensionLabel(String dimension) {
        return switch (dimension) {
            case "accuracy" -> "准确性";
            case "depth" -> "深度";
            case "expression" -> "表达";
            case "structure" -> "结构性";
            default -> dimension;
        };
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(round1(value));
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
