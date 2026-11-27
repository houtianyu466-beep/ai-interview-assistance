package com.aiinterview.resume.dto;

import com.aiinterview.resume.entity.Resume;

import java.time.LocalDateTime;

/**
 * 简历列表/摘要视图。
 *
 * @param id                简历 ID
 * @param fileName          原始文件名（手动录入时为「手动输入简历」）
 * @param yearsOfExperience 工作年限
 * @param education         学历
 * @param skills            技能标签，英文逗号分隔
 * @param status            解析状态：UPLOADED / PARSING / PARSED / FAILED
 * @param errorMessage      解析失败原因
 * @param createdAt         创建时间
 */
public record ResumeVO(
        Long id,
        String fileName,
        Integer yearsOfExperience,
        String education,
        String skills,
        String status,
        String errorMessage,
        LocalDateTime createdAt) {

    /**
     * 由实体转换视图。
     *
     * @param resume 简历实体，可为 null
     * @return 视图对象，入参为 null 时返回 null
     */
    public static ResumeVO from(Resume resume) {
        if (resume == null) {
            return null;
        }
        return new ResumeVO(
                resume.getId(),
                resume.getFileName(),
                resume.getYearsOfExperience(),
                resume.getEducation(),
                resume.getSkills(),
                resume.getStatus() == null ? null : resume.getStatus().name(),
                resume.getErrorMessage(),
                resume.getCreatedAt());
    }
}
