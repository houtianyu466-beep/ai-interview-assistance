package com.aiinterview.ai.voice;

import com.aiinterview.common.api.Result;
import com.aiinterview.common.util.TextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 语音合成接口。
 *
 * <p>前端 AI 视频面试用它在朗读题目时播放更自然的声音；未配置合成服务时返回 503，
 * 前端自动退回浏览器 {@code speechSynthesis}。</p>
 */
@Tag(name = "语音", description = "语音转写与语音合成")
@RestController
@RequestMapping("/api/v1/voice")
public class TtsController {

    private final TtsClient ttsClient;
    private final TtsProperties properties;

    /**
     * 构造器注入。
     *
     * @param ttsClient  合成客户端
     * @param properties 合成配置
     */
    public TtsController(TtsClient ttsClient, TtsProperties properties) {
        this.ttsClient = ttsClient;
        this.properties = properties;
    }

    /**
     * 合成语音，直接返回音频字节。
     *
     * @param request 合成请求
     * @return 音频响应；未配置或失败时由全局异常处理返回 JSON 错误
     */
    @Operation(summary = "合成语音", description = "返回 audio/mpeg 音频字节；provider=none 时返回 503")
    @PostMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> synthesize(@RequestBody TtsRequest request) {
        String text = TextUtils.defaultIfBlank(request.text(), "");
        if (TextUtils.isBlank(text)) {
            return ResponseEntity.badRequest().build();
        }
        String clipped = text.length() > properties.getMaxChars()
                ? text.substring(0, properties.getMaxChars())
                : text;
        TtsResult result = ttsClient.synthesize(clipped, request.voice());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-TTS-Provider", result.provider())
                .header("X-TTS-Latency", String.valueOf(result.latencyMs()))
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.audio());
    }

    /**
     * 查询合成能力是否可用，供前端决定是否提前降级。
     *
     * @return 能力信息
     */
    @Operation(summary = "语音合成能力", description = "返回是否可用与当前音色，前端据此选择播放方式")
    @GetMapping("/tts/info")
    public Result<Map<String, Object>> info() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", ttsClient.enabled());
        data.put("provider", ttsClient.providerName());
        data.put("voice", properties.getVoice());
        data.put("model", properties.getModel());
        return Result.ok(data);
    }

    /**
     * 合成请求。
     *
     * @param text  待合成文本
     * @param voice 音色，可为空
     */
    public record TtsRequest(String text, String voice) {
    }
}
