package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.pojo.entity.AttendanceTaskUser;
import com.example.demo.pojo.vo.AttendanceTaskParticipantVO;
import com.example.demo.pojo.vo.MyAttendanceTaskVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AttendanceTaskUserMapper extends BaseMapper<AttendanceTaskUser> {

    /** 管理员查看任务名单，只查原始字段；DONE / PENDING 在 Service 里组装。 */
    @Select("""
            SELECT tu.user_id,
                   u.user_name,
                   u.nick_name,
                   tu.checked_at,
                   tu.record_id,
                   r.source,
                   r.operator_id,
                   r.remark
            FROM attendance_task_user tu
            JOIN `user` u ON u.id = tu.user_id
            LEFT JOIN attendance_record r ON r.id = tu.record_id AND r.deleted = 0
            WHERE tu.task_id = #{taskId}
            ORDER BY tu.id
            """)
    @ResultType(AttendanceTaskParticipantVO.class)
    List<AttendanceTaskParticipantVO> selectParticipants(@Param("taskId") Long taskId);

    /** 当前登录用户自己的考勤任务，按开始时间倒序。 */
    @Select("""
            SELECT t.id AS task_id,
                   t.title,
                   t.check_type,
                   t.start_at,
                   t.end_at,
                   t.latitude,
                   t.longitude,
                   t.radius_m,
                   t.address,
                   tu.record_id,
                   tu.checked_at
            FROM attendance_task t
            JOIN attendance_task_user tu ON tu.task_id = t.id
            WHERE tu.user_id = #{userId}
            ORDER BY t.start_at DESC, t.id DESC
            """)
    @ResultType(MyAttendanceTaskVO.class)
    IPage<MyAttendanceTaskVO> selectMyTaskPage(IPage<MyAttendanceTaskVO> page,
                                               @Param("userId") Long userId);

    /**
     * 条件更新“已打卡”：只有当前还没打卡时才能更新成功。
     *
     * <p>并发打卡时只有一个请求能让影响行数变成 1，另一个返回 0，调用方转成 409。</p>
     */
    @Update("""
            UPDATE attendance_task_user
            SET record_id = #{recordId}, checked_at = #{checkedAt}
            WHERE task_id = #{taskId} AND user_id = #{userId} AND record_id IS NULL
            """)
    int markChecked(@Param("taskId") Long taskId,
                    @Param("userId") Long userId,
                    @Param("recordId") Long recordId,
                    @Param("checkedAt") LocalDateTime checkedAt);

    /** 撤销打卡：只有 record_id 仍指向这条记录时才清空。 */
    @Update("""
            UPDATE attendance_task_user
            SET record_id = NULL, checked_at = NULL
            WHERE task_id = #{taskId} AND user_id = #{userId} AND record_id = #{recordId}
            """)
    int clearChecked(@Param("taskId") Long taskId,
                     @Param("userId") Long userId,
                     @Param("recordId") Long recordId);

    /** 修改补卡时间：只更新仍指向这条记录的名单行。 */
    @Update("""
            UPDATE attendance_task_user
            SET checked_at = #{checkedAt}
            WHERE task_id = #{taskId} AND user_id = #{userId} AND record_id = #{recordId}
            """)
    int updateCheckedAt(@Param("taskId") Long taskId,
                        @Param("userId") Long userId,
                        @Param("recordId") Long recordId,
                        @Param("checkedAt") LocalDateTime checkedAt);
}
