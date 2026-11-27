package com.aiinterview.interview.entity;

import com.aiinterview.interview.enums.MessageRole;
import com.aiinterview.interview.enums.MessageType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试对话消息，对应表 {@code interview_message}，用于面试回放。
 */
@Data
@TableName("interview_message")
public class InterviewMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long interviewId;

    private MessageRole role;

    private MessageType type;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
