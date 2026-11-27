package com.aiinterview.system.controller;

import com.aiinterview.common.api.Result;
import com.aiinterview.common.log.OperationLog;
import com.aiinterview.system.dto.LoginRequest;
import com.aiinterview.system.dto.LoginResponse;
import com.aiinterview.system.dto.RegisterRequest;
import com.aiinterview.system.dto.UserVO;
import com.aiinterview.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册、登录、当前用户信息与登出。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册候选人账号，成功后直接返回令牌。
     *
     * @param request 注册请求
     * @return 令牌与用户信息
     */
    @Operation(summary = "注册（仅候选人）")
    @OperationLog("用户注册")
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    /**
     * 账号密码登录。
     *
     * @param request 登录请求
     * @return 令牌与用户信息
     */
    @Operation(summary = "登录")
    @OperationLog("用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 查询当前登录用户信息。
     *
     * @return 当前用户信息
     */
    @Operation(summary = "当前登录用户信息")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.currentUserProfile());
    }

    /**
     * 退出登录，把当前令牌加入黑名单。
     *
     * @param authorization 请求头 Authorization，缺失时也返回成功
     * @return 空响应
     */
    @Operation(summary = "退出登录")
    @OperationLog("退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return Result.ok();
    }
}
