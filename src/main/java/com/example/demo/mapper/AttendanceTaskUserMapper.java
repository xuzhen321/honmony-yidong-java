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

import java.util.List;

@Mapper
public interface AttendanceTaskUserMapper extends BaseMapper<AttendanceTaskUser> {

    /** 管理员查看任务名单，只查原始字段；DONE / PENDING 在 Service 里组装。 */
    @Select("""
            SELECT tu.user_id,
                   u.user_name,
                   u.nick_name,
                   tu.checked_at,
                   tu.record_id
            FROM attendance_task_user tu
            JOIN `user` u ON u.id = tu.user_id
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
}
