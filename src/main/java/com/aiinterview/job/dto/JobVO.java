package com.aiinterview.job.dto;

import com.aiinterview.job.entity.JobPosition;

import java.time.LocalDateTime;

/**
 * 岗位视图对象。
 *
 * @param id           岗位 id
 * @param title        岗位名称
 * @param department   所属部门
 * @param level        岗位级别（JUNIOR / MIDDLE / SENIOR）
 * @param description  岗位职责
 * @param requirements 任职要求
 * @param status       岗位状态（DRAFT / OPEN / CLOSED）
 * @param createdBy    创建人 id
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 */
public record JobVO(Long id,
                    String title,
                    String department,
                    String level,
                    String description,
                    String requirements,
                    String status,
                    Long createdBy,
                    LocalDateTime createdAt,
                    LocalDateTime updatedAt) {

    /**
     * 由实体转换为视图对象。
     *
     * @param job 岗位实体
     * @return 岗位视图对象，入参为 null 时返回 null
     */
    public static JobVO from(JobPosition job) {
        if (job == null) {
            return null;
        }
        String level = job.getLevel() == null ? null : job.getLevel().name();
        String status = job.getStatus() == null ? null : job.getStatus().name();
        return new JobVO(job.getId(), job.getTitle(), job.getDepartment(), level, job.getDescription(),
                job.getRequirements(), status, job.getCreatedBy(), job.getCreatedAt(), job.getUpdatedAt());
    }
}
