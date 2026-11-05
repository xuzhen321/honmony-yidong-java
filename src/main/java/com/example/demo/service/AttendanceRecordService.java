package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.mapper.AttendanceRecordMapper;
import com.example.demo.mapper.AttendanceTaskMapper;
import com.example.demo.mapper.AttendanceTaskUserMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.pojo.dto.ManualCheckinRequest;
import com.example.demo.pojo.dto.UpdateManualRecordRequest;
import com.example.demo.pojo.entity.AttendanceRecord;
import com.example.demo.pojo.entity.AttendanceTask;
import com.example.demo.pojo.entity.AttendanceTaskUser;
import com.example.demo.pojo.entity.User;
import com.example.demo.pojo.vo.ManualRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 管理员补卡 / 修改补卡 / 撤销补卡。
 *
 * <p>只允许操作 {@code source=MANUAL} 的记录；声纹打卡记录是审计证据，不允许修改或撤销。
 * 补卡不要求员工具备声纹，管理员可以从名单里直接把人标成已打卡。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceRecordService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String SOURCE_MANUAL = "MANUAL";

    private final UserMapper userMapper;
    private final AttendanceTaskMapper attendanceTaskMapper;
    private final AttendanceTaskUserMapper attendanceTaskUserMapper;
    private final AttendanceRecordMapper attendanceRecordMapper;

    /**
     * 管理员手动补卡。
     *
     * <p>任务必须已经开始（允许已结束）；员工必须在名单里且尚未打卡。
     * 补卡时间可以晚于任务结束时间，这是补录场景的正常情况。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ManualRecordVO manualCheckin(Long operatorId, ManualCheckinRequest request) {
        this.requireAdmin(operatorId);

        AttendanceTask task = attendanceTaskMapper.selectById(request.getTaskId());
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "考勤任务不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(task.getStartAt())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "考勤还没开始，不能补卡");
        }

        AttendanceTaskUser taskUser = this.findTaskUser(task.getId(), request.getUserId());
        if (taskUser == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该员工不在本次考勤名单中");
        }
        if (taskUser.getRecordId() != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "该员工已完成打卡");
        }

        LocalDateTime checkedAt = request.getCheckAt() == null
                ? now : this.toLocal(request.getCheckAt());
        if (checkedAt.isAfter(now)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "补卡时间不能晚于当前时间");
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setUserId(request.getUserId());
        record.setTaskId(task.getId());
        record.setRecordDate(task.getStartAt().toLocalDate());
        record.setCheckType(task.getCheckType());
        record.setSource(SOURCE_MANUAL);
        record.setOperatorId(operatorId);
        record.setRemark(this.normalizeRemark(request.getRemark()));
        record.setIsMock(false);
        record.setDeleted(0);
        record.setCreatedAt(now);
        attendanceRecordMapper.upsert(record);

        AttendanceRecord saved = attendanceRecordMapper
                .selectByTaskAndUserIncludingDeleted(task.getId(), request.getUserId());
        if (saved == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "补卡记录写入失败");
        }
        int marked = attendanceTaskUserMapper.markChecked(
                task.getId(), request.getUserId(), saved.getId(), checkedAt);
        if (marked != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "该员工已完成打卡");
        }

        log.info("管理员 {} 为用户 {} 在任务 {} 补卡，时间 {}",
                operatorId, request.getUserId(), task.getId(), checkedAt);
        return this.toVO(saved, checkedAt);
    }

    /** 修改补卡时间 / 备注；字段为 null 表示不修改。 */
    @Transactional(rollbackFor = Exception.class)
    public ManualRecordVO updateManual(Long operatorId, Long recordId,
                                       UpdateManualRecordRequest request) {
        this.requireAdmin(operatorId);

        AttendanceRecord record = this.requireManualRecord(recordId);
        AttendanceTaskUser taskUser = this.findTaskUser(record.getTaskId(), record.getUserId());
        LocalDateTime checkedAt = taskUser == null ? record.getCreatedAt()
                : taskUser.getCheckedAt();

        if (request.getCheckAt() != null) {
            checkedAt = this.toLocal(request.getCheckAt());
            if (checkedAt.isAfter(LocalDateTime.now())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "补卡时间不能晚于当前时间");
            }
            int updated = attendanceTaskUserMapper.updateCheckedAt(
                    record.getTaskId(), record.getUserId(), recordId, checkedAt);
            if (updated != 1) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "打卡记录状态已变化，请刷新后重试");
            }
        }
        if (request.getRemark() != null) {
            String remark = this.normalizeRemark(request.getRemark());
            int updated = attendanceRecordMapper.updateManualRemark(recordId, remark);
            if (updated != 1) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "打卡记录状态已变化，请刷新后重试");
            }
            record.setRemark(remark);
        }

        log.info("管理员 {} 修改补卡记录 {}，时间 {}", operatorId, recordId, checkedAt);
        return this.toVO(record, checkedAt);
    }

    /** 撤销补卡：记录逻辑删除，名单恢复为未打卡，员工可以重新打卡或再次补卡。 */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long operatorId, Long recordId) {
        this.requireAdmin(operatorId);

        AttendanceRecord record = this.requireManualRecord(recordId);
        int revoked = attendanceRecordMapper.revokeManual(recordId);
        if (revoked != 1) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "打卡记录不存在");
        }
        int cleared = attendanceTaskUserMapper.clearChecked(
                record.getTaskId(), record.getUserId(), recordId);
        if (cleared != 1) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "打卡记录状态已变化，请刷新后重试");
        }

        log.info("管理员 {} 撤销补卡记录 {}，任务 {}，用户 {}",
                operatorId, recordId, record.getTaskId(), record.getUserId());
    }

    private AttendanceRecord requireManualRecord(Long recordId) {
        AttendanceRecord record = attendanceRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "打卡记录不存在");
        }
        if (!SOURCE_MANUAL.equals(record.getSource())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "只能修改或撤销管理员补录的记录");
        }
        return record;
    }

    private AttendanceTaskUser findTaskUser(Long taskId, Long userId) {
        return attendanceTaskUserMapper.selectOne(
                new LambdaQueryWrapper<AttendanceTaskUser>()
                        .eq(AttendanceTaskUser::getTaskId, taskId)
                        .eq(AttendanceTaskUser::getUserId, userId));
    }

    private ManualRecordVO toVO(AttendanceRecord record, LocalDateTime checkedAt) {
        return new ManualRecordVO(record.getId(), record.getTaskId(), record.getUserId(),
                record.getCheckType(), record.getRecordDate(), checkedAt,
                record.getSource(), record.getOperatorId(), record.getRemark());
    }

    private String normalizeRemark(String remark) {
        if (remark == null || remark.isBlank()) {
            return null;
        }
        return remark.trim();
    }

    private LocalDateTime toLocal(OffsetDateTime value) {
        return value.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** 管理员权限从数据库实时查询，不信任 token 里的旧角色。 */
    private void requireAdmin(Long operatorId) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !ROLE_ADMIN.equals(operator.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "没有访问权限");
        }
    }
}
