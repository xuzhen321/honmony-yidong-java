package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.pojo.entity.VoiceprintProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /** 已录好的数字标签，用于拼 missingDigits。 */
    @Select("""
            SELECT phrase_tag
            FROM voiceprint_profile
            WHERE user_id = #{userId}
              AND deleted = 0
              AND phrase_tag LIKE 'DIGIT\\_%'
            GROUP BY phrase_tag
            """)
    List<String> selectDistinctDigitTags(@Param("userId") Long userId);

    /** 某用户某个数字当前生效模板数（上限 2）。 */
    @Select("""
            SELECT COUNT(*)
            FROM voiceprint_profile
            WHERE user_id = #{userId}
              AND phrase_tag = #{phraseTag}
              AND deleted = 0
            """)
    int countEffectiveByUserAndTag(@Param("userId") Long userId,
                                   @Param("phraseTag") String phraseTag);

    /**
     * 模板 SHA-256 是否已存在。
     *
     * <p>必须包含逻辑删除的行：{@code uk_vp_sha} 是全库唯一，
     * 重置后也不能拿同一段旧音频重新注册。</p>
     */
    @Select("SELECT COUNT(*) FROM voiceprint_profile WHERE template_sha256 = #{sha256}")
    int countByTemplateShaIncludingDeleted(@Param("sha256") String sha256);

    /**
     * 其他用户同一数字的生效模板，用于注册时的 1:N 查重。
     *
     * <p>只和同一 {@code phrase_tag} 比，数字 3 和数字 8 本来就不该互相比较。</p>
     */
    @Select("""
            SELECT *
            FROM voiceprint_profile
            WHERE phrase_tag = #{phraseTag}
              AND user_id <> #{userId}
              AND deleted = 0
            """)
    List<VoiceprintProfile> selectOtherUsersEffectiveByTag(@Param("userId") Long userId,
                                                           @Param("phraseTag") String phraseTag);
}
