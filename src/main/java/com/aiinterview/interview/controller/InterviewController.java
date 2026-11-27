package com.aiinterview.interview.controller;

import com.aiinterview.common.api.PageResult;
import com.aiinterview.common.api.Result;
import com.aiinterview.common.log.OperationLog;
import com.aiinterview.interview.dto.AnswerResultVO;
import com.aiinterview.interview.dto.CreateInterviewRequest;
import com.aiinterview.interview.dto.CurrentQuestionVO;
import com.aiinterview.interview.dto.InterviewDetailVO;
import com.aiinterview.interview.dto.InterviewQuery;
import com.aiinterview.interview.dto.InterviewVO;
import com.aiinterview.interview.dto.MessageVO;
import com.aiinterview.interview.dto.ReportVO;
import com.aiinterview.interview.dto.SubmitAnswerRequest;
import com.aiinterview.interview.service.InterviewService;
import com.aiinterview.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面试主流程接口：创建、开始、答题、结束、报告与回放。
 */
@Tag(name = "面试管理")
@RestController
@RequestMapping("/api/v1/interviews")
@Validated
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * 构造器注入。
     *
     * @param interviewService 面试服务
     */
    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    /**
     * 创建面试。
     *
     * @param request 创建请求
     * @return 新建的面试
     */
    @Operation(summary = "创建面试")
    @OperationLog("创建面试")
    @PostMapping
    public Result<InterviewVO> create(@Valid @RequestBody CreateInterviewRequest request) {
        return Result.ok(interviewService.create(request));
    }

    /**
     * 分页查询我的面试。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询我的面试")
    @GetMapping("/my")
    public Result<PageResult<InterviewVO>> myInterviews(@Valid InterviewQuery query) {
        return Result.ok(interviewService.myInterviews(query));
    }

    /**
     * 分页查询全部面试，仅管理员与面试官可用。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询全部面试")
    @PreAuthorize("hasAnyRole('ADMIN','INTERVIEWER')")
    @GetMapping
    public Result<PageResult<InterviewVO>> allInterviews(@Valid InterviewQuery query) {
        return Result.ok(interviewService.allInterviews(query));
    }

    /**
     * 查询面试详情。
     *
     * @param id 面试 id
     * @return 面试详情
     */
    @Operation(summary = "查询面试详情")
    @GetMapping("/{id}")
    public Result<InterviewDetailVO> detail(@PathVariable Long id) {
        return Result.ok(interviewService.detail(id));
    }

    /**
     * 查询当前待作答的题目。
     *
     * @param id 面试 id
     * @return 当前题目
     */
    @Operation(summary = "查询当前题目")
    @GetMapping("/{id}/questions/current")
    public Result<CurrentQuestionVO> currentQuestion(@PathVariable Long id) {
        return Result.ok(interviewService.currentQuestion(id, SecurityUtils.currentUserId()));
    }

    /**
     * 开始面试。
     *
     * @param id 面试 id
     * @return 第一题
     */
    @Operation(summary = "开始面试")
    @OperationLog("开始面试")
    @PostMapping("/{id}/start")
    public Result<CurrentQuestionVO> start(@PathVariable Long id) {
        interviewService.requireOwnedInterview(id, SecurityUtils.currentUserId());
        return Result.ok(interviewService.start(id));
    }

    /**
     * 提交单题答案。
     *
     * @param id      面试 id
     * @param request 提交请求
     * @return 单题结果
     */
    @Operation(summary = "提交答案")
    @OperationLog("提交面试答案")
    @PostMapping("/{id}/answers")
    public Result<AnswerResultVO> submitAnswer(@PathVariable Long id,
                                               @Valid @RequestBody SubmitAnswerRequest request) {
        return Result.ok(interviewService.submitAnswer(id, SecurityUtils.currentUserId(), request));
    }

    /**
     * 结束面试并生成报告。
     *
     * @param id 面试 id
     * @return 面试报告
     */
    @Operation(summary = "结束面试")
    @OperationLog("结束面试")
    @PostMapping("/{id}/finish")
    public Result<ReportVO> finish(@PathVariable Long id) {
        return Result.ok(interviewService.finish(id, SecurityUtils.currentUserId()));
    }

    /**
     * 取消面试。
     *
     * @param id 面试 id
     * @return 空结果
     */
    @Operation(summary = "取消面试")
    @OperationLog("取消面试")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        interviewService.cancel(id);
        return Result.ok();
    }

    /**
     * 查询面试报告。
     *
     * @param id 面试 id
     * @return 面试报告
     */
    @Operation(summary = "查询面试报告")
    @GetMapping("/{id}/report")
    public Result<ReportVO> report(@PathVariable Long id) {
        return Result.ok(interviewService.report(id));
    }

    /**
     * 重新生成面试报告。
     *
     * @param id 面试 id
     * @return 新的面试报告
     */
    @Operation(summary = "重新生成面试报告")
    @OperationLog("重新生成面试报告")
    @PostMapping("/{id}/report/regenerate")
    public Result<ReportVO> regenerateReport(@PathVariable Long id) {
        return Result.ok(interviewService.regenerateReport(id));
    }

    /**
     * 查询面试对话消息。
     *
     * @param id 面试 id
     * @return 消息列表
     */
    @Operation(summary = "查询面试对话消息")
    @GetMapping("/{id}/messages")
    public Result<List<MessageVO>> messages(@PathVariable Long id) {
        return Result.ok(interviewService.messages(id));
    }

    /**
     * 上报一次切屏。
     *
     * @param id 面试 id
     * @return 空结果
     */
    @Operation(summary = "记录切屏")
    @OperationLog("记录面试切屏")
    @PostMapping("/{id}/switch")
    public Result<Void> reportSwitch(@PathVariable Long id) {
        interviewService.reportSwitch(id, SecurityUtils.currentUserId());
        return Result.ok();
    }
}
