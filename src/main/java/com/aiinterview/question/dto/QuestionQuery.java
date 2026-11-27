package com.aiinterview.question.dto;

import lombok.Data;

/**
 * 题库分页查询条件。
 *
 * <p>作为 GET 请求的 query 参数绑定对象，所有字段均可为空；分页参数由
 * {@link #pageOrDefault()} 与 {@link #sizeOrDefault()} 提供默认值与上限保护。</p>
 */
@Data
public class QuestionQuery {

    /** 所属岗位 id，为空表示不按岗位过滤 */
    private Long positionId;

    /** 题目分类，精确匹配 */
    private String category;

    /** 难度，精确匹配 */
    private Integer difficulty;

    /** 关键词，模糊匹配题干与标签 */
    private String keyword;

    /** 页码，从 1 开始 */
    private Integer page;

    /** 每页条数，默认 10，上限 100 */
    private Integer size;

    /**
     * 返回合法的页码，为空或小于 1 时取 1。
     *
     * @return 页码
     */
    public int pageOrDefault() {
        return page == null || page < 1 ? 1 : page;
    }

    /**
     * 返回合法的每页条数，为空或小于 1 时取 10，并限制最大 100。
     *
     * @return 每页条数
     */
    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }
}
