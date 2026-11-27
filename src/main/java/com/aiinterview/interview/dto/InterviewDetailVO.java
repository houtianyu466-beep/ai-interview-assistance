package com.aiinterview.interview.dto;

import java.util.List;

/**
 * 面试详情：会话信息 + 题目概要列表。
 *
 * @param interview 面试会话视图
 * @param questions 题目概要列表（按题号升序）
 */
public record InterviewDetailVO(InterviewVO interview, List<QuestionBriefVO> questions) {
}
