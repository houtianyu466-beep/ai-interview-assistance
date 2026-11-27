package com.aiinterview.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档，对应表 {@code knowledge_document}。
 *
 * <p>一篇文档由若干 {@link KnowledgeChunk} 组成：正文按段落与中文标点切块后，
 * 每个分块单独参与 BM25 检索（配置 embedding 时还会额外保存向量）。</p>
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 文档标题，最长 200 字 */
    private String title;

    /** 分类：GUIDE / TECH / HR 等，可为空 */
    private String category;

    /** 来源：MANUAL（手工录入）/ IMPORT / AI */
    private String source;

    /** 正文原文，便于重新切块 */
    private String content;

    /** 状态：READY 表示可直接检索 */
    private String status;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入与更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
