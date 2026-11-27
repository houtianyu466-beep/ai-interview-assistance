package com.aiinterview.ai.model;

import java.util.List;

/**
 * 结构化简历（AI 解析结果）。
 *
 * <p>用 record 承载，字段与 Prompt 中约定的 JSON Schema 一一对应；
 * 紧凑构造器负责把 null 集合归一化为空集合，避免调用方到处判空。</p>
 */
public record ParsedResume(
        String name,
        String email,
        String phone,
        Integer yearsOfExperience,
        String education,
        List<String> skills,
        List<WorkExperience> workExperiences,
        List<ProjectExperience> projects,
        List<String> highlights,
        String summary) {

    public ParsedResume {
        skills = skills == null ? List.of() : List.copyOf(skills);
        workExperiences = workExperiences == null ? List.of() : List.copyOf(workExperiences);
        projects = projects == null ? List.of() : List.copyOf(projects);
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
    }
}
