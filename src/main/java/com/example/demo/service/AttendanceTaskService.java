package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.BusinessException;
import com.example.demo.mapper.AttendanceTaskMapper;
import com.example.demo.mapper.AttendanceTaskUserMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.mapper.VoiceprintProfileMapper;
import com.example.demo.pojo.dto.CreateAttendanceTaskRequest;
import com.example.demo.pojo.entity.AttendanceTask;
import com.example.demo.pojo.entity.AttendanceTaskUser;
import com.example.demo.pojo.entity.User;
import com.example.demo.pojo.vo.AttendanceTaskDetailVO;
import com.example.demo.pojo.vo.AttendanceTaskListItemVO;
import com.example.demo.pojo.vo.AttendanceTaskParticipantVO;
import com.example.demo.pojo.vo.CreateAttendanceTaskResult;
import com.example.demo.pojo.vo.MyAttendanceTaskVO;
import com.example.demo.pojo.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 考勤任务：管理员创建任务/查看任务，普通用户查看自己的任务。
 *
 * <p>本期没有修改、取消、删除任务的接口；任务过了 endAt 自然失效。</p>
 */
@Service
@RequiredArgsConstructor
public class AttendanceTaskService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceTaskService.class);

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String CHECK_IN = "IN";
    private static final String CHECK_OUT = "OUT";
    private static final Set<String> TASK_STATES = Set.of("UPCOMING", "OPEN", "CLOSED");
    private static final int REQUIRED_DIGIT_COUNT = 10;

    private final UserMapper userMapper;
    private final VoiceprintProfileMapper voiceprintProfileMapper;
    private final AttendanceTaskMapper attendanceTaskMapper;
    private final AttendanceTaskUserMapper attendanceTaskUserMapper;

    /**
     * 管理员创建考勤任务，并在同一事务里写入名单。
     */
    @Transactional(rollbackFor = Exception.class)
    public CreateAttendanceTaskResult createTask(Long operatorId, CreateAttendanceTaskRequest request) {
        this.requireAdmin(operatorId);

        if (request.getStartAt() == null || request.getEndAt() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "startAt / endAt 不能为空");
        }
        String checkType = request.getCheckType() == null
                ? null : request.getCheckType().trim().toUpperCase();
        if (!CHECK_IN.equals(checkType) && !CHECK_OUT.equals(checkType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "checkType 只能是 IN 或 OUT");
        }

        LocalDateTime startAt = this.toLocal(request.getStartAt());
        LocalDateTime endAt = this.toLocal(request.getEndAt());
        if (!startAt.isBefore(endAt)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "endAt 必须晚于 startAt");
        }

        // 去重并拒绝 null；保留管理员传入的顺序
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        if (request.getUserIds() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "userIds 不能为空");
        }
        for (Long userId : request.getUserIds()) {
            if (userId == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "userIds 不能包含 null");
            }
            userIds.add(userId);
        }
        if (userIds.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "userIds 不能为空");
        }

        // selectByIds 会自动追加 deleted = 0，所以冻结用户也会被当作无效
        List<User> users = userMapper.selectByIds(userIds);
        Set<Long> foundIds = users.stream().map(User::getId).collect(Collectors.toSet());
        List<Long> invalidIds = userIds.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!invalidIds.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "以下用户不存在或已冻结: " + invalidIds);
        }

        LocalDateTime now = LocalDateTime.now();
        AttendanceTask task = new AttendanceTask();
        task.setTitle(this.resolveTitle(request.getTitle(), checkType, now.toLocalDate()));
        task.setCheckType(checkType);
        task.setStartAt(startAt);
        task.setEndAt(endAt);
        task.setCreatedBy(operatorId);
        task.setCreatedAt(now);
        attendanceTaskMapper.insert(task);

        for (Long userId : userIds) {
            AttendanceTaskUser taskUser = new AttendanceTaskUser();
            taskUser.setTaskId(task.getId());
            taskUser.setUserId(userId);
            taskUser.setCreatedAt(now);
            attendanceTaskUserMapper.insert(taskUser);
        }

        log.info("管理员 {} 创建考勤任务 {}，类型 {}，名单 {} 人",
                operatorId, task.getId(), checkType, userIds.size());
        return new CreateAttendanceTaskResult(task.getId(), userIds.size());
    }

    /** 管理员分页查看任务。 */
    public PageResult<AttendanceTaskListItemVO> pageTasks(Long operatorId, String state,
                                                          String checkType, long pageNum, long pageSize) {
        this.requireAdmin(operatorId);

        String normalizedState = this.normalizeState(state);
        String normalizedCheckType = this.normalizeCheckType(checkType);
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }

        Page<AttendanceTaskListItemVO> page = new Page<>(pageNum, pageSize);
        IPage<AttendanceTaskListItemVO> result =
                attendanceTaskMapper.selectTaskPage(page, normalizedState, normalizedCheckType);

        LocalDateTime now = LocalDateTime.now();
        result.getRecords().forEach(vo ->
                vo.setState(this.stateOf(vo.getStartAt(), vo.getEndAt(), now)));

        return new PageResult<>(result.getCurrent(), result.getSize(),
                result.getTotal(), result.getPages(), result.getRecords());
    }

    /** 管理员查看单个任务和名单完成情况。 */
    public AttendanceTaskDetailVO getTaskDetail(Long operatorId, Long taskId) {
        this.requireAdmin(operatorId);

        AttendanceTask task = attendanceTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "考勤任务不存在");
        }

        AttendanceTaskDetailVO vo = new AttendanceTaskDetailVO();
        vo.setTaskId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setCheckType(task.getCheckType());
        vo.setStartAt(task.getStartAt());
        vo.setEndAt(task.getEndAt());
        vo.setState(this.stateOf(task.getStartAt(), task.getEndAt(), LocalDateTime.now()));

        // 名单状态在 Java 里组装，不把 CASE WHEN 压给数据库
        List<AttendanceTaskParticipantVO> participants =
                attendanceTaskUserMapper.selectParticipants(taskId);
        for (AttendanceTaskParticipantVO participant : participants) {
            participant.setStatus(participant.getRecordId() != null ? "DONE" : "PENDING");
        }
        vo.setParticipants(participants);
        return vo;
    }

    /** 当前登录用户分页查看自己的任务。未录满 10 个数字时拒绝，前端应跳声纹注册页。 */
    public PageResult<MyAttendanceTaskVO> pageMyTasks(Long userId, long pageNum, long pageSize) {
        if (voiceprintProfileMapper.countDistinctDigitTags(userId) < REQUIRED_DIGIT_COUNT) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "请先完成声纹注册");
        }
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }

        Page<MyAttendanceTaskVO> page = new Page<>(pageNum, pageSize);
        IPage<MyAttendanceTaskVO> result =
                attendanceTaskUserMapper.selectMyTaskPage(page, userId);

        LocalDateTime now = LocalDateTime.now();
        for (MyAttendanceTaskVO vo : result.getRecords()) {
            vo.setState(this.stateOf(vo.getStartAt(), vo.getEndAt(), now));
            vo.setDone(vo.getRecordId() != null);
        }

        return new PageResult<>(result.getCurrent(), result.getSize(),
                result.getTotal(), result.getPages(), result.getRecords());
    }

    /** 管理员权限从数据库实时查询，不信任 token 里的旧角色 */
    private void requireAdmin(Long operatorId) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !ROLE_ADMIN.equals(operator.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "没有访问权限");
        }
    }

    private String normalizeState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        String normalized = state.trim().toUpperCase();
        if (!TASK_STATES.contains(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "state 只能是 UPCOMING / OPEN / CLOSED");
        }
        return normalized;
    }

    private String normalizeCheckType(String checkType) {
        if (checkType == null || checkType.isBlank()) {
            return null;
        }
        String normalized = checkType.trim().toUpperCase();
        if (!CHECK_IN.equals(normalized) && !CHECK_OUT.equals(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "checkType 只能是 IN 或 OUT");
        }
        return normalized;
    }

    private LocalDateTime toLocal(OffsetDateTime value) {
        return value.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private String resolveTitle(String title, String checkType, LocalDate date) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return date + (CHECK_IN.equals(checkType) ? " 上班打卡" : " 下班打卡");
    }

    private String stateOf(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime now) {
        if (now.isBefore(startAt)) {
            return "UPCOMING";
        }
        if (now.isBefore(endAt)) {
            return "OPEN";
        }
        return "CLOSED";
    }
}
