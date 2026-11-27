package com.aiinterview.question.service;

import com.aiinterview.common.api.PageResult;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.question.dto.QuestionQuery;
import com.aiinterview.question.dto.QuestionSaveRequest;
import com.aiinterview.question.dto.QuestionVO;
import com.aiinterview.question.entity.QuestionBank;
import com.aiinterview.question.mapper.QuestionBankMapper;
import com.aiinterview.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 题库业务服务：提供题目的增删改查，以及面试出题失败时的随机选题降级能力。
 *
 * <p>题目分两类：绑定岗位的专属题（{@code position_id} 非空）与通用题（{@code position_id} 为空）。
 * 随机选题时优先使用岗位专属题，数量不足才用通用题补齐。</p>
 */
@Service
public class QuestionService {

    /** 人工录入的题目来源标记 */
    private static final String SOURCE_MANUAL = "MANUAL";

    private final QuestionBankMapper questionBankMapper;

    /**
     * 构造器注入。
     *
     * @param questionBankMapper 题库 Mapper
     */
    public QuestionService(QuestionBankMapper questionBankMapper) {
        this.questionBankMapper = questionBankMapper;
    }

    /**
     * 分页查询题库，支持按岗位、分类、难度过滤，关键词模糊匹配题干与标签。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public PageResult<QuestionVO> page(QuestionQuery query) {
        Page<QuestionBank> page = new Page<>(query.pageOrDefault(), query.sizeOrDefault());
        String keyword = query.getKeyword();
        LambdaQueryWrapper<QuestionBank> wrapper = Wrappers.<QuestionBank>lambdaQuery()
                .eq(query.getPositionId() != null, QuestionBank::getPositionId, query.getPositionId())
                .eq(TextUtils.isBlank(query.getCategory()) == false, QuestionBank::getCategory, query.getCategory())
                .eq(query.getDifficulty() != null, QuestionBank::getDifficulty, query.getDifficulty())
                .and(TextUtils.isBlank(keyword) == false, condition -> condition
                        .like(QuestionBank::getContent, keyword)
                        .or()
                        .like(QuestionBank::getTags, keyword))
                .orderByDesc(QuestionBank::getCreatedAt)
                .orderByDesc(QuestionBank::getId);
        Page<QuestionBank> result = questionBankMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(QuestionVO::from).toList());
    }

    /**
     * 查询题目详情。
     *
     * @param id 题目 id
     * @return 题目详情
     * @throws BizException 题目不存在时抛出 notFound
     */
    public QuestionVO detail(Long id) {
        return QuestionVO.from(requireQuestion(id));
    }

    /**
     * 创建题目，来源固定为人工录入（MANUAL），创建人为当前登录用户。
     *
     * @param request 保存请求
     * @return 创建后的题目
     */
    @Transactional(rollbackFor = Exception.class)
    public QuestionVO create(QuestionSaveRequest request) {
        QuestionBank entity = new QuestionBank();
        applyRequest(entity, request);
        entity.setSource(SOURCE_MANUAL);
        entity.setCreatedBy(SecurityUtils.currentUserId());
        questionBankMapper.insert(entity);
        return QuestionVO.from(entity);
    }

    /**
     * 更新题目。请求中的字段整份覆盖，{@code positionId} 传空表示改为通用题。
     *
     * @param id      题目 id
     * @param request 保存请求
     * @return 更新后的题目
     * @throws BizException 题目不存在时抛出 notFound
     */
    @Transactional(rollbackFor = Exception.class)
    public QuestionVO update(Long id, QuestionSaveRequest request) {
        QuestionBank existing = requireQuestion(id);
        LambdaUpdateWrapper<QuestionBank> wrapper = Wrappers.<QuestionBank>lambdaUpdate()
                .eq(QuestionBank::getId, id)
                .set(QuestionBank::getPositionId, request.positionId())
                .set(QuestionBank::getCategory, request.category().trim())
                .set(QuestionBank::getDifficulty, request.difficulty())
                .set(QuestionBank::getContent, request.content().trim())
                .set(QuestionBank::getReferenceAnswer, request.referenceAnswer())
                .set(QuestionBank::getKeywords, request.keywords())
                .set(QuestionBank::getTags, request.tags())
                .set(QuestionBank::getUpdatedAt, LocalDateTime.now());
        questionBankMapper.update(null, wrapper);

        existing.setPositionId(request.positionId());
        existing.setCategory(request.category().trim());
        existing.setDifficulty(request.difficulty());
        existing.setContent(request.content().trim());
        existing.setReferenceAnswer(request.referenceAnswer());
        existing.setKeywords(request.keywords());
        existing.setTags(request.tags());
        existing.setUpdatedAt(LocalDateTime.now());
        return QuestionVO.from(existing);
    }

