package com.aiinterview.question.controller;

import com.aiinterview.common.api.PageResult;
import com.aiinterview.common.api.Result;
import com.aiinterview.common.log.OperationLog;
import com.aiinterview.question.dto.QuestionQuery;
import com.aiinterview.question.dto.QuestionSaveRequest;
import com.aiinterview.question.dto.QuestionVO;
import com.aiinterview.question.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题库管理接口。
 *
 * <p>所有登录用户均可查询题目；新增、修改、删除仅管理员与面试官可用。</p>
 */
@Tag(name = "题库管理")
@RestController
@RequestMapping("/api/v1/questions")
@Validated
public class QuestionController {

    private final QuestionService questionService;

    /**
     * 构造器注入。
     *
     * @param questionService 题库服务
     */
    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /**
     * 分页查询题库。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询题目")
    @GetMapping
    public Result<PageResult<QuestionVO>> page(@Valid QuestionQuery query) {
        return Result.ok(questionService.page(query));
    }

    /**
     * 查询题目详情。
     *
     * @param id 题目 id
     * @return 题目详情
     */
    @Operation(summary = "查询题目详情")
    @GetMapping("/{id}")
    public Result<QuestionVO> detail(@PathVariable Long id) {
        return Result.ok(questionService.detail(id));
    }

    /**
     * 创建题目。
     *
     * @param request 保存请求
     * @return 创建后的题目
     */
    @Operation(summary = "创建题目")
    @OperationLog("创建题库题目")
    @PreAuthorize("hasAnyRole('ADMIN','INTERVIEWER')")
    @PostMapping
    public Result<QuestionVO> create(@Valid @RequestBody QuestionSaveRequest request) {
        return Result.ok(questionService.create(request));
    }

    /**
     * 更新题目。
     *
     * @param id      题目 id
     * @param request 保存请求
     * @return 更新后的题目
     */
    @Operation(summary = "更新题目")
    @OperationLog("更新题库题目")
    @PreAuthorize("hasAnyRole('ADMIN','INTERVIEWER')")
    @PutMapping("/{id}")
    public Result<QuestionVO> update(@PathVariable Long id, @Valid @RequestBody QuestionSaveRequest request) {
        return Result.ok(questionService.update(id, request));
    }

    /**
     * 删除题目。
     *
     * @param id 题目 id
     * @return 成功响应
     */
    @Operation(summary = "删除题目")
    @OperationLog("删除题库题目")
    @PreAuthorize("hasAnyRole('ADMIN','INTERVIEWER')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return Result.ok();
    }
}
