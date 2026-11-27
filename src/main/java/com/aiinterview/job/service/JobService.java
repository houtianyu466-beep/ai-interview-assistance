package com.aiinterview.job.service;

import com.aiinterview.common.api.PageResult;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.enums.InterviewStatus;
import com.aiinterview.interview.mapper.InterviewMapper;
import com.aiinterview.job.dto.JobQuery;
import com.aiinterview.job.dto.JobSaveRequest;
import com.aiinterview.job.dto.JobVO;
import com.aiinterview.job.entity.JobPosition;
import com.aiinterview.job.enums.JobStatus;
import com.aiinterview.job.mapper.JobPositionMapper;
import com.aiinterview.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 岗位服务：岗位的增删改查、状态流转，以及供面试模块复用的「必须为招聘中」校验。
 */
@Service
public class JobService {

    private final JobPositionMapper jobPositionMapper;
    private final InterviewMapper interviewMapper;

    public JobService(JobPositionMapper jobPositionMapper, InterviewMapper interviewMapper) {
        this.jobPositionMapper = jobPositionMapper;
        this.interviewMapper = interviewMapper;
    }

    /**
     * 分页查询岗位，可按关键字（岗位名称/部门）与状态过滤。
     *
     * @param query 查询条件
     * @return 岗位分页数据
     */
    public PageResult<JobVO> page(JobQuery query) {
        Page<JobPosition> page = new Page<>(query.pageOrDefault(), query.sizeOrDefault());
        String keyword = query.getKeyword();
        LambdaQueryWrapper<JobPosition> wrapper = Wrappers.<JobPosition>lambdaQuery()
                .and(TextUtils.isBlank(keyword) == false, w -> w
                        .like(JobPosition::getTitle, keyword)
                        .or()
                        .like(JobPosition::getDepartment, keyword))
                .eq(query.getStatus() != null, JobPosition::getStatus, query.getStatus())
                .orderByDesc(JobPosition::getCreatedAt);
        Page<JobPosition> result = jobPositionMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(JobVO::from).toList());
    }

    /**
     * 查询岗位详情。
     *
     * @param id 岗位 id
     * @return 岗位信息
     */
    public JobVO detail(Long id) {
        return JobVO.from(requireJob(id));
    }

    /**
     * 新建岗位，状态为空时默认草稿。
     *
     * @param request 岗位信息
     * @return 新建后的岗位
     */
    @Transactional(rollbackFor = Exception.class)
    public JobVO create(JobSaveRequest request) {
        JobPosition job = new JobPosition();
        applyRequest(job, request);
        if (job.getStatus() == null) {
            job.setStatus(JobStatus.DRAFT);
        }
        job.setCreatedBy(SecurityUtils.currentUserId());
        jobPositionMapper.insert(job);
        return JobVO.from(job);
    }

    /**
     * 更新岗位信息。
     *
     * @param id      岗位 id
     * @param request 岗位信息
     * @return 更新后的岗位
     */
    @Transactional(rollbackFor = Exception.class)
    public JobVO update(Long id, JobSaveRequest request) {
        JobPosition job = requireJob(id);
        applyRequest(job, request);
        jobPositionMapper.updateById(job);
        return JobVO.from(job);
    }

    /**
     * 删除岗位。
     *
     * <p>存在待开始（CREATED）或进行中（IN_PROGRESS）的面试引用该岗位时禁止删除。</p>
     *
     * @param id 岗位 id
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireJob(id);
        Long referencing = interviewMapper.selectCount(Wrappers.<Interview>lambdaQuery()
                .eq(Interview::getPositionId, id)
                .in(Interview::getStatus, InterviewStatus.CREATED, InterviewStatus.IN_PROGRESS));
        if (referencing != null && referencing > 0) {
            throw BizException.conflict("该岗位存在未结束的面试，无法删除");
        }
        jobPositionMapper.deleteById(id);
    }

    /**
     * 变更岗位状态（如发布、关闭）。
     *
     * @param id     岗位 id
     * @param status 目标状态
     * @return 变更后的岗位
     */
    @Transactional(rollbackFor = Exception.class)
    public JobVO changeStatus(Long id, JobStatus status) {
        if (status == null) {
            throw BizException.badRequest("岗位状态不能为空");
        }
        JobPosition job = requireJob(id);
        job.setStatus(status);
        jobPositionMapper.updateById(job);
        return JobVO.from(job);
    }

    /**
     * 校验岗位存在且处于招聘中状态，供面试模块创建面试时调用。
     *
     * @param id 岗位 id
     * @return 招聘中的岗位实体
     */
    public JobPosition requireOpenJob(Long id) {
        JobPosition job = requireJob(id);
        if (job.getStatus() != JobStatus.OPEN) {
            throw BizException.conflict("该岗位未开放招聘");
        }
        return job;
    }

    /**
     * 按 id 查询岗位实体，不存在时抛 404 业务异常。
     *
     * @param id 岗位 id
     * @return 岗位实体
     */
    private JobPosition requireJob(Long id) {
        if (id == null) {
            throw BizException.notFound("岗位不存在");
        }
        JobPosition job = jobPositionMapper.selectById(id);
        if (job == null) {
            throw BizException.notFound("岗位不存在");
        }
        return job;
    }

    /**
     * 把请求字段写入实体，状态为空时保留原值（新增场景由调用方补默认值）。
     *
     * @param job     目标实体
     * @param request 岗位信息
     */
    private void applyRequest(JobPosition job, JobSaveRequest request) {
        job.setTitle(request.title());
        job.setDepartment(request.department());
        job.setLevel(request.level());
        job.setDescription(request.description());
        job.setRequirements(request.requirements());
        if (request.status() != null) {
            job.setStatus(request.status());
        }
    }
}
