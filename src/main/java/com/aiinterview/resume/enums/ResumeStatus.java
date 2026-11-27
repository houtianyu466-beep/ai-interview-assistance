package com.aiinterview.resume.enums;

/**
 * 简历解析状态。
 */
public enum ResumeStatus {
    /** 文件已上传，等待解析 */
    UPLOADED,
    /** 解析中（异步任务执行中） */
    PARSING,
    /** 解析成功 */
    PARSED,
    /** 解析失败 */
    FAILED
}
