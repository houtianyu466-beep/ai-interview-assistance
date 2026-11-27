package com.aiinterview.infrastructure.storage;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘存储实现。
 *
 * <p>安全要点：</p>
 * <ul>
 *     <li>扩展名白名单，拒绝可执行文件；</li>
 *     <li>文件名不参与落盘（用 UUID），避免中文/特殊字符与路径穿越；</li>
 *     <li>按 年/月/日 分目录，避免单目录文件过多。</li>
 * </ul>
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("txt", "md", "docx", "pdf", "webm", "wav", "mp3", "m4a", "ogg");

    private final StorageProperties properties;
    private final Path root;

    public LocalFileStorageService(StorageProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.getRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建存储根目录: " + root, e);
        }
        log.info("文件存储根目录: {}", root);
    }

    @Override
    public String store(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (file.getSize() > properties.getMaxSize()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件超过大小限制");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "仅支持 txt / md / docx / pdf / webm / wav / mp3 / m4a / ogg 格式");
        }
        LocalDate today = LocalDate.now();
        String relativeDir = "%s/%d/%02d/%02d".formatted(dir, today.getYear(), today.getMonthValue(), today.getDayOfMonth());
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetDir = root.resolve(relativeDir).normalize();
        Path target = targetDir.resolve(storedName).normalize();
        ensureInsideRoot(target);
        try {
            Files.createDirectories(targetDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件保存失败", e);
        }
        return relativeDir + "/" + storedName;
    }

    @Override
    public Path resolve(String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        ensureInsideRoot(target);
        return target;
    }

    @Override
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (Exception e) {
            log.warn("删除文件失败 path={}", relativePath, e);
        }
    }

    private void ensureInsideRoot(Path target) {
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法文件路径");
        }
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
