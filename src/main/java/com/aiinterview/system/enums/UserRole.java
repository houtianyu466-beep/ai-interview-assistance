package com.aiinterview.system.enums;

/**
 * 用户角色。角色决定接口访问权限：
 * <ul>
 *     <li>CANDIDATE：候选人，可维护自己的简历、参加面试、查看报告；</li>
 *     <li>INTERVIEWER：面试官，可维护岗位与题库、查看所有面试记录；</li>
 *     <li>ADMIN：管理员，拥有全部权限并可管理用户。</li>
 * </ul>
 */
public enum UserRole {
    CANDIDATE,
    INTERVIEWER,
    ADMIN
}
