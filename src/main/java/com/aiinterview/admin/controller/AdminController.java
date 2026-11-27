package com.aiinterview.admin.controller;

import com.aiinterview.admin.dto.StatsOverviewVO;
import com.aiinterview.admin.dto.UserStatusRequest;
import com.aiinterview.admin.service.StatsService;
import com.aiinterview.common.api.PageResult;
import com.aiinterview.common.api.Result;
import com.aiinterview.common.log.OperationLog;
import com.aiinterview.system.dto.UserQuery;
import com.aiinterview.system.dto.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端接口：数据概览、用户管理。
 *
 * <p>类级鉴权只允许 ADMIN 访问，方法内无需重复声明。</p>
 */
@Tag(name = "管理端")
@RestController
@RequestMapping("/api/v1/admin")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final StatsService statsService;

    /**
     * 构造器注入。
     *
     * @param statsService 统计与管理服务
     */
    public AdminController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * 查询管理端统计概览。
     *
     * @return 统计概览
     */
    @Operation(summary = "查询统计概览")
    @GetMapping("/stats/overview")
    public Result<StatsOverviewVO> overview() {
        return Result.ok(statsService.overview());
    }

    /**
     * 分页查询用户。
     *
     * @param query 查询条件
     * @return 用户分页结果
     */
    @Operation(summary = "分页查询用户")
    @GetMapping("/users")
    public Result<PageResult<UserVO>> users(@Valid UserQuery query) {
        return Result.ok(statsService.pageUsers(query));
    }

    /**
     * 修改用户状态（启用/禁用）。
     *
     * @param id      用户 id
     * @param request 状态请求
     * @return 更新后的用户
     */
    @Operation(summary = "修改用户状态")
    @OperationLog("修改用户状态")
    @PatchMapping("/users/{id}/status")
    public Result<UserVO> updateUserStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        return Result.ok(statsService.updateUserStatus(id, request.status()));
    }
}
