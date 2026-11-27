package com.aiinterview.resume.entity;

import com.aiinterview.resume.enums.ResumeStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历，对应表 {@code resume}。
 */
@Data
@TableName("resume")
public class Resume {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String fileName;

    /** 相对存储路径，例如 2026/11/26/xxx.docx */
    private String filePath;

    /** 抽取出的纯文本 */
    private String rawText;

    /** AI 解析出的结构化 JSON（ParsedResume） */
    private String parsedJson;

    /** 技能标签，逗号分隔，便于列表展示与匹配 */
    private String skills;

    private Integer yearsOfExperience;

    private String education;

    private ResumeStatus status;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
