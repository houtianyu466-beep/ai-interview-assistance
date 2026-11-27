package com.aiinterview.practice.dto;

/**
 * 练习提示视图。
 *
 * @param hint 一句话提示（不超过 60 字），只引导思考方向，不给出完整答案
 */
public record PracticeHintVO(String hint) {
}
