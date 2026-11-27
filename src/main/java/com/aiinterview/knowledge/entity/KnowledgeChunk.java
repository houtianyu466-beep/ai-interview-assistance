package com.aiinterview.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库分块，对应表 {@code knowledge_chunk}。
 *
 * <p>分块是检索的最小单元：BM25 索引以 {@link #content} 为语料，
 * {@link #embedding} 保存可选的向量 JSON（如 {@code [0.01, -0.23, ...]}）。
 * 向量生成失败时该字段为 {@code null}，分块仍可参与 BM25 检索，不影响整体灌库。</p>
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属文档 id */
    private Long documentId;

    /** 文档内分块序号，从 0 开始；与 documentId 组成唯一键 */
    private Integer chunkIndex;

    /** 分块正文 */
    private String content;

    /** 粗粒度词数，用于统计与观测 */
    private Integer tokens;

    /** 向量 JSON，未配置 embedding 或生成失败时为 null */
    private String embedding;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
