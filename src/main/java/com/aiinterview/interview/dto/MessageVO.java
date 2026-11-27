package com.aiinterview.interview.dto;

import com.aiinterview.interview.entity.InterviewMessage;

import java.time.LocalDateTime;

/**
 * 面试对话消息视图，用于回放。
 *
 * @param id        消息 id
 * @param role      发送方：SYSTEM / AI / CANDIDATE
 * @param type      消息类型：QUESTION / ANSWER / SCORE / SYSTEM
 * @param content   消息内容
 * @param createdAt 创建时间
 */
public record MessageVO(Long id, String role, String type, String content, LocalDateTime createdAt) {

    /**
     * 由消息实体构建视图。
     *
     * @param message 消息实体
     * @return 消息视图
     */
    public static MessageVO from(InterviewMessage message) {
        return new MessageVO(
                message.getId(),
                message.getRole() == null ? null : message.getRole().name(),
                message.getType() == null ? null : message.getType().name(),
                message.getContent(),
                message.getCreatedAt());
    }
}
