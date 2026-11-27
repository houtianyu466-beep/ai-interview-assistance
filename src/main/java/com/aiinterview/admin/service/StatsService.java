package com.aiinterview.admin.service;

import com.aiinterview.admin.dto.DailyCountVO;
import com.aiinterview.admin.dto.PositionStatVO;
import com.aiinterview.admin.dto.StatsOverviewVO;
import com.aiinterview.admin.dto.StatusCountVO;
import com.aiinterview.common.api.PageResult;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.common.util.TextUtils;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.entity.InterviewReport;
import com.aiinterview.interview.enums.InterviewStatus;
import com.aiinterview.interview.mapper.InterviewMapper;
import com.aiinterview.interview.mapper.InterviewReportMapper;
import com.aiinterview.job.entity.JobPosition;
import com.aiinterview.job.mapper.JobPositionMapper;
import com.aiinterview.security.SecurityUtils;
import com.aiinterview.system.dto.UserQuery;
import com.aiinterview.system.dto.UserVO;
import com.aiinterview.system.entity.SysUser;
import com.aiinterview.system.enums.UserRole;
import com.aiinterview.system.enums.UserStatus;
import com.aiinterview.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 管理端统计与用户管理服务。
 *
 * <p>统计全部基于现有 Mapper + Wrapper 完成，不新增 XML；日期分组使用
 * {@code DATE(created_at)}，已确认 H2(MySQL 模式) 与 MySQL 8 均支持。
 * {@code selectMaps} 返回的列名大小写在不同数据库下不一致，读取时统一做了兼容。</p>
 */
@Service
public class StatsService {

    /** 最近统计天数 */
    private static final int RECENT_DAYS = 7;

    /** 热门岗位展示数量 */
    private static final int HOT_POSITION_LIMIT = 5;

    private final SysUserMapper sysUserMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final JobPositionMapper jobPositionMapper;

    /**
     * 构造器注入。
     *
     * @param sysUserMapper         用户 Mapper
     * @param interviewMapper       面试会话 Mapper
     * @param interviewReportMapper 面试报告 Mapper
     * @param jobPositionMapper     岗位 Mapper
     */
    public StatsService(SysUserMapper sysUserMapper,
                        InterviewMapper interviewMapper,
                        InterviewReportMapper interviewReportMapper,
                        JobPositionMapper jobPositionMapper) {
        this.sysUserMapper = sysUserMapper;
        this.interviewMapper = interviewMapper;
        this.interviewReportMapper = interviewReportMapper;
        this.jobPositionMapper = jobPositionMapper;
    }

