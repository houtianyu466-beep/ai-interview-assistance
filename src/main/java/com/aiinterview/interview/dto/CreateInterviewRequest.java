package com.aiinterview.interview.dto;

import com.aiinterview.interview.enums.InterviewMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 创建面试请求。
 *
 * @param positionId     应聘岗位 id，必填且岗位必须处于 OPEN 状态
 * @param resumeId       用于出题的简历 id，可为空（为空时使用通用题）
 * @param mode           面试模式，必填
 * @param totalQuestions 本次面试题数，为空时默认 5；取值范围 3-15
 */
public record CreateInterviewRequest(
        @NotNull(message = "岗位不能为空") Long positionId,
        Long resumeId,
        @NotNull(message = "面试模式不能为空") InterviewMode mode,
        @Min(value = 3, message = "题数不能少于 3") @Max(value = 15, message = "题数不能超过 15") Integer totalQuestions) {
}
