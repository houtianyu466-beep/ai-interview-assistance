package com.aiinterview.interview.dto;

import com.aiinterview.interview.enums.InterviewStatus;
import lombok.Data;

/**
 * 面试分页查询条件。
 *
 * <p>「我的面试」与「全部面试」共用该查询对象，区别在于前者固定按当前登录人过滤。</p>
 */
@Data
public class InterviewQuery {

    /** 面试状态，为空表示不限 */
    private InterviewStatus status;

    /** 岗位标题关键字，模糊匹配，为空表示不限 */
    private String keyword;

    /** 页码，从 1 开始 */
    private Integer page;

    /** 每页大小 */
    private Integer size;

    /**
     * 页码，非法值回退为 1。
     *
     * @return 合法页码
     */
    public long pageOrDefault() {
        return page == null || page < 1 ? 1L : page;
    }

    /**
     * 每页大小，非法值回退为 10，上限 100。
     *
     * @return 合法每页大小
     */
    public long sizeOrDefault() {
        if (size == null || size < 1) {
            return 10L;
        }
        return Math.min(size, 100);
    }
}
