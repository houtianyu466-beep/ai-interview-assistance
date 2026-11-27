package com.aiinterview.knowledge.dto;

import com.aiinterview.knowledge.entity.KnowledgeDocument;

import java.time.LocalDateTime;

/**
 * 知识文档展示对象。
 *
 * @param id         文档 id
 * @param title      标题
 * @param category   分类
 * @param source     来源
 * @param status     状态
 * @param chunkCount 分块数量
 * @param createdAt  创建时间
 */
public record KnowledgeDocumentVO(
        Long id,
        String title,
        String category,
        String source,
        String status,
        Integer chunkCount,
        LocalDateTime createdAt) {

    /**
     * 由实体与分块数量组装展示对象。
     *
     * @param entity     文档实体，允许为 {@code null}
     * @param chunkCount 分块数量
     * @return 展示对象，入参为空时返回 {@code null}
     */
    public static KnowledgeDocumentVO from(KnowledgeDocument entity, int chunkCount) {
        if (entity == null) {
            return null;
        }
        return new KnowledgeDocumentVO(
                entity.getId(),
                entity.getTitle(),
                entity.getCategory(),
                entity.getSource(),
                entity.getStatus(),
                chunkCount,
                entity.getCreatedAt());
    }
}
