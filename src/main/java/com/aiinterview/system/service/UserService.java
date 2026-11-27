package com.aiinterview.system.service;

import com.aiinterview.common.api.PageResult;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.security.SecurityUtils;
import com.aiinterview.system.dto.ChangePasswordRequest;
import com.aiinterview.system.dto.UpdateProfileRequest;
import com.aiinterview.system.dto.UserQuery;
import com.aiinterview.system.dto.UserVO;
import com.aiinterview.system.entity.SysUser;
import com.aiinterview.system.enums.UserRole;
import com.aiinterview.system.enums.UserStatus;
import com.aiinterview.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 用户服务：个人资料、密码、状态与后台分页管理。
 *
 * <p>角色校验放在 Controller 的 {@code @PreAuthorize} 上，Service 只处理业务规则。</p>
 */
@Service
public class UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 修改当前登录用户的个人资料。
     *
     * @param request 资料修改请求
     * @return 修改后的用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(UpdateProfileRequest request) {
        SysUser user = requireUser(SecurityUtils.currentUserId());
        user.setRealName(request.realName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setAvatar(request.avatar());
        userMapper.updateById(user);
        return UserVO.from(user);
    }

    /**
     * 修改当前登录用户的密码。
     *
     * <p>必须校验原密码，且新密码不能与原密码相同。</p>
     *
     * @param request 密码修改请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        SysUser user = requireUser(SecurityUtils.currentUserId());
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw BizException.badRequest("原密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw BizException.badRequest("新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
    }

    /**
     * 修改指定用户状态（仅管理员，权限由 Controller 保证）。
     *
     * @param userId 用户 id
     * @param status 目标状态
     * @return 修改后的用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateStatus(Long userId, UserStatus status) {
        if (status == null) {
            throw BizException.badRequest("用户状态不能为空");
        }
        SysUser user = requireUser(userId);
        user.setStatus(status);
        userMapper.updateById(user);
        return UserVO.from(user);
    }

    /**
     * 分页查询用户（仅管理员，权限由 Controller 保证）。
     *
     * @param query 查询条件，支持关键词（用户名/真实姓名）、角色与状态过滤
     * @return 用户分页数据
     */
    public PageResult<UserVO> page(UserQuery query) {
        String keyword = query.getKeyword();
        UserRole role = parseRole(query.getRole());
        UserStatus status = parseStatus(query.getStatus());
        Page<SysUser> page = new Page<>(query.pageOrDefault(), query.sizeOrDefault());
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .and(TextUtils.isBlank(keyword) == false, w -> w
                        .like(SysUser::getUsername, keyword)
                        .or()
                        .like(SysUser::getRealName, keyword))
                .eq(role != null, SysUser::getRole, role)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> result = userMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(UserVO::from).toList());
    }

    /**
     * 按 id 查询用户信息，本人或管理员可访问。
     *
     * @param userId 用户 id
     * @return 用户信息
     */
    public UserVO getById(Long userId) {
        SecurityUtils.requireOwner(userId);
        return UserVO.from(requireUser(userId));
    }

    /**
     * 按 id 查询用户实体，不存在时抛 404 业务异常；供其他模块复用。
     *
     * @param userId 用户 id
     * @return 用户实体
     */
    public SysUser requireUser(Long userId) {
        if (userId == null) {
            throw BizException.notFound("用户不存在");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    /**
     * 解析角色参数，空白返回 {@code null} 表示不过滤，非法值抛 400。
     *
     * @param raw 原始角色字符串
     * @return 角色枚举或 null
     */
    private UserRole parseRole(String raw) {
        if (TextUtils.isBlank(raw)) {
            return null;
        }
        try {
            return UserRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BizException.badRequest("用户角色不合法：" + raw);
        }
    }

    /**
     * 解析状态参数，空白返回 {@code null} 表示不过滤，非法值抛 400。
     *
     * @param raw 原始状态字符串
     * @return 状态枚举或 null
     */
    private UserStatus parseStatus(String raw) {
        if (TextUtils.isBlank(raw)) {
            return null;
        }
        try {
            return UserStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BizException.badRequest("用户状态不合法：" + raw);
        }
    }
}
