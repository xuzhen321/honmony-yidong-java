package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.pojo.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {

    /**
     * 写入打卡记录；同一 {@code (task_id, user_id)} 已存在时复活并覆盖原行。
     *
     * <p>{@code uk_ar_task_user} 不包含 {@code deleted}，所以撤销后的记录仍然占着唯一键，
     * 不能直接 INSERT。这里用 {@code ON DUPLICATE KEY UPDATE} 把逻辑删除的旧行恢复成
     * {@code deleted=0}，同时把上一次的声纹/位置/补卡字段全部覆盖，避免残留脏数据。</p>
     */
    @Insert("""
            INSERT INTO attendance_record
                (user_id, task_id, record_date, check_type, challenge_id, detected_digits,
                 vp_profile_id, match_score, audio_sha256, check_latitude, check_longitude,
                 distance_m, accuracy_m, is_mock, source, operator_id, remark, deleted, created_at)
            VALUES
                (#{record.userId}, #{record.taskId}, #{record.recordDate}, #{record.checkType},
                 #{record.challengeId}, #{record.detectedDigits}, #{record.vpProfileId},
                 #{record.matchScore}, #{record.audioSha256}, #{record.checkLatitude},
                 #{record.checkLongitude}, #{record.distanceM}, #{record.accuracyM},
                 #{record.isMock}, #{record.source}, #{record.operatorId}, #{record.remark},
                 0, #{record.createdAt})
            ON DUPLICATE KEY UPDATE
                record_date     = VALUES(record_date),
                check_type      = VALUES(check_type),
                challenge_id    = VALUES(challenge_id),
                detected_digits = VALUES(detected_digits),
                vp_profile_id   = VALUES(vp_profile_id),
                match_score     = VALUES(match_score),
                audio_sha256    = VALUES(audio_sha256),
                check_latitude  = VALUES(check_latitude),
                check_longitude = VALUES(check_longitude),
                distance_m      = VALUES(distance_m),
                accuracy_m      = VALUES(accuracy_m),
                is_mock         = VALUES(is_mock),
                source          = VALUES(source),
                operator_id     = VALUES(operator_id),
                remark          = VALUES(remark),
                deleted         = 0,
                created_at      = VALUES(created_at)
            """)
    int upsert(@Param("record") AttendanceRecord record);

    /**
     * 按任务 + 用户查记录，包含逻辑删除的行。
     *
     * <p>upsert 之后用它拿回真实主键；撤销时也用它确认旧行状态。</p>
     */
    @Select("""
            SELECT *
            FROM attendance_record
            WHERE task_id = #{taskId} AND user_id = #{userId}
            LIMIT 1
            """)
    AttendanceRecord selectByTaskAndUserIncludingDeleted(@Param("taskId") Long taskId,
                                                         @Param("userId") Long userId);

    /** 撤销管理员补录的记录（逻辑删除）。声纹打卡记录不允许撤销。 */
    @Update("""
            UPDATE attendance_record
            SET deleted = 1
            WHERE id = #{recordId} AND deleted = 0 AND source = 'MANUAL'
            """)
    int revokeManual(@Param("recordId") Long recordId);

    /** 修改补录记录的备注；remark 传 null 表示清空。 */
    @Update("""
            UPDATE attendance_record
            SET remark = #{remark}
            WHERE id = #{recordId} AND deleted = 0 AND source = 'MANUAL'
            """)
    int updateManualRemark(@Param("recordId") Long recordId,
                           @Param("remark") String remark);
}
