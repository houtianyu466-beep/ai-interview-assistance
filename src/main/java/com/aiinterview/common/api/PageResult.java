package com.aiinterview.common.api;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应结构。
 */
public class PageResult<T> implements Serializable {

    /** 总记录数 */
    private long total;
    /** 当前页码，从 1 开始 */
    private long current;
    /** 每页大小 */
    private long size;
    /** 总页数 */
    private long pages;
    /** 当前页数据 */
    private List<T> records;

    public PageResult() {
    }

    public PageResult(long total, long current, long size, List<T> records) {
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = size == 0 ? 0 : (total + size - 1) / size;
        this.records = records;
    }

    public static <T> PageResult<T> of(long total, long current, long size, List<T> records) {
        return new PageResult<>(total, current, size, records);
    }

    public static <T> PageResult<T> empty(long current, long size) {
        return new PageResult<>(0, current, size, List.of());
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getCurrent() {
        return current;
    }

    public void setCurrent(long current) {
        this.current = current;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getPages() {
        return pages;
    }

    public void setPages(long pages) {
        this.pages = pages;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}
