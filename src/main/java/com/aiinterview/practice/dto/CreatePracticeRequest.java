package com.aiinterview.practice.dto;

import com.aiinterview.interview.enums.InterviewMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 创建练习请求。
 *
 * @param targetId       练习方向 id，必填
 * @param resumeId       简历 id，可选；传入时只允许使用本人已解析的简历
 * @param mode           练习模式，必填
 * @param totalQuestions 题数，可选；为空时取练习方向的默认题数，范围 3-15
 */
public record CreatePracticeRequest(
        @NotNull(message = "练习方向不能为空") Long targetId,
        Long resumeId,
        @NotNull(message = "练习模式不能为空") InterviewMode mode,
        @Min(value = 3, message = "题数不能少于 3 道") @Max(value = 15, message = "题数不能多于 15 道") Integer totalQuestions) {
}
