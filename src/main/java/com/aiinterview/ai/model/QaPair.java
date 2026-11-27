package com.aiinterview.ai.model;

/**
 * 报告生成时的问答对快照。
 *
 * @param seq        题号
 * @param question   题目
 * @param answer     候选人回答
 * @param score      该题得分
 * @param comment    该题点评
 */
public record QaPair(int seq, String question, String answer, Double score, String comment) {
}
