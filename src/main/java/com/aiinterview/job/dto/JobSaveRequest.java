package com.aiinterview.job.dto;

import com.aiinterview.job.enums.JobLevel;
import com.aiinterview.job.enums.JobStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 岗位新增/更新请求。
 *
 * @param title        岗位名称
 * @param department   所属部门
 * @param level        岗位级别
 * @param description  岗位职责
 * @param requirements 任职要求
 * @param status       岗位状态，新增时为空默认 DRAFT
 */
public record JobSaveRequest(

        @NotBlank(message = "岗位名称不能为空")
        @Size(max = 128, message = "岗位名称长度不能超过 128 个字符")
        String title,

        String department,

        JobLevel level,

        String description,

        String requirements,

        JobStatus status
) {
}
