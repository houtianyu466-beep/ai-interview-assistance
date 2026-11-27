package com.aiinterview.job.dto;

import com.aiinterview.job.enums.JobStatus;
import lombok.Data;

/**
 * 岗位分页查询条件。
 *
 * <p>使用普通类 + Lombok，便于 Spring MVC 以 query 参数绑定；
 * 分页参数统一通过 {@link #pageOrDefault()} 与 {@link #sizeOrDefault()} 读取。</p>
 */
@Data
public class JobQuery {

    /** 关键字：模糊匹配岗位名称或所属部门 */
    private String keyword;

    /** 状态过滤，为空表示全部 */
    private JobStatus status;

    /** 页码，从 1 开始 */
    private Integer page;

    /** 每页条数 */
    private Integer size;

    /**
     * 页码，默认 1。
     *
     * @return 合法页码
     */
    public long pageOrDefault() {
        return page == null || page < 1 ? 1L : page;
    }

    /**
     * 每页条数，默认 10，最大 100。
     *
     * @return 合法每页条数
     */
    public long sizeOrDefault() {
        if (size == null || size < 1) {
            return 10L;
        }
        return Math.min(size, 100);
    }
}
