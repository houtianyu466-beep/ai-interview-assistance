package com.aiinterview.interview.service;

import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.entity.InterviewQuestion;
import com.aiinterview.interview.mapper.InterviewMapper;
import com.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 练习历史题干查询服务。
 *
 * <p>出题器需要在 Prompt 中携带「最近已经问过的题目」，并对 AI 结果做二次去重，
 * 因此这里统一负责「最近 N 场会话 → 题目题干」的查询，避免出题器直接拼 SQL。
 * 查询失败只记录 warn 并返回空列表，绝不阻断练习创建。</p>
 */
@Service
public class QuestionHistoryService {

    private static final Logger log = LoggerFactory.getLogger(QuestionHistoryService.class);

    /** 默认回看的最近练习场数 */
    private static final int RECENT_SESSION_LIMIT = 5;

    /** 单次最多读取的历史题干条数，避免历史过长挤占 Prompt 与内存 */
    private static final int MAX_HISTORY_QUESTION_FETCH = 120;

    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper questionMapper;

    /**
     * 构造器注入。
     *
     * @param interviewMapper  面试会话 Mapper，用于取最近练习场次
     * @param questionMapper   面试题目 Mapper，用于取这些场次已出过的题干
     */
    public QuestionHistoryService(InterviewMapper interviewMapper,
                                  InterviewQuestionMapper questionMapper) {
        this.interviewMapper = interviewMapper;
        this.questionMapper = questionMapper;
    }

    /**
     * 查询指定用户最近若干场练习已出过的题干。
     *
     * <p>按会话创建时间与 id 倒序取最近 {@value #RECENT_SESSION_LIMIT} 场（可排除当前会话），
     * 再按题目创建时间倒序读取题干；结果按题干去重，最多 {@value #MAX_HISTORY_QUESTION_FETCH} 条。</p>
     *
     * @param userId             用户 id，为空时直接返回空列表
     * @param excludeInterviewId 需要排除的会话 id（通常是当前正在创建的会话），可为空
     * @return 历史题干列表，按最近优先排列，不返回 {@code null}
     */
    public List<String> recentQuestionContents(Long userId, Long excludeInterviewId) {
        if (userId == null) {
            return List.of();
        }
        try {
            List<Interview> recentSessions = interviewMapper.selectList(Wrappers.<Interview>lambdaQuery()
                    .eq(Interview::getUserId, userId)
                    .ne(excludeInterviewId != null, Interview::getId, excludeInterviewId)
                    .orderByDesc(Interview::getCreatedAt)
                    .orderByDesc(Interview::getId)
                    .last("LIMIT " + RECENT_SESSION_LIMIT));
            if (recentSessions == null || recentSessions.isEmpty()) {
                return List.of();
            }
            List<Long> interviewIds = recentSessions.stream()
                    .map(Interview::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (interviewIds.isEmpty()) {
                return List.of();
            }
            List<InterviewQuestion> questions = questionMapper.selectList(Wrappers.<InterviewQuestion>lambdaQuery()
                    .in(InterviewQuestion::getInterviewId, interviewIds)
                    .orderByDesc(InterviewQuestion::getCreatedAt)
                    .orderByDesc(InterviewQuestion::getId)
                    .last("LIMIT " + MAX_HISTORY_QUESTION_FETCH));
            if (questions == null || questions.isEmpty()) {
                return List.of();
            }
            Set<String> contents = new LinkedHashSet<>();
            for (InterviewQuestion question : questions) {
                if (question == null || TextUtils.isBlank(question.getContent())) {
                    continue;
                }
                contents.add(question.getContent().trim());
            }
            return new ArrayList<>(contents);
        } catch (RuntimeException e) {
            log.warn("查询历史题干失败，按无历史处理 userId={} reason={}", userId, e.getMessage());
            return List.of();
        }
    }
}
