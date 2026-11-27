package com.aiinterview.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 知识文档录入请求。
 *
 * @param title    标题，必填，最长 200 字
 * @param category 分类，可为空，建议 GUIDE / TECH / HR
 * @param content  正文，必填，最长 20 万字；服务端会按段落与中文标点切块
 */
public record KnowledgeIngestRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题不能超过 200 字")
        String title,
        String category,
        @NotBlank(message = "内容不能为空")
        @Size(max = 200000, message = "内容不能超过 200000 字")
        String content) {
}
