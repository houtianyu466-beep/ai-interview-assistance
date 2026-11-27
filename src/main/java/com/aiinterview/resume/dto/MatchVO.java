package com.aiinterview.resume.dto;

import com.aiinterview.ai.model.MatchResult;

import java.util.List;

/**
 * 简历与岗位的匹配结果视图。
 *
 * @param score         匹配分，0-100
 * @param matchedSkills 已匹配的技能
 * @param missingSkills 岗位要求但简历缺失的技能
 * @param advice        改进建议
 */
public record MatchVO(double score, List<String> matchedSkills, List<String> missingSkills, String advice) {

    public MatchVO {
        matchedSkills = matchedSkills == null ? List.of() : List.copyOf(matchedSkills);
        missingSkills = missingSkills == null ? List.of() : List.copyOf(missingSkills);
    }

    /**
     * 由 AI 匹配结果转换视图。
     *
     * @param result AI 匹配结果，可为 null
     * @return 匹配视图；AI 未返回结果时给出一条可读提示
     */
    public static MatchVO from(MatchResult result) {
        if (result == null) {
            return new MatchVO(0, List.of(), List.of(), "AI 未返回匹配结果，请稍后重试");
        }
        return new MatchVO(result.score(), result.matchedSkills(), result.missingSkills(), result.advice());
    }
}
