package com.aiinterview.interview.enums;

/**
 * 面试状态机：
 *
 * <pre>
 * CREATED ──start──▶ IN_PROGRESS ──答完最后一题/finish──▶ FINISHED
 *    │                    │
 *    └──────cancel────────┴──────cancel──────▶ CANCELLED
 * </pre>
 *
 * 状态流转集中在 {@code InterviewStateMachine}，Service 中不允许随意 setStatus。
 */
public enum InterviewStatus {
    /** 已创建，题目已生成，等待开始 */
    CREATED,
    /** 进行中 */
    IN_PROGRESS,
    /** 已结束，报告已生成 */
    FINISHED,
    /** 已取消 */
    CANCELLED
}
