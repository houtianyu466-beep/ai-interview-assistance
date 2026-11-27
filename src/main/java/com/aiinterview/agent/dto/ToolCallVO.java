package com.aiinterview.agent.dto;

/**
 * 一次工具调用记录，用于前端折叠展示与调用链排查。
 *
 * @param name          工具名，如 search_knowledge / get_practice_state / get_my_resume
 * @param arguments     模型给出的调用参数（原始 JSON 字符串）
 * @param resultSummary 执行结果摘要，最多 200 字
 */
public record ToolCallVO(String name, String arguments, String resultSummary) {
}
