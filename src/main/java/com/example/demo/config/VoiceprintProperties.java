package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 声纹模块配置，对应 application.yaml 里的 {@code app.voiceprint.*}。
 *
 * <p>AES 密钥从环境变量 {@code VOICEPRINT_AES_KEY} 读取，不写进仓库。
 * M2 创建 {@code TemplateCipher} 时再校验密钥长度，未配置直接启动失败。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.voiceprint")
public class VoiceprintProperties {

    /** 音频根目录，默认 D:/a2026-10/attendance-voice */
    private String audioRoot = "D:\\a2026-10\\attendance-voice";

    /** Base64 编码的 32 字节 AES 密钥；由环境变量 VOICEPRINT_AES_KEY 注入 */
    private String aesKey;

    /** 挑战码有效期（秒），默认 1 分钟 */
    private int challengeTtlSeconds = 60;

    /** 同一 nonce 允许失败次数 */
    private int challengeMaxAttempts = 3;

    /** 同一用户每分钟最多申请挑战码次数 */
    private int challengeRateLimitPerMinute = 5;

    /** 注册时 1:N 查重阈值，最高分必须低于它 */
    private double duplicateThreshold = 0.70;

    /** 打卡判定阈值 */
    private Thresholds thresholds = new Thresholds();

    @Data
    public static class Thresholds {

        /** 4 段平均分下限 */
        private double avg = 0.78;

        /** 4 段最低分下限 */
        private double min = 0.65;

        /** 最佳与次佳分数差下限 */
        private double margin = 0.05;
    }
}
