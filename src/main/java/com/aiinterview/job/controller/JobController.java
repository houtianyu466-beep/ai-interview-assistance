package com.aiinterview.job.controller;

import com.aiinterview.common.api.PageResult;
import com.aiinterview.common.api.Result;
import com.aiinterview.common.log.OperationLog;
import com.aiinterview.job.dto.JobQuery;
import com.aiinterview.job.dto.JobSaveRequest;
import com.aiinterview.job.dto.JobVO;
import com.aiinterview.job.enums.JobStatus;
import com.aiinterview.job.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗位管理接口。
 */
@Tag(name = "岗位管理")
@RestController
@RequestMapping("/api/v1/jobs")
@Validated
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * 分页查询岗位。
     *
     * @param query 查询条件
     * @return 岗位分页数据
     */
    @Operation(summary = "分页查询岗位")
    @GetMapping
    public Result<PageResult<JobVO>> page(@Valid JobQuery query) {
        return Result.ok(jobService.page(query));
    }

    /**
     * 查询岗位详情。
     *
     * @param id 岗位 id
     * @return 岗位信息
     */
    @Operation(summary = "查询岗位详情")
    @GetMapping("/{id}")
    public Result<JobVO> detail(@PathVariable Long id) {
        return Result.ok(jobService.detail(id));
    }

    /**
     * 新建岗位。
     *
     * @param request 岗位信息
     * @return 新建后的岗位
     */
    @Operation(summary = "新建岗位")
    @OperationLog("新建岗位")
    @PreAuthorize("hasAnyRole('ADMIN','INTERVIEWER')")
    @PostMapping
    public Result<JobVO> create(@Valid @RequestBody JobSaveRequest request) {
        return Result.ok(jobService.create(request));
    }

    /**
     * 更新岗位。
     *
     * @param id      岗位 id
     * @param request 岗位信息
     * @return 更新后的岗位
     */
    @Operation(summary = "更新岗位")
    @OperationLog("更新岗位")
    @PreAuthorize("hasAnyRole('ADMIN','INTERVIEWER')")
    @PutMapping("/{id}")
    public Result<JobVO> update(@PathVariable Long id, @Valid @RequestBody JobSaveRequest request) {
        return Result.ok(jobService.update(id, request));
    }

    /**
     * 删除岗位（仅管理员）。
     *
     * @param id 岗位 id
     * @return 空响应
     */
    @Operation(summary = "删除岗位")
    @OperationLog("删除岗位")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return Result.ok();
    }

    /**
     * 变更岗位状态。
     *
     * @param id     岗位 id
     * @param status 目标状态，如 OPEN / CLOSED
     * @return 变更后的岗位
     */
    @Operation(summary = "变更岗位状态")
    @OperationLog("变更岗位状态")
    @PreAuthorize("hasAnyRole('ADMIN','INTERVIEWER')")
    @PatchMapping("/{id}/status")
    public Result<JobVO> changeStatus(@PathVariable Long id, @RequestParam JobStatus status) {
        return Result.ok(jobService.changeStatus(id, status));
    }
}
