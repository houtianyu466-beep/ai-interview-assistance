package com.aiinterview.agent.dto;

import java.time.LocalDateTime;

/**
 * 教练调用链视图。
 *
 * @param id        主键
 * @param sessionId 练习会话 id
 * @param scene     场景，当前为 COACH
 * @param input     用户输入
 * @param output    最终回答
 * @param toolCalls 工具调用轨迹 JSON
 * @param success   是否由真实模型完成
 * @param latencyMs 端到端耗时（毫秒）
 * @param createdAt 创建时间
 */
public record AgentTraceVO(Long id,
                           Long sessionId,
                           String scene,
                           String input,
                           String output,
                           String toolCalls,
                           Boolean success,
                           Integer latencyMs,
                           LocalDateTime createdAt) {
}
