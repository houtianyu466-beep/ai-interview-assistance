package com.aiinterview.video.controller;

import com.aiinterview.common.api.Result;
import com.aiinterview.common.log.OperationLog;
import com.aiinterview.security.SecurityUtils;
import com.aiinterview.video.dto.CreateVideoRoomRequest;
import com.aiinterview.video.dto.IceConfigVO;
import com.aiinterview.video.dto.VideoRoomVO;
import com.aiinterview.video.service.VideoRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 视频面试接口：房间创建/查询/结束与 ICE 配置。
 *
 * <p>媒体流由前端 WebRTC 直连，后端只负责房间元信息；实时信令走
 * {@code /ws/call?token=...&code=...&name=...}。</p>
 */
@Tag(name = "视频面试")
@RestController
@RequestMapping("/api/v1/video")
@Validated
public class VideoController {

    private final VideoRoomService videoRoomService;

    /**
     * 构造器注入。
     *
     * @param videoRoomService 视频房间服务
     */
    public VideoController(VideoRoomService videoRoomService) {
        this.videoRoomService = videoRoomService;
    }

    /**
     * 创建视频房间。
     *
     * @param request 创建请求
     * @return 新建的房间
     */
    @Operation(summary = "创建视频房间")
    @OperationLog("创建视频房间")
    @PostMapping("/rooms")
    public Result<VideoRoomVO> create(@Valid @RequestBody CreateVideoRoomRequest request) {
        return Result.ok(videoRoomService.create(request));
    }

    /**
     * 按房间码查询视频房间。
     *
     * @param code 房间码
     * @return 房间信息
     */
    @Operation(summary = "查询视频房间")
    @GetMapping("/rooms/{code}")
    public Result<VideoRoomVO> get(@PathVariable String code) {
        return Result.ok(videoRoomService.get(code));
    }

    /**
     * 结束视频房间，仅房主可调用。
     *
     * @param code 房间码
     * @return 结束后的房间
     */
    @Operation(summary = "结束视频房间")
    @OperationLog("结束视频房间")
    @PostMapping("/rooms/{code}/end")
    public Result<VideoRoomVO> end(@PathVariable String code) {
        return Result.ok(videoRoomService.end(code, SecurityUtils.currentUserId()));
    }

    /**
     * 查询 WebRTC ICE 配置。
     *
     * @return ICE 服务器列表
     */
    @Operation(summary = "查询 ICE 配置")
    @GetMapping("/ice")
    public Result<IceConfigVO> iceConfig() {
        return Result.ok(videoRoomService.iceConfig());
    }
}