    /**
     * 删除题目。
     *
     * @param id 题目 id
     * @throws BizException 题目不存在时抛出 notFound
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireQuestion(id);
        questionBankMapper.deleteById(id);
    }

    /**
     * 随机选题，供 AI 出题失败时降级使用。
     *
     * <p>选题规则：</p>
     * <ol>
     *     <li>优先取岗位专属题（可再按分类过滤），数量不足时再用 {@code position_id IS NULL} 的通用题补齐；</li>
     *     <li>候选内部按难度升序排列，同难度内部随机打乱；</li>
     *     <li>最终结果再按难度升序稳定排序，保证基础题优先出现；</li>
     *     <li>最多返回 {@code limit} 条，无数据返回空列表。</li>
     * </ol>
     *
     * @param positionId 岗位 id，为空表示只取通用题
     * @param category   题目分类，为空表示不限分类
     * @param limit      需要返回的题目数量
     * @return 题目列表，不返回 {@code null}
     */
    public List<QuestionBank> pickRandom(Long positionId, String category, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        // 多取一些候选，避免随机后可选范围过窄；库内排序只做粗排，真正打散在内存中完成
        int fetchSize = limit * 3;
        List<QuestionBank> selected = new ArrayList<>(Math.min(limit, fetchSize));
        if (positionId != null) {
            selected.addAll(randomizedByDifficulty(selectOrdered(positionId, category, fetchSize)));
        }
        if (selected.size() < limit) {
            selected.addAll(randomizedByDifficulty(selectOrdered(null, category, fetchSize)));
        }
        if (selected.size() > limit) {
            selected = new ArrayList<>(selected.subList(0, limit));
        }
        // 稳定排序：同难度保持上一步的随机顺序
        selected.sort(Comparator.comparingInt(question ->
                question.getDifficulty() == null ? Integer.MAX_VALUE : question.getDifficulty()));
        return selected;
    }

    /**
     * 把候选题目按难度升序排列，同难度内部随机打乱。
     *
     * @param candidates 候选题目
     * @return 排序后的新列表
     */
    private List<QuestionBank> randomizedByDifficulty(List<QuestionBank> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<QuestionBank>> byDifficulty = new TreeMap<>();
        for (QuestionBank question : candidates) {
            int difficulty = question.getDifficulty() == null ? Integer.MAX_VALUE : question.getDifficulty();
            byDifficulty.computeIfAbsent(difficulty, key -> new ArrayList<>()).add(question);
        }
        List<QuestionBank> ordered = new ArrayList<>(candidates.size());
        for (List<QuestionBank> group : byDifficulty.values()) {
            Collections.shuffle(group, ThreadLocalRandom.current());
            ordered.addAll(group);
        }
        return ordered;
    }

    /**
     * 查询题目，不存在时抛出 notFound。
     *
     * @param id 题目 id
     * @return 题目实体
     */
    private QuestionBank requireQuestion(Long id) {
        QuestionBank question = id == null ? null : questionBankMapper.selectById(id);
        if (question == null) {
            throw BizException.notFound("题目不存在");
        }
        return question;
    }

    /**
     * 把保存请求中的可写字段复制到实体。
     *
     * @param entity  目标实体
     * @param request 保存请求
     */
    private void applyRequest(QuestionBank entity, QuestionSaveRequest request) {
        entity.setPositionId(request.positionId());
        entity.setCategory(request.category().trim());
        entity.setDifficulty(request.difficulty());
        entity.setContent(request.content().trim());
        entity.setReferenceAnswer(request.referenceAnswer());
        entity.setKeywords(request.keywords());
        entity.setTags(request.tags());
    }

    /**
     * 按来源查询候选题目：岗位 id 为空查通用题，否则查该岗位专属题。
     * SQL 中先按难度升序取有限条，避免全表加载；最终顺序由调用方在内存中打乱。
     *
     * @param positionId 岗位 id，为空表示通用题
     * @param category   分类，为空表示不限
     * @param fetchSize  最多查询条数
     * @return 候选题目列表
     */
    private List<QuestionBank> selectOrdered(Long positionId, String category, int fetchSize) {
        LambdaQueryWrapper<QuestionBank> wrapper = Wrappers.<QuestionBank>lambdaQuery()
                .isNull(positionId == null, QuestionBank::getPositionId)
                .eq(positionId != null, QuestionBank::getPositionId, positionId)
                .eq(TextUtils.isBlank(category) == false, QuestionBank::getCategory, category)
                .orderByAsc(QuestionBank::getDifficulty)
                .orderByAsc(QuestionBank::getId)
                .last("LIMIT " + fetchSize);
        return questionBankMapper.selectList(wrapper);
    }
}
