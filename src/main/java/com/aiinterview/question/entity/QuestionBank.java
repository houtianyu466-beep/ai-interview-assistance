package com.aiinterview.question.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题库题目，对应表 {@code question_bank}。
 *
 * <p>作用有二：面试官沉淀标准题；AI 出题失败时作为降级题源。</p>
 */
@Data
@TableName("question_bank")
public class QuestionBank {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属岗位，null 表示通用题 */
    private Long positionId;

    /** 题目分类：JAVA / SPRING / MYSQL / REDIS / ALGORITHM / BEHAVIOR 等 */
    private String category;

    /** 难度 1-5 */
    private Integer difficulty;

    private String content;

    /** 参考答案，用于 AI 评分时作为参照 */
    private String referenceAnswer;

    /** 关键点，逗号分隔，用于规则降级评分 */
    private String keywords;

    private String tags;

    /** 来源：MANUAL / AI */
    private String source;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
