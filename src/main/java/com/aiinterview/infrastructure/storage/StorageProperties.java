package com.aiinterview.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储配置，对应 {@code app.storage.*}。
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** 存储根目录，支持相对路径（相对于启动目录） */
    private String root = "./data/uploads";
    /** 单文件最大字节数 */
    private long maxSize = 10 * 1024 * 1024;

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }
}
