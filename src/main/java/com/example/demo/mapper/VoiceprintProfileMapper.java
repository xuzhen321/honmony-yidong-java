package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.pojo.entity.VoiceprintProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VoiceprintProfileMapper extends BaseMapper<VoiceprintProfile> {

    /**
     * 统计某个用户已经录好的数字种类数（0~10）。
     *
     * <p>只看生效且未删除的模板；同一个数字录了 2 条也只算 1 种。</p>
     */
    @Select("""
            SELECT COUNT(DISTINCT phrase_tag)
            FROM voiceprint_profile
            WHERE user_id = #{userId}
              AND deleted = 0
              AND phrase_tag LIKE 'DIGIT\\_%'
            """)
    int countDistinctDigitTags(@Param("userId") Long userId);
}
