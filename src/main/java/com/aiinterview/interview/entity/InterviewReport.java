package com.aiinterview.interview.entity;

import com.aiinterview.interview.enums.Recommendation;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试报告，对应表 {@code interview_report}，与面试会话一对一。
 */
@Data
@TableName("interview_report")
public class InterviewReport {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long interviewId;

    private BigDecimal overallScore;

    /** 维度均分 JSON，例如 {"accuracy":78.5,"depth":71.0} */
    private String dimensionScores;

    /** 优势点 JSON 数组字符串 */
    private String strengths;

    /** 待改进点 JSON 数组字符串 */
    private String weaknesses;

    private Recommendation suggestion;

    private String summary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
