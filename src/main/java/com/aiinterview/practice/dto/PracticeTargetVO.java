package com.aiinterview.practice.dto;

import com.aiinterview.common.util.TextUtils;
import com.aiinterview.practice.entity.PracticeTarget;

import java.util.List;

/**
 * 练习方向视图。
 *
 * @param id               方向 id
 * @param code             方向编码
 * @param title            方向名称
 * @param subtitle         副标题
 * @param category         方向分类
 * @param description      方向描述
 * @param focusTopics      重点考察方向（由逗号分隔的字符串拆分）
 * @param defaultQuestions 默认题数
 * @param difficulty       默认难度 1-5
 * @param icon             前端图标名
 */
public record PracticeTargetVO(
        Long id,
        String code,
        String title,
        String subtitle,
        String category,
        String description,
        List<String> focusTopics,
        Integer defaultQuestions,
        Integer difficulty,
        String icon) {

    /**
     * 由练习方向实体构建视图，{@code focusTopics} 按逗号拆分。
     *
     * @param target 练习方向实体
     * @return 练习方向视图
     */
    public static PracticeTargetVO from(PracticeTarget target) {
        return new PracticeTargetVO(
                target.getId(),
                target.getCode(),
                target.getTitle(),
                target.getSubtitle(),
                target.getCategory(),
                target.getDescription(),
                TextUtils.splitCsv(target.getFocusTopics()),
                target.getDefaultQuestions(),
                target.getDifficulty(),
                target.getIcon());
    }
}
