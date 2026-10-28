package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.pojo.entity.AttendanceTask;
import com.example.demo.pojo.vo.AttendanceTaskListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AttendanceTaskMapper extends BaseMapper<AttendanceTask> {

    /**
     * 管理员分页查询考勤任务，附带名单人数和已完成人数。
     *
     * <p>state 的过滤用数据库时间 NOW(3)，只接受 UPCOMING / OPEN / CLOSED；
     * 参数校验在 Service 层做。</p>
     */
    @Select("""
            <script>
            SELECT t.id AS task_id,
                   t.title,
                   t.check_type,
                   t.start_at,
                   t.end_at,
                   t.created_by,
                   t.created_at,
                   (SELECT COUNT(*) FROM attendance_task_user tu WHERE tu.task_id = t.id) AS participant_count,
                   (SELECT COUNT(*) FROM attendance_task_user tu WHERE tu.task_id = t.id AND tu.record_id IS NOT NULL) AS done_count
            FROM attendance_task t
            <where>
                <if test="state != null and state == 'UPCOMING'">
                    AND t.start_at &gt; NOW(3)
                </if>
                <if test="state != null and state == 'OPEN'">
                    AND t.start_at &lt;= NOW(3) AND t.end_at &gt; NOW(3)
                </if>
                <if test="state != null and state == 'CLOSED'">
                    AND t.end_at &lt;= NOW(3)
                </if>
                <if test="checkType != null and checkType != ''">
                    AND t.check_type = #{checkType}
                </if>
            </where>
            ORDER BY t.start_at DESC, t.id DESC
            </script>
            """)
    @ResultType(AttendanceTaskListItemVO.class)
    IPage<AttendanceTaskListItemVO> selectTaskPage(IPage<AttendanceTaskListItemVO> page,
                                                   @Param("state") String state,
                                                   @Param("checkType") String checkType);
}
