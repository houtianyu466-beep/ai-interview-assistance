package com.aiinterview.admin.dto;

/**
 * 面试状态分布项。
 *
 * @param status 面试状态，例如 CREATED / IN_PROGRESS / FINISHED / CANCELLED
 * @param count  该状态下的面试数量
 */
public record StatusCountVO(String status, long count) {
}
