package com.aiinterview.ai.voice;

import com.aiinterview.ai.voice.dto.TranscriptionVO;
import com.aiinterview.common.api.Result;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.log.OperationLog;
import com.aiinterview.infrastructure.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * 语音录制转写接口：练习者上传录音，服务端转写为文字。
 *
 * <p>流程：校验文件（非空、大小、扩展名）→ 落盘到 {@code voice} 目录 → 调用 {@link AsrClient}
 * → 返回文字与落盘路径。转写属于 AI 能力，未配置真实服务时由 {@link UnavailableAsrClient}
 * 返回带引导文案的错误，前端可提示用户改用文字作答。</p>
 *
 * <p>该接口需要登录（沿用全局安全策略），资源不区分归属：音频仅用于本次转写。</p>
 */
@Tag(name = "语音转写")
@RestController
@RequestMapping("/api/v1/voice")
public class VoiceController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("webm", "wav", "mp3", "m4a", "ogg");

    private final AsrClient asrClient;
    private final VoiceProperties properties;
    private final FileStorageService storage;

    /**
     * 构造器注入：转写客户端、语音配置与文件存储。
     *
     * @param asrClient  语音转写客户端（mock 或 openai，由配置决定）
     * @param properties 语音转写配置
     * @param storage    文件存储服务
     */
    public VoiceController(AsrClient asrClient, VoiceProperties properties, FileStorageService storage) {
        this.asrClient = asrClient;
        this.properties = properties;
        this.storage = storage;
    }

    /**
     * 上传录音并转写为文字。
     *
     * <p>language 不传时使用配置的默认语言（{@code app.voice.language}）。</p>
     *
     * @param file     音频文件，支持 webm / wav / mp3 / m4a / ogg
     * @param language 识别语言（如 zh、en），可选
     * @return 转写文字、提供方、模型、耗时与音频落盘路径
     */
    @Operation(summary = "语音转写")
    @OperationLog("语音转写")
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<TranscriptionVO> transcribe(@RequestPart("file") MultipartFile file,
                                              @RequestParam(value = "language", required = false) String language) {
        validate(file);
        String audioPath = storage.store(file, "voice");
        Path path = storage.resolve(audioPath);
        String effectiveLanguage = (language == null || language.isBlank()) ? properties.getLanguage() : language;
        AsrResult result = asrClient.transcribe(path, effectiveLanguage);
        return Result.ok(new TranscriptionVO(
                result.text(),
                result.provider(),
                result.model(),
                result.latencyMs(),
                audioPath));
    }

    /**
     * 校验音频文件：非空、不超过配置上限、扩展名在白名单内。
     *
     * <p>这些错误都属于请求参数问题，统一返回 HTTP 400，便于前端直接提示用户重新录音。</p>
     *
     * @param file 上传文件
     */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请上传语音文件");
        }
        if (file.getSize() > properties.getMaxSize()) {
            throw BizException.badRequest("语音文件不能超过 " + (properties.getMaxSize() / (1024 * 1024)) + "MB");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw BizException.badRequest("仅支持 webm / wav / mp3 / m4a / ogg 格式");
        }
    }

    /**
     * 取小写扩展名，没有扩展名时返回空串。
     *
     * @param fileName 原始文件名，可为 null
     * @return 小写扩展名（不含点）
     */
    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
