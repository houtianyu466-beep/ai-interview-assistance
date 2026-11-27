package com.aiinterview.admin.dto;

/**
 * 热门岗位统计项。
 *
 * @param positionId     岗位 id
 * @param title          岗位名称
 * @param interviewCount 该岗位的面试数量
 */
public record PositionStatVO(Long positionId, String title, long interviewCount) {
}
