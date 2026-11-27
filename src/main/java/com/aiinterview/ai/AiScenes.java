package com.aiinterview.ai;

/**
 * AI 调用场景常量，同时用于 {@code ai_call_log.scene} 字段。
 */
public final class AiScenes {

    public static final String RESUME_PARSE = "RESUME_PARSE";
    public static final String QUESTION_GENERATE = "QUESTION_GENERATE";
    public static final String ANSWER_SCORE = "ANSWER_SCORE";
    public static final String REPORT_GENERATE = "REPORT_GENERATE";
    public static final String RESUME_MATCH = "RESUME_MATCH";
    /** 练习提示：练习者卡壳时给出的一句话思考方向 */
    public static final String HINT = "HINT";

    private AiScenes() {
    }
}
