package com.aiinterview.interview.ws;

import com.aiinterview.interview.dto.AnswerResultVO;
import com.aiinterview.interview.dto.CurrentQuestionVO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket 实时面试通道的消息体，上行（客户端 → 服务器）与下行（服务器 → 客户端）复用同一结构。
 *
 * <p>消息统一为 JSON，例如提交回答：</p>
 * <pre>{@code
 * {"type":"ANSWER","content":"我的回答...","thinkingSeconds":12,"data":{"questionId":1}}
 * }</pre>
 *
 * <p>服务器 → 客户端类型：{@link #TYPE_CONNECTED}、{@link #TYPE_QUESTION}、{@link #TYPE_SCORE}、
 * {@link #TYPE_FINISHED}、{@link #TYPE_PONG}、{@link #TYPE_ERROR}、{@link #TYPE_SWITCH_ACK}；</p>
 * <p>客户端 → 服务器类型：{@link #TYPE_START}、{@link #TYPE_ANSWER}、{@link #TYPE_PING}、{@link #TYPE_SWITCH}。</p>
 *
 * @param type            消息类型，非空
 * @param content         文本内容：客户端 {@code ANSWER} 时为回答正文，服务器回包时为提示文案
 * @param thinkingSeconds 思考秒数，仅客户端 {@code ANSWER} 消息使用，可为空（为空时按 0 处理）
 * @param data            结构化数据，如当前题目、评分结果、reportId 等，可为空
 */
public record WsMessage(String type, String content, Integer thinkingSeconds, Object data) {

    /** 服务器 → 客户端：连接建立成功。 */
    public static final String TYPE_CONNECTED = "CONNECTED";
    /** 服务器 → 客户端：下发当前题目。 */
    public static final String TYPE_QUESTION = "QUESTION";
    /** 服务器 → 客户端：本题评分结果。 */
    public static final String TYPE_SCORE = "SCORE";
    /** 服务器 → 客户端：面试结束并给出报告入口。 */
    public static final String TYPE_FINISHED = "FINISHED";
    /** 服务器 → 客户端：心跳响应。 */
    public static final String TYPE_PONG = "PONG";
    /** 服务器 → 客户端：错误提示。 */
    public static final String TYPE_ERROR = "ERROR";
    /** 服务器 → 客户端：切换题目成功。 */
    public static final String TYPE_SWITCH_ACK = "SWITCH_ACK";
    /** 客户端 → 服务器：开始面试。 */
    public static final String TYPE_START = "START";
    /** 客户端 → 服务器：提交回答。 */
    public static final String TYPE_ANSWER = "ANSWER";
    /** 客户端 → 服务器：心跳请求。 */
    public static final String TYPE_PING = "PING";
    /** 客户端 → 服务器：切换到下一题。 */
    public static final String TYPE_SWITCH = "SWITCH";

    /**
     * 构造「连接建立成功」消息，携带面试 id 便于客户端确认通道归属。
     *
     * @param interviewId 面试 id
     * @return 连接成功消息
     */
    public static WsMessage connected(Long interviewId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("interviewId", interviewId);
        return new WsMessage(TYPE_CONNECTED, "实时面试通道已连接", null, data);
    }

    /**
     * 构造「题目下发」消息，正文直接给出题干，完整对象放在 {@code data} 中。
     *
     * @param question 当前题目
     * @return 题目消息
     */
    public static WsMessage question(CurrentQuestionVO question) {
        String content = question == null ? null : question.content();
        return new WsMessage(TYPE_QUESTION, content, null, question);
    }

    /**
     * 构造「本题评分」消息，评语作为正文，完整结果放在 {@code data} 中。
     *
     * @param result 评分结果
     * @return 评分消息
     */
    public static WsMessage score(AnswerResultVO result) {
        String content = result == null ? null : result.comment();
        return new WsMessage(TYPE_SCORE, content, null, result);
    }

    /**
     * 构造「面试结束」消息；整体分取不到时只下发 reportId，由客户端再拉取报告详情。
     *
     * @param reportId     报告 id
     * @param overallScore 整体得分，可为空
     * @return 结束消息
     */
    public static WsMessage finished(Long reportId, Double overallScore) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportId", reportId);
        if (overallScore != null) {
            data.put("overallScore", overallScore);
        }
        return new WsMessage(TYPE_FINISHED, "面试已结束，报告已生成", null, data);
    }

    /**
     * 构造心跳响应消息。
     *
     * @return 心跳响应
     */
    public static WsMessage pong() {
        return new WsMessage(TYPE_PONG, "PONG", null, null);
    }

    /**
     * 构造错误提示消息，异常信息放在正文中。
     *
     * @param message 错误提示
     * @return 错误消息
     */
    public static WsMessage error(String message) {
        return new WsMessage(TYPE_ERROR, message, null, null);
    }

    /**
     * 构造「切换题目成功」消息。
     *
     * @param count 当前累计切换次数，接口不返回时可为空
     * @return 切换确认消息
     */
    public static WsMessage switched(Integer count) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (count != null) {
            data.put("switchCount", count);
        }
        return new WsMessage(TYPE_SWITCH_ACK, "已切换题目", null, data.isEmpty() ? null : data);
    }
}
