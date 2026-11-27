package com.aiinterview.practice.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 练习方向，对应表 {@code practice_target}。
 *
 * <p>练习方向是「面试练习工具」的核心字典（如 Java 后端、前端、产品经理、通用 HR 面），
 * 替代招聘系统中的岗位概念：它没有部门、没有编制、也没有录用含义，只描述练习的主题与考察重点。</p>
 */
@Data
@TableName("practice_target")
public class PracticeTarget {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 唯一编码，例如 java-backend / frontend */
    private String code;

    /** 方向名称，例如 Java 后端工程师 */
    private String title;

    /** 副标题，用于卡片补充说明 */
    private String subtitle;

    /** 方向分类，例如 BACKEND / FRONTEND / PRODUCT */
    private String category;

    /** 方向描述（2-3 句中文） */
    private String description;

    /** 重点考察方向，英文逗号分隔 */
    private String focusTopics;

    /** 默认题数 */
    private Integer defaultQuestions;

    /** 默认难度 1-5 */
    private Integer difficulty;

    /** 前端图标名（Element Plus 图标） */
    private String icon;

    /** 排序值，越小越靠前 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
