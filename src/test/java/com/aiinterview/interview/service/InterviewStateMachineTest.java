package com.aiinterview.interview.service;

import com.aiinterview.common.exception.BizException;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.enums.InterviewStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 面试状态机单元测试：保证状态流转只有一个入口，非法流转统一抛 409。
 */
class InterviewStateMachineTest {

    private Interview createdInterview() {
        Interview interview = new Interview();
        interview.setStatus(InterviewStatus.CREATED);
        interview.setCurrentIndex(0);
        interview.setTotalQuestions(3);
        return interview;
    }

    @Test
    @DisplayName("CREATED 状态可以开始，并写入开始时间")
    void shouldStartFromCreated() {
        Interview interview = createdInterview();

        InterviewStateMachine.assertCanStart(interview);
        InterviewStateMachine.markStarted(interview);

        assertThat(interview.getStatus()).isEqualTo(InterviewStatus.IN_PROGRESS);
        assertThat(interview.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("重复开始抛 409")
    void shouldRejectStartTwice() {
        Interview interview = createdInterview();
        InterviewStateMachine.markStarted(interview);

        assertThatThrownBy(() -> InterviewStateMachine.assertCanStart(interview))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("状态");
    }

    @Test
    @DisplayName("结束会写入结束时间并计算时长")
    void shouldMarkFinished() {
        Interview interview = createdInterview();
        InterviewStateMachine.markStarted(interview);
        interview.setStartedAt(java.time.LocalDateTime.now().minusMinutes(10));

        InterviewStateMachine.markFinished(interview);

        assertThat(interview.getStatus()).isEqualTo(InterviewStatus.FINISHED);
        assertThat(interview.getFinishedAt()).isNotNull();
        assertThat(interview.getDurationSeconds()).isGreaterThanOrEqualTo(600);
    }

    @Test
    @DisplayName("已结束的面试不能取消")
    void shouldRejectCancelAfterFinish() {
        Interview interview = createdInterview();
        InterviewStateMachine.markStarted(interview);
        InterviewStateMachine.markFinished(interview);

        assertThatThrownBy(() -> InterviewStateMachine.markCancelled(interview))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("结束");
    }

    @Test
    @DisplayName("已取消的面试不能作答")
    void shouldRejectAnswerAfterCancel() {
        Interview interview = createdInterview();
        InterviewStateMachine.markCancelled(interview);

        assertThatThrownBy(() -> InterviewStateMachine.assertInProgress(interview))
                .isInstanceOf(BizException.class);
    }
}
