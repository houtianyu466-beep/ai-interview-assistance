package com.aiinterview.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试教练调用链，对应表 {@code agent_trace}。
 *
 * <p>一次教练对话记录一行：用户输入、工具调用轨迹、最终输出与耗时。
 * 与 {@code ai_call_log} 的区别是这里记录的是「Agent 行为」而非单次模型调用：
 * 即使一次对话里模型调用了多次工具，也只落一条 {@code agent_trace}。</p>
 */
@Data
@TableName("agent_trace")
public class AgentTrace {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 发起对话的用户 */
    private Long userId;

    /** 练习会话 id，对应 interview_session.id */
    private Long sessionId;

    /** 场景，当前固定为 COACH */
    private String scene;

    /** 用户输入 */
    private String input;

    /** 工具调用轨迹 JSON，元素结构见 {@link com.aiinterview.agent.dto.ToolCallVO} */
    private String toolCalls;

    /** 最终回答（落库前截断到 4000 字） */
    private String output;

    /** 是否由真实模型完成；走知识库降级回答时为 {@code false} */
    private Boolean success;

    /** 端到端耗时（毫秒） */
    private Integer latencyMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
