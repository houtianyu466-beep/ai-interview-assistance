package com.aiinterview.ai.model;

import java.util.List;

/**
 * 简历与岗位的匹配结果。
 */
public record MatchResult(
        double score,
        List<String> matchedSkills,
        List<String> missingSkills,
        String advice) {

    public MatchResult {
        score = Math.max(0, Math.min(100, score));
        matchedSkills = matchedSkills == null ? List.of() : List.copyOf(matchedSkills);
        missingSkills = missingSkills == null ? List.of() : List.copyOf(missingSkills);
    }
}
