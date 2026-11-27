package com.aiinterview.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * 文件存储抽象。当前为本地磁盘实现，后续可替换为对象存储（OSS/S3）。
 */
public interface FileStorageService {

    /**
     * 保存文件并返回相对路径。
     *
     * @param file 上传文件
     * @param dir  逻辑目录（如 resume）
     * @return 相对路径，例如 {@code resume/2026/11/26/uuid.docx}
     */
    String store(MultipartFile file, String dir);

    /**
     * 把相对路径解析为绝对路径，并防止路径穿越。
     */
    Path resolve(String relativePath);

    void delete(String relativePath);
}
