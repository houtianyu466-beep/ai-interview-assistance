package com.aiinterview.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试题目快照，对应表 {@code interview_question}。
 *
 * <p>题目在创建面试时一次性生成并落库，好处是：面试过程中题目稳定、
 * 可直接按 seq 回溯，报告阶段也不必再次调用大模型。</p>
 */
@Data
@TableName("interview_question")
public class InterviewQuestion {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long interviewId;

    /** 题号，从 1 开始 */
    private Integer seq;

    private String content;

    private String category;

    private Integer difficulty;

    /** 参考答案（AI 生成或题库带出） */
    private String referenceAnswer;

    private String answer;

    private LocalDateTime answerAt;

    /** 思考/作答耗时（秒） */
    private Integer thinkingSeconds;

    /** 本题得分 0-100 */
    private BigDecimal score;

    /** 维度得分 JSON，例如 {"accuracy":80,"depth":70} */
    private String dimensionScores;

    /** AI 点评 */
    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
