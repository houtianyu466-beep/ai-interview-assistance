package com.aiinterview.practice.dto;

import com.aiinterview.interview.dto.CurrentQuestionVO;

/**
 * 跳过当前题的返回结果。
 *
 * @param finished     跳过的是否是最后一题（跳过即结束整场练习）
 * @param nextQuestion 下一题，练习结束时为空
 * @param reportId     练习报告 id，仅在 finished=true 时有值
 */
public record PracticeSkipResult(
        boolean finished,
        CurrentQuestionVO nextQuestion,
        Long reportId) {
}
