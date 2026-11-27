package com.aiinterview.video.service;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.mapper.InterviewMapper;
import com.aiinterview.security.SecurityUtils;
import com.aiinterview.video.config.VideoProperties;
import com.aiinterview.video.dto.CreateVideoRoomRequest;
import com.aiinterview.video.dto.IceConfigVO;
import com.aiinterview.video.dto.IceServerVO;
import com.aiinterview.video.dto.VideoRoomVO;
import com.aiinterview.video.entity.VideoRoom;
import com.aiinterview.video.mapper.VideoRoomMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 视频面试房间服务：创建、查看、结束房间，以及下发 ICE 配置。
 *
 * <p>媒体流由前端 WebRTC 直连，本服务只负责房间元信息与房间码；
 * 信令转发见 {@code com.aiinterview.video.ws} 包。</p>
 */
@Service
public class VideoRoomService {

    private static final Logger log = LoggerFactory.getLogger(VideoRoomService.class);

    /** 房间码字符集：大写字母 + 数字，排除易混淆的 0/O/1/I。 */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    /** 房间码长度。 */
    private static final int CODE_LENGTH = 8;
    /** 房间码唯一性冲突时的最大尝试次数。 */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VideoRoomMapper videoRoomMapper;
    private final InterviewMapper interviewMapper;
    private final VideoProperties videoProperties;

    /**
     * 构造器注入。
     *
     * @param videoRoomMapper 视频房间 Mapper
     * @param interviewMapper 面试会话 Mapper，用于校验练习会话归属
     * @param videoProperties 视频面试配置
     */
    public VideoRoomService(VideoRoomMapper videoRoomMapper,
                            InterviewMapper interviewMapper,
                            VideoProperties videoProperties) {
        this.videoRoomMapper = videoRoomMapper;
        this.interviewMapper = interviewMapper;
        this.videoProperties = videoProperties;
    }

    /**
     * 创建视频房间。
     *
     * <p>规则：</p>
     * <ul>
     *     <li>{@code mode} 仅允许 {@code AI}/{@code HUMAN}；</li>
     *     <li>AI 模式必须传 {@code practiceSessionId}，且会话存在并归当前用户所有；</li>
     *     <li>房间码唯一性冲突时最多重试 {@value #MAX_CODE_ATTEMPTS} 次。</li>
     * </ul>
     *
     * @param request 创建请求
     * @return 新建的房间视图
     * @throws BizException 参数非法（400）、练习会话不存在（404）、不属于当前用户（403）或房间码生成失败
     */
    public VideoRoomVO create(CreateVideoRoomRequest request) {
        if (request == null) {
            throw BizException.badRequest("创建视频房间的请求不能为空");
        }
        String mode = normalizeMode(request.mode());
        Long userId = SecurityUtils.currentUserId();
        Long practiceSessionId = request.practiceSessionId();

        if (VideoRoom.MODE_AI.equals(mode)) {
            requireOwnedPracticeSession(practiceSessionId, userId);
        }

        VideoRoom room = new VideoRoom();
        room.setMode(mode);
        room.setPracticeSessionId(practiceSessionId);
        room.setHostUserId(userId);
        room.setStatus(VideoRoom.STATUS_WAITING);
        insertWithUniqueCode(room);
        log.info("视频房间已创建，code={}，mode={}，hostUserId={}", room.getCode(), mode, userId);
        return VideoRoomVO.from(room);
    }

    /**
     * 按房间码查询房间，登录用户均可查看。
     *
     * @param code 房间码
     * @return 房间视图
     * @throws BizException 房间码为空（400）或房间不存在（404）
     */
    public VideoRoomVO get(String code) {
        return VideoRoomVO.from(requireRoom(code));
    }

    /**
     * 结束视频房间，仅房主可操作；已结束的房间直接返回，保证幂等。
     *
     * @param code   房间码
     * @param userId 当前用户 id
     * @return 结束后的房间视图
     * @throws BizException 房间不存在（404）、操作者非房主（403）
     */
    public VideoRoomVO end(String code, Long userId) {
        VideoRoom room = requireRoom(code);
        if (userId == null || !userId.equals(room.getHostUserId())) {
            throw BizException.forbidden("仅房主可以结束视频房间");
        }
        if (VideoRoom.STATUS_ENDED.equals(room.getStatus())) {
            return VideoRoomVO.from(room);
        }
        room.setStatus(VideoRoom.STATUS_ENDED);
        room.setEndedAt(LocalDateTime.now());
        videoRoomMapper.updateById(room);
        log.info("视频房间已结束，code={}，hostUserId={}", room.getCode(), userId);
        return VideoRoomVO.from(room);
    }

