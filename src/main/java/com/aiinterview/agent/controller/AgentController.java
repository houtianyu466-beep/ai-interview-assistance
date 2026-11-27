package com.aiinterview.agent.controller;

import com.aiinterview.agent.dto.AgentTraceVO;
import com.aiinterview.agent.dto.CoachRequest;
import com.aiinterview.agent.dto.CoachResponse;
import com.aiinterview.agent.service.AgentService;
import com.aiinterview.common.api.Result;
import com.aiinterview.common.log.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面试内 AI 教练接口。
 *
 * <p>挂在练习会话下，会话归属校验在 {@link AgentService} 内完成；
 * 教练回答会真实调用模型工具，模型不可用时自动降级为知识库回答。</p>
 */
@Tag(name = "AI 面试教练")
@RestController
@RequestMapping("/api/v1/practice/sessions/{id}/coach")
@Validated
public class AgentController {

    private final AgentService agentService;

    /**
     * 构造器注入。
     *
     * @param agentService 教练服务
     */
    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 向 AI 教练提问。
     *
     * @param id      练习 id
     * @param request 提问请求
     * @return 教练回答
     */
    @Operation(summary = "向 AI 教练提问")
    @OperationLog("AI 教练对话")
    @PostMapping
    public Result<CoachResponse> coach(@PathVariable Long id,
                                       @Valid @RequestBody CoachRequest request) {
        return Result.ok(agentService.coach(id, request.message()));
    }

    /**
     * 查询该练习最近的 AI 教练调用链。
     *
     * @param id 练习 id
     * @return 调用链列表，最多 50 条
     */
    @Operation(summary = "查询 AI 教练调用链")
    @GetMapping("/traces")
    public Result<List<AgentTraceVO>> traces(@PathVariable Long id) {
        return Result.ok(agentService.traces(id));
    }
}
