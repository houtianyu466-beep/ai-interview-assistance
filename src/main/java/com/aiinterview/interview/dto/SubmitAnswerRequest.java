package com.aiinterview.interview.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交单题答案请求。
 *
 * @param questionId      题目 id，必填
 * @param answer          回答内容，必填，最长 10000 字
 * @param thinkingSeconds 思考/作答耗时（秒），可为空
 */
public record SubmitAnswerRequest(
        @NotNull(message = "题目 id 不能为空") Long questionId,
        @NotBlank(message = "回答内容不能为空") @Size(max = 10000, message = "回答内容不能超过 10000 字") String answer,
        @Min(value = 0, message = "作答耗时不能为负数") Integer thinkingSeconds) {
}
