package com.aiinterview.practice.controller;

import com.aiinterview.common.api.PageResult;
import com.aiinterview.common.api.Result;
import com.aiinterview.common.log.OperationLog;
import com.aiinterview.interview.dto.AnswerResultVO;
import com.aiinterview.interview.dto.CurrentQuestionVO;
import com.aiinterview.interview.dto.MessageVO;
import com.aiinterview.interview.dto.ReportVO;
import com.aiinterview.practice.dto.CreatePracticeRequest;
import com.aiinterview.practice.dto.PracticeAnswerRequest;
import com.aiinterview.practice.dto.PracticeHintVO;
import com.aiinterview.practice.dto.PracticeReviewVO;
import com.aiinterview.practice.dto.PracticeSessionVO;
import com.aiinterview.practice.dto.PracticeSkipResult;
import com.aiinterview.practice.dto.PracticeTargetVO;
import com.aiinterview.practice.service.PracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面试练习接口：练习方向、创建练习、答题、提示、跳过、结束与复盘。
 *
 * <p>全部接口都需要登录；练习会话的归属校验在 {@link PracticeService} 内完成。</p>
 */
@Tag(name = "面试练习")
@RestController
@RequestMapping("/api/v1/practice")
@Validated
public class PracticeController {

    private final PracticeService practiceService;

    /**
     * 构造器注入。
     *
     * @param practiceService 练习服务
     */
    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    /**
     * 查询练习方向列表。
     *
     * @return 练习方向列表
     */
    @Operation(summary = "查询练习方向列表")
    @GetMapping("/targets")
    public Result<List<PracticeTargetVO>> targets() {
        return Result.ok(practiceService.targets());
    }

    /**
     * 查询练习方向详情。
     *
     * @param id 方向 id
     * @return 练习方向
     */
    @Operation(summary = "查询练习方向详情")
    @GetMapping("/targets/{id}")
    public Result<PracticeTargetVO> target(@PathVariable Long id) {
        return Result.ok(practiceService.target(id));
    }

    /**
     * 创建练习。
     *
     * @param request 创建请求
     * @return 新建的练习会话
     */
    @Operation(summary = "创建练习")
    @OperationLog("创建练习")
    @PostMapping("/sessions")
    public Result<PracticeSessionVO> create(@Valid @RequestBody CreatePracticeRequest request) {
        return Result.ok(practiceService.create(request));
    }

    /**
     * 分页查询我的练习。
     *
     * @param page   页码，默认 1
     * @param size   每页大小，默认 10
     * @param status 练习状态，可选
     * @return 分页结果
     */
    @Operation(summary = "分页查询我的练习")
    @GetMapping("/sessions/my")
    public Result<PageResult<PracticeSessionVO>> mySessions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {
        return Result.ok(practiceService.mySessions(page, size, status));
    }

    /**
     * 查询练习详情。
     *
     * @param id 练习 id
     * @return 练习会话
     */
    @Operation(summary = "查询练习详情")
    @GetMapping("/sessions/{id}")
    public Result<PracticeSessionVO> session(@PathVariable Long id) {
        return Result.ok(practiceService.session(id));
    }

    /**
     * 开始练习。
     *
     * @param id 练习 id
     * @return 第一题
     */
    @Operation(summary = "开始练习")
    @OperationLog("开始练习")
    @PostMapping("/sessions/{id}/start")
    public Result<CurrentQuestionVO> start(@PathVariable Long id) {
        return Result.ok(practiceService.start(id));
    }

    /**
     * 查询当前待作答的题目。
     *
     * @param id 练习 id
     * @return 当前题目
     */
    @Operation(summary = "查询当前题目")
    @GetMapping("/sessions/{id}/current")
    public Result<CurrentQuestionVO> current(@PathVariable Long id) {
        return Result.ok(practiceService.current(id));
    }

    /**
     * 提交单题答案。
     *
     * @param id      练习 id
     * @param request 作答请求
     * @return 单题结果
     */
    @Operation(summary = "提交练习答案")
    @OperationLog("提交练习答案")
    @PostMapping("/sessions/{id}/answers")
    public Result<AnswerResultVO> answer(@PathVariable Long id,
                                         @Valid @RequestBody PracticeAnswerRequest request) {
        return Result.ok(practiceService.answer(id, request));
    }

    /**
     * 获取当前题的提示。
     *
     * @param id 练习 id
     * @return 一句话提示
     */
    @Operation(summary = "获取练习提示")
    @OperationLog("获取练习提示")
    @PostMapping("/sessions/{id}/hint")
    public Result<PracticeHintVO> hint(@PathVariable Long id) {
        return Result.ok(practiceService.hint(id));
    }

    /**
     * 跳过当前题。
     *
     * @param id 练习 id
     * @return 跳过结果
     */
    @Operation(summary = "跳过当前题目")
    @OperationLog("跳过练习题目")
    @PostMapping("/sessions/{id}/skip")
    public Result<PracticeSkipResult> skip(@PathVariable Long id) {
        return Result.ok(practiceService.skip(id));
    }

    /**
     * 结束练习并生成报告。
     *
     * @param id 练习 id
     * @return 练习报告
     */
    @Operation(summary = "结束练习")
    @OperationLog("结束练习")
    @PostMapping("/sessions/{id}/finish")
    public Result<ReportVO> finish(@PathVariable Long id) {
        return Result.ok(practiceService.finish(id));
    }

    /**
     * 查询练习复盘。
     *
     * @param id 练习 id
     * @return 练习复盘
     */
    @Operation(summary = "查询练习复盘")
    @GetMapping("/sessions/{id}/review")
    public Result<PracticeReviewVO> review(@PathVariable Long id) {
        return Result.ok(practiceService.review(id));
    }

    /**
     * 查询练习对话消息。
     *
     * @param id 练习 id
     * @return 消息列表
     */
    @Operation(summary = "查询练习对话消息")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<MessageVO>> messages(@PathVariable Long id) {
        return Result.ok(practiceService.messages(id));
    }

    /**
     * 取消练习。
     *
     * @param id 练习 id
     * @return 空结果
     */
    @Operation(summary = "取消练习")
    @OperationLog("取消练习")
    @PostMapping("/sessions/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        practiceService.cancel(id);
        return Result.ok();
    }
}
