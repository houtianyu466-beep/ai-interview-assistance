package com.aiinterview.admin.dto;

/**
 * 按天统计的面试创建量。
 *
 * @param date  日期，格式 {@code yyyy-MM-dd}
 * @param count 当天创建的面试数量
 */
public record DailyCountVO(String date, long count) {
}