    /**
     * 查询前端所需的 ICE 配置。
     *
     * <p>配置中的每个字符串转换为一条 {@link IceServerVO}：{@code urls} 为单元素列表，
     * TURN 用户名与凭证当前不支持下配置，固定为 {@code null}。</p>
     *
     * @return ICE 配置
     */
    public IceConfigVO iceConfig() {
        List<String> configured = videoProperties.getIceServers();
        if (configured == null) {
            configured = List.of(VideoProperties.DEFAULT_ICE_SERVER);
        }
        List<IceServerVO> servers = configured.stream()
                .filter(url -> !TextUtils.isBlank(url))
                .map(url -> new IceServerVO(List.of(url.trim()), null, null))
                .toList();
        return new IceConfigVO(servers);
    }

    /**
     * 校验 AI 模式关联的练习会话：必填、存在且属于当前用户。
     *
     * @param practiceSessionId 练习会话 id
     * @param userId            当前用户 id
     */
    private void requireOwnedPracticeSession(Long practiceSessionId, Long userId) {
        if (practiceSessionId == null) {
            throw BizException.badRequest("AI 模式必须关联练习会话 practiceSessionId");
        }
        Interview interview = interviewMapper.selectById(practiceSessionId);
        if (interview == null) {
            throw BizException.notFound("练习会话不存在");
        }
        if (!userId.equals(interview.getUserId())) {
            throw BizException.forbidden("无权使用他人的练习会话");
        }
    }

    /**
     * 生成唯一房间码并落库，冲突时重试。
     *
     * @param room 待插入的房间实体
     */
    private void insertWithUniqueCode(VideoRoom room) {
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            room.setId(null);
            room.setCode(randomCode());
            Long existing = videoRoomMapper.selectCount(Wrappers.<VideoRoom>lambdaQuery()
                    .eq(VideoRoom::getCode, room.getCode()));
            if (existing != null && existing > 0) {
                log.warn("视频房间码已存在，重新生成，code={}，attempt={}", room.getCode(), attempt);
                continue;
            }
            try {
                videoRoomMapper.insert(room);
                return;
            } catch (DuplicateKeyException e) {
                // 并发创建时预检查可能同时通过，最终由数据库唯一索引兜底
                log.warn("视频房间码唯一性冲突，重新生成，code={}，attempt={}", room.getCode(), attempt);
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "房间码生成失败，请稍后重试");
    }

    /**
     * 随机生成一个房间码。
     *
     * @return 8 位房间码
     */
    private String randomCode() {
        char[] chars = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            chars[i] = CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)];
        }
        return new String(chars);
    }

    /**
     * 校验并归一化模式：仅 AI/HUMAN，忽略大小写与首尾空白。
     *
     * @param rawMode 原始模式
     * @return 归一化后的模式
     */
    private String normalizeMode(String rawMode) {
        if (TextUtils.isBlank(rawMode)) {
            throw BizException.badRequest("mode 不能为空");
        }
        String mode = rawMode.trim().toUpperCase(Locale.ROOT);
        if (!VideoRoom.MODE_AI.equals(mode) && !VideoRoom.MODE_HUMAN.equals(mode)) {
            throw BizException.badRequest("mode 只能为 AI 或 HUMAN");
        }
        return mode;
    }

    /**
     * 按房间码加载房间，不存在时抛 404。
     *
     * @param code 房间码
     * @return 房间实体
     */
    private VideoRoom requireRoom(String code) {
        if (TextUtils.isBlank(code)) {
            throw BizException.badRequest("房间码不能为空");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        VideoRoom room = videoRoomMapper.selectOne(Wrappers.<VideoRoom>lambdaQuery()
                .eq(VideoRoom::getCode, normalized)
                .last("LIMIT 1"));
        if (room == null) {
            throw BizException.notFound("视频房间不存在");
        }
        return room;
    }
}
