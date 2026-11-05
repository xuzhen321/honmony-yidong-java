package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.pojo.entity.VoiceAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VoiceAttemptMapper extends BaseMapper<VoiceAttempt> {

    /** 同一段音频是否提交过（全局防重放，失败尝试也算）。 */
    @Select("SELECT COUNT(*) FROM voice_attempt WHERE audio_sha256 = #{sha256}")
    int countByAudioSha(@Param("sha256") String sha256);
}
