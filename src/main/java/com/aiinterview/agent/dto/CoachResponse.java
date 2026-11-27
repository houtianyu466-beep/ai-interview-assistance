package com.aiinterview.agent.dto;

import java.util.List;

/**
 * 教练回答。
 *
 * @param answer    最终回答文本
 * @param citations 知识库引用，最多 3 条
 * @param toolCalls 本轮实际执行的工具调用轨迹
 * @param fallback  是否走了「未配置模型/模型失败」的知识库降级回答
 * @param latencyMs 本轮端到端耗时（毫秒）
 */
public record CoachResponse(String answer,
                            List<CitationVO> citations,
                            List<ToolCallVO> toolCalls,
                            boolean fallback,
                            long latencyMs) {
}
