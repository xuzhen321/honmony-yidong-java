package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.pojo.entity.VoiceChallenge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface VoiceChallengeMapper extends BaseMapper<VoiceChallenge> {

    /** 指定时间之后该用户申请了多少次挑战码，用于每分钟限流。 */
    @Select("""
            SELECT COUNT(*)
            FROM voice_challenge
            WHERE user_id = #{userId} AND created_at > #{since}
            """)
    int countCreatedSince(@Param("userId") Long userId,
                          @Param("since") LocalDateTime since);

    /** 作废同一用户同一任务下所有未使用的旧挑战码。 */
    @Update("""
            UPDATE voice_challenge
            SET used_at = #{now}
            WHERE user_id = #{userId} AND task_id = #{taskId} AND used_at IS NULL
            """)
    int invalidateUnused(@Param("userId") Long userId,
                         @Param("taskId") Long taskId,
                         @Param("now") LocalDateTime now);

    /**
     * 消费挑战码；只有未使用且未过期时才能成功。
     *
     * @return 1 成功；0 表示已被并发请求用掉或刚好过期
     */
    @Update("""
            UPDATE voice_challenge
            SET used_at = #{now}
            WHERE id = #{id} AND used_at IS NULL AND expire_at > #{now}
            """)
    int consume(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 打卡失败次数 +1。 */
    @Update("UPDATE voice_challenge SET attempts = attempts + 1 WHERE id = #{id}")
    int incrementAttempts(@Param("id") Long id);
}
