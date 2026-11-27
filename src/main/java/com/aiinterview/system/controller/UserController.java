package com.aiinterview.system.controller;

import com.aiinterview.common.api.Result;
import com.aiinterview.common.log.OperationLog;
import com.aiinterview.system.dto.ChangePasswordRequest;
import com.aiinterview.system.dto.UpdateProfileRequest;
import com.aiinterview.system.dto.UserVO;
import com.aiinterview.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户自助接口：维护当前登录用户的资料与密码。
 */
@Tag(name = "用户")
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 修改当前登录用户的个人资料。
     *
     * @param request 资料修改请求
     * @return 修改后的用户信息
     */
    @Operation(summary = "修改个人资料")
    @OperationLog("修改个人资料")
    @PutMapping("/me")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateProfile(request));
    }

    /**
     * 修改当前登录用户的登录密码。
     *
     * @param request 密码修改请求
     * @return 空响应
     */
    @Operation(summary = "修改登录密码")
    @OperationLog("修改登录密码")
    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return Result.ok();
    }
}
