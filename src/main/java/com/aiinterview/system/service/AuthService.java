package com.aiinterview.system.service;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.infrastructure.cache.CacheStore;
import com.aiinterview.security.JwtAuthenticationFilter;
import com.aiinterview.security.JwtProperties;
import com.aiinterview.security.JwtTokenProvider;
import com.aiinterview.security.LoginUser;
import com.aiinterview.security.SecurityUtils;
import com.aiinterview.security.TokenPayload;
import com.aiinterview.system.dto.LoginRequest;
import com.aiinterview.system.dto.LoginResponse;
import com.aiinterview.system.dto.RegisterRequest;
import com.aiinterview.system.dto.UserVO;
import com.aiinterview.system.entity.SysUser;
import com.aiinterview.system.enums.UserRole;
import com.aiinterview.system.enums.UserStatus;
import com.aiinterview.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 认证服务：注册、登录、当前用户信息与登出。
 *
 * <p>安全要点：</p>
 * <ul>
 *     <li>注册固定为 CANDIDATE 角色，密码使用 BCrypt 加密存储；</li>
 *     <li>登录失败统一提示「用户名或密码错误」，不泄露账号是否存在；</li>
 *     <li>登出把令牌的 jti 写入黑名单缓存，TTL 为令牌剩余有效期。</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_TYPE = "Bearer";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final CacheStore cacheStore;

    public AuthService(SysUserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       JwtProperties jwtProperties,
                       CacheStore cacheStore) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.cacheStore = cacheStore;
    }

    /**
     * 注册候选人账号，成功后直接签发令牌，前端无需再调用登录接口。
     *
     * @param request 注册请求
     * @return 令牌与用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        Long count = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.username()));
        if (count != null && count > 0) {
            throw BizException.conflict("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        // 注册入口只允许候选人，面试官/管理员由管理员在后台创建
        user.setRole(UserRole.CANDIDATE);
        user.setStatus(UserStatus.ACTIVE);
        userMapper.insert(user);
        return buildLoginResponse(user);
    }

    /**
     * 账号密码登录。
     *
     * <p>用户不存在与密码错误返回同一提示，避免被用于枚举账号。</p>
     *
     * @param request 登录请求
     * @return 令牌与用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw BizException.forbidden("账号已被禁用，请联系管理员");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        return buildLoginResponse(user);
    }

    /**
     * 查询当前登录用户资料。
     *
     * @return 当前用户信息
     */
    public UserVO currentUserProfile() {
        SysUser user = userMapper.selectById(SecurityUtils.currentUserId());
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return UserVO.from(user);
    }

    /**
     * 登出：把当前令牌加入黑名单，使其在过期前无法继续使用。
     *
     * <p>令牌缺失、非法或已过期时静默返回，不向前端抛异常。</p>
     *
     * @param authorizationHeader 请求头 Authorization 的原始值
     */
    public void logout(String authorizationHeader) {
        if (TextUtils.isBlank(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return;
        }
        try {
            TokenPayload payload = jwtTokenProvider.parseToken(token);
            long remainingSeconds = Duration.between(Instant.now(), payload.expiresAt()).getSeconds();
            if (remainingSeconds <= 0) {
                return;
            }
            cacheStore.put(JwtAuthenticationFilter.BLACKLIST_PREFIX + payload.jti(),
                    "1", Duration.ofSeconds(remainingSeconds));
        } catch (Exception e) {
            // 令牌本身非法或已过期，登出保持幂等，仅记录调试日志
            log.debug("登出时令牌解析失败，已忽略：{}", e.getMessage());
        }
    }

    /**
     * 签发令牌并组装登录响应。
     *
     * @param user 已通过认证的用户
     * @return 令牌与用户信息
     */
    private LoginResponse buildLoginResponse(SysUser user) {
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRole().name());
        String accessToken = jwtTokenProvider.createToken(loginUser);
        return new LoginResponse(accessToken, TOKEN_TYPE,
                jwtProperties.getExpireMinutes() * 60, UserVO.from(user));
    }
}
