package com.aiinterview.interview.service;

import com.aiinterview.common.exception.BizException;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.enums.InterviewStatus;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 面试状态机：集中管理 {@link InterviewStatus} 的合法流转。
 *
 * <p>状态图：</p>
 * <pre>
 * CREATED ──start──▶ IN_PROGRESS ──答完最后一题/finish──▶ FINISHED
 *    │                    │
 *    └──────cancel────────┴──────cancel──────▶ CANCELLED
 * </pre>
 *
 * <p>该类只修改传入实体，不负责持久化，落库由 Service 决定。</p>
 */
public final class InterviewStateMachine {

    private InterviewStateMachine() {
    }

    /**
     * 校验面试可以开始：仅 {@code CREATED} 状态允许。
     *
     * @param interview 面试实体
     * @throws BizException 当前状态不允许开始时抛出 409
     */
    public static void assertCanStart(Interview interview) {
        if (interview.getStatus() != InterviewStatus.CREATED) {
            throw BizException.conflict("当前面试状态为" + label(interview.getStatus()) + "，无法开始");
        }
    }

    /**
     * 校验面试可以进行答题：仅 {@code IN_PROGRESS} 状态允许。
     *
     * @param interview 面试实体
     * @throws BizException 当前状态不允许作答时抛出 409
     */
    public static void assertInProgress(Interview interview) {
        if (interview.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw BizException.conflict("当前面试状态为" + label(interview.getStatus()) + "，无法作答");
        }
    }

    /**
     * 开始面试：{@code CREATED → IN_PROGRESS}，并记录开始时间。
     *
     * @param interview 面试实体
     * @throws BizException 当前状态不允许开始时抛出 409
     */
    public static void markStarted(Interview interview) {
        assertCanStart(interview);
        interview.setStatus(InterviewStatus.IN_PROGRESS);
        if (interview.getStartedAt() == null) {
            interview.setStartedAt(LocalDateTime.now());
        }
    }

    /**
     * 结束面试：写入 {@code FINISHED}、结束时间与耗时。
     *
     * @param interview 面试实体
     */
    public static void markFinished(Interview interview) {
        LocalDateTime now = LocalDateTime.now();
        interview.setStatus(InterviewStatus.FINISHED);
        interview.setFinishedAt(now);
        if (interview.getStartedAt() == null) {
            interview.setDurationSeconds(0);
        } else {
            long seconds = Duration.between(interview.getStartedAt(), now).getSeconds();
            interview.setDurationSeconds((int) Math.max(0, seconds));
        }
    }

    /**
     * 取消面试：{@code CREATED/IN_PROGRESS → CANCELLED}。
     *
     * @param interview 面试实体
     * @throws BizException 已结束或已取消时抛出 409
     */
    public static void markCancelled(Interview interview) {
        InterviewStatus status = interview.getStatus();
        if (status != InterviewStatus.CREATED && status != InterviewStatus.IN_PROGRESS) {
            throw BizException.conflict("当前面试状态为" + label(status) + "，无法取消");
        }
        interview.setStatus(InterviewStatus.CANCELLED);
    }

    /**
     * 判断面试是否已结束。
     *
     * @param interview 面试实体
     * @return 已结束返回 {@code true}
     */
    public static boolean isFinished(Interview interview) {
        return interview.getStatus() == InterviewStatus.FINISHED;
    }

    private static String label(InterviewStatus status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case CREATED -> "已创建";
            case IN_PROGRESS -> "进行中";
            case FINISHED -> "已结束";
            case CANCELLED -> "已取消";
        };
    }
}
