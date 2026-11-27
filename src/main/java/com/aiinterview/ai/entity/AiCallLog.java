package com.aiinterview.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用日志，对应表 {@code ai_call_log}。
 *
 * <p>记录每次大模型调用的耗时、token、是否降级，便于成本分析与问题定位。</p>
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 场景：RESUME_PARSE / QUESTION_GENERATE / ANSWER_SCORE / REPORT_GENERATE / RESUME_MATCH */
    private String scene;

    private String provider;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer latencyMs;

    private Boolean success;

    /** 是否走了规则降级 */
    private Boolean fallback;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
