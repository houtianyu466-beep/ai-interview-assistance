package com.aiinterview.admin.dto;

import java.util.List;

/**
 * 管理端统计概览。
 *
 * @param userCount          用户总数
 * @param candidateCount     候选人数量
 * @param interviewerCount   面试官数量
 * @param interviewCount     面试总数
 * @param finishedCount      已完成面试数（status = FINISHED）
 * @param inProgressCount    进行中面试数（status = IN_PROGRESS）
 * @param averageScore       已生成报告的整体平均分，无数据时为 0.0
 * @param last7Days          最近 7 天（含今天）每天的面试创建量，按日期升序
 * @param statusDistribution 面试状态分布
 * @param hotPositions       面试数量 Top5 岗位
 */
public record StatsOverviewVO(
        long userCount,
        long candidateCount,
        long interviewerCount,
        long interviewCount,
        long finishedCount,
        long inProgressCount,
        double averageScore,
        List<DailyCountVO> last7Days,
        List<StatusCountVO> statusDistribution,
        List<PositionStatVO> hotPositions) {
}
