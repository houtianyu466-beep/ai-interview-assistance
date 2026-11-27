package com.aiinterview.resume.dto;

import com.aiinterview.ai.model.ParsedResume;

/**
 * 简历详情视图：摘要信息 + 抽取出的纯文本 + 结构化解析结果。
 *
 * @param resume  简历摘要
 * @param rawText 抽取出的纯文本
 * @param parsed  AI/规则解析出的结构化简历，尚未解析完成时为 null
 */
public record ResumeDetailVO(ResumeVO resume, String rawText, ParsedResume parsed) {
}
