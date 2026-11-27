package com.aiinterview.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 手工粘贴简历文本的请求体。
 *
 * @param content 简历纯文本，上限 20000 字
 */
public record ResumeTextRequest(
        @NotBlank(message = "简历内容不能为空")
        @Size(max = 20000, message = "简历内容不能超过 20000 字")
        String content) {
}
