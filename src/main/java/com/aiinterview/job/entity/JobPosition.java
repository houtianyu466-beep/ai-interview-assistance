package com.aiinterview.job.entity;

import com.aiinterview.job.enums.JobLevel;
import com.aiinterview.job.enums.JobStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 招聘岗位，对应表 {@code job_position}。AI 出题会把岗位 JD 作为上下文。
 */
@Data
@TableName("job_position")
public class JobPosition {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    private String department;

    private JobLevel level;

    /** 岗位职责 */
    private String description;

    /** 任职要求 */
    private String requirements;

    private JobStatus status;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