    /**
     * 汇总管理端首页所需的全部统计数据。
     *
     * @return 统计概览
     */
    public StatsOverviewVO overview() {
        long userCount = sysUserMapper.selectCount(null);
        long candidateCount = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, UserRole.CANDIDATE));
        long interviewerCount = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, UserRole.INTERVIEWER));
        long interviewCount = interviewMapper.selectCount(null);
        long finishedCount = interviewMapper.selectCount(Wrappers.<Interview>lambdaQuery()
                .eq(Interview::getStatus, InterviewStatus.FINISHED));
        long inProgressCount = interviewMapper.selectCount(Wrappers.<Interview>lambdaQuery()
                .eq(Interview::getStatus, InterviewStatus.IN_PROGRESS));

        return new StatsOverviewVO(
                userCount,
                candidateCount,
                interviewerCount,
                interviewCount,
                finishedCount,
                inProgressCount,
                averageScore(),
                last7Days(),
                statusDistribution(),
                hotPositions());
    }

    /**
     * 分页查询用户，支持关键词（用户名/姓名）、角色、状态过滤。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public PageResult<UserVO> pageUsers(UserQuery query) {
        String keyword = query.getKeyword();
        UserRole role = parseRole(query.getRole());
        UserStatus status = parseStatus(query.getStatus());
        Page<SysUser> page = new Page<>(query.pageOrDefault(), query.sizeOrDefault());
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .and(TextUtils.isBlank(keyword) == false, condition -> condition
                        .like(SysUser::getUsername, keyword)
                        .or()
                        .like(SysUser::getRealName, keyword))
                .eq(role != null, SysUser::getRole, role)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreatedAt)
                .orderByDesc(SysUser::getId);
        Page<SysUser> result = sysUserMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(UserVO::from).toList());
    }

    /**
     * 修改用户状态。
     *
     * <p>两条保护规则：</p>
     * <ul>
     *     <li>不允许禁用当前登录的管理员自己；</li>
     *     <li>系统必须至少保留一个 ACTIVE 状态的 ADMIN。</li>
     * </ul>
     *
     * @param userId 用户 id
     * @param status 目标状态
     * @return 更新后的用户
     * @throws BizException 用户不存在抛 notFound；违反保护规则抛 conflict
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUserStatus(Long userId, UserStatus status) {
        if (status == null) {
            throw BizException.badRequest("用户状态不能为空");
        }
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        Long currentUserId = SecurityUtils.currentUserId();
        if (UserStatus.DISABLED == status && currentUserId.equals(userId)) {
            throw BizException.conflict("不能禁用当前登录的账号");
        }
        if (UserStatus.DISABLED == status && UserRole.ADMIN == user.getRole()) {
            Long activeAdmins = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getRole, UserRole.ADMIN)
                    .eq(SysUser::getStatus, UserStatus.ACTIVE)
                    .ne(SysUser::getId, userId));
            if (activeAdmins == null || activeAdmins <= 0) {
                throw BizException.conflict("系统至少保留一个可用的管理员");
            }
        }
        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return UserVO.from(user);
    }

    /**
     * 统计已生成报告的整体平均分，保留一位小数；无数据返回 0.0。
     *
     * @return 平均分
     */
    private double averageScore() {
        QueryWrapper<InterviewReport> wrapper = new QueryWrapper<InterviewReport>()
                .select("AVG(overall_score) AS avg_score");
        List<Map<String, Object>> rows = interviewReportMapper.selectMaps(wrapper);
        for (Map<String, Object> row : rows) {
            Object value = firstNumber(row);
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue())
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();
            }
        }
        return 0.0;
    }

    /**
     * 最近 7 天（含今天）每天的面试创建数量，缺失日期补 0，按日期升序返回。
     *
     * @return 7 条按天统计
     */
    private List<DailyCountVO> last7Days() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(RECENT_DAYS - 1L);
        QueryWrapper<Interview> wrapper = new QueryWrapper<Interview>()
                .select("DATE(created_at) AS d", "COUNT(*) AS c")
                .ge("created_at", start.atStartOfDay())
                .groupBy("DATE(created_at)");
        List<Map<String, Object>> rows = interviewMapper.selectMaps(wrapper);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate date = extractDate(row);
            if (date == null) {
                continue;
            }
            counts.merge(date, extractCount(row), Long::sum);
        }
        List<DailyCountVO> result = new ArrayList<>(RECENT_DAYS);
        for (int i = 0; i < RECENT_DAYS; i++) {
            LocalDate date = start.plusDays(i);
            result.add(new DailyCountVO(date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    counts.getOrDefault(date, 0L)));
        }
        return result;
    }

    /**
     * 按面试状态分组计数，按状态枚举定义顺序返回，便于前端稳定渲染。
     *
     * @return 状态分布
     */
    private List<StatusCountVO> statusDistribution() {
        QueryWrapper<Interview> wrapper = new QueryWrapper<Interview>()
                .select("status", "COUNT(*) AS c")
                .groupBy("status");
        List<Map<String, Object>> rows = interviewMapper.selectMaps(wrapper);
        List<StatusCountVO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object status = valueOf(row, "status");
            if (status == null) {
                continue;
            }
            result.add(new StatusCountVO(status.toString(), extractCount(row)));
        }
        result.sort(Comparator.comparingInt((StatusCountVO item) -> statusOrder(item.status()))
                .thenComparing(StatusCountVO::status));
        return result;
    }

    /**
     * 统计面试数量最多的前 5 个岗位。
     *
     * @return 岗位面试数量排行
     */
    private List<PositionStatVO> hotPositions() {
        QueryWrapper<Interview> wrapper = new QueryWrapper<Interview>()
                .select("position_id", "COUNT(*) AS c")
                .groupBy("position_id");
        List<Map<String, Object>> rows = interviewMapper.selectMaps(wrapper);
        List<Map.Entry<Long, Long>> counts = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long positionId = toLong(valueOf(row, "position_id", "positionId"));
            if (positionId == null) {
                continue;
            }
            counts.add(Map.entry(positionId, extractCount(row)));
        }
        counts.sort((left, right) -> {
            int byCount = Long.compare(right.getValue(), left.getValue());
            return byCount != 0 ? byCount : Long.compare(left.getKey(), right.getKey());
        });
        if (counts.size() > HOT_POSITION_LIMIT) {
            counts = counts.subList(0, HOT_POSITION_LIMIT);
        }
        List<Long> positionIds = counts.stream().map(Map.Entry::getKey).toList();
        Map<Long, String> titles = new HashMap<>();
        if (!positionIds.isEmpty()) {
            for (JobPosition position : jobPositionMapper.selectByIds(positionIds)) {
                titles.put(position.getId(), position.getTitle());
            }
        }
        List<PositionStatVO> result = new ArrayList<>(counts.size());
        for (Map.Entry<Long, Long> entry : counts) {
            result.add(new PositionStatVO(entry.getKey(),
                    titles.getOrDefault(entry.getKey(), "未知岗位"),
                    entry.getValue()));
        }
        return result;
    }

    /**
     * 从分组结果中读取日期字段：优先取别名 d / date，兜底取第一个非数值列。
     *
     * @param row 一行分组结果
     * @return 日期，无法识别时返回 {@code null}
     */
    private LocalDate extractDate(Map<String, Object> row) {
        Object value = valueOf(row, "d", "date");
        if (value == null) {
            for (Object candidate : row.values()) {
                if (candidate != null && !(candidate instanceof Number)) {
                    value = candidate;
                    break;
                }
            }
        }
        return toLocalDate(value);
    }

    /**
     * 从分组结果中读取计数字段：优先取别名 c / count，兜底取第一个数值列。
     *
     * @param row 一行分组结果
     * @return 计数，无法识别时返回 0
     */
    private long extractCount(Map<String, Object> row) {
        Object value = valueOf(row, "c", "count", "count(*)");
        if (value instanceof Number number) {
            return number.longValue();
        }
        for (Object candidate : row.values()) {
            if (candidate instanceof Number number) {
                return number.longValue();
            }
        }
        return 0L;
    }

    /**
     * 查找一行结果中第一个数值列。
     *
     * @param row 一行统计结果
     * @return 数值，没有时返回 {@code null}
     */
    private Object firstNumber(Map<String, Object> row) {
        for (Object candidate : row.values()) {
            if (candidate instanceof Number) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 按别名读取列值，忽略大小写；列名可能因数据库不同而为大写或小写。
     *
     * @param row  一行结果
     * @param keys 候选列名，按优先级排列
     * @return 列值，未命中返回 {@code null}
     */
    private Object valueOf(Map<String, Object> row, String... keys) {
        if (row == null) {
            return null;
        }
        for (String key : keys) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 把数据库返回的日期对象转成 {@link LocalDate}，兼容 Date / Timestamp / LocalDate / 字符串。
     *
     * @param value 原始值
     * @return 日期，无法识别时返回 {@code null}
     */
    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof CharSequence text) {
            String raw = text.toString().trim();
            try {
                return LocalDate.parse(raw.length() >= 10 ? raw.substring(0, 10) : raw);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 把数据库返回的数值转成 {@link Long}。
     *
     * @param value 原始值
     * @return 数值，无法识别时返回 {@code null}
     */
    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof CharSequence text) {
            try {
                return Long.parseLong(text.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 状态枚举排序序号，未知状态排到最后。
     *
     * @param status 状态字符串
     * @return 排序序号
     */
    private int statusOrder(String status) {
        try {
            return InterviewStatus.valueOf(status).ordinal();
        } catch (IllegalArgumentException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * 解析角色参数，空值返回 {@code null} 表示不过滤。
     *
     * @param raw 原始字符串
     * @return 角色枚举
     * @throws BizException 角色非法时抛出 badRequest
     */
    private UserRole parseRole(String raw) {
        if (TextUtils.isBlank(raw)) {
            return null;
        }
        try {
            return UserRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("用户角色不合法：" + raw);
        }
    }

    /**
     * 解析状态参数，空值返回 {@code null} 表示不过滤。
     *
     * @param raw 原始字符串
     * @return 状态枚举
     * @throws BizException 状态非法时抛出 badRequest
     */
    private UserStatus parseStatus(String raw) {
        if (TextUtils.isBlank(raw)) {
            return null;
        }
        try {
            return UserStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("用户状态不合法：" + raw);
        }
    }
}
