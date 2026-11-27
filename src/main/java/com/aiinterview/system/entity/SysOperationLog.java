package com.aiinterview.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志，由 {@code OperationLogAspect} 自动写入。
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String username;

    /** 操作描述，来自 @OperationLog 注解 */
    private String action;

    /** HTTP 方法 + URI */
    private String method;

    private String path;

    private String ip;

    private Integer costMs;

    private Boolean success;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
