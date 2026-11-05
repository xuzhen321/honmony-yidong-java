package com.example.demo.service;

import com.example.demo.mapper.MediaObjectMapper;
import com.example.demo.mapper.VoiceAttemptMapper;
import com.example.demo.mapper.VoiceChallengeMapper;
import com.example.demo.pojo.entity.MediaObject;
import com.example.demo.pojo.entity.VoiceAttempt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡失败记录器。
 *
 * <p>必须用独立事务（{@code REQUIRES_NEW}）提交失败明细：如果和业务异常放在同一个事务里，
 * 抛异常时会把 {@code voice_attempt}、音频元数据和 {@code attempts + 1} 一起回滚，
 * 打卡失败就没有证据可查了。</p>
 */
@Component
public class AttemptRecorder {

    private static final String OWNER_TYPE_CHECKIN = "CHECKIN";

    private final TransactionTemplate requiresNew;
    private final VoiceAttemptMapper attemptMapper;
    private final MediaObjectMapper mediaObjectMapper;
    private final VoiceChallengeMapper challengeMapper;

    public AttemptRecorder(PlatformTransactionManager transactionManager,
                           VoiceAttemptMapper attemptMapper,
                           MediaObjectMapper mediaObjectMapper,
                           VoiceChallengeMapper challengeMapper) {
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.attemptMapper = attemptMapper;
        this.mediaObjectMapper = mediaObjectMapper;
        this.challengeMapper = challengeMapper;
    }

    /**
     * 提交一条 FAIL 尝试：尝试明细 + 音频元数据 + 挑战码失败次数 +1。
     *
     * @param attempt      已填好用户、任务、分数、失败原因等字段，result 必须是 FAIL
     * @param relativePath 打卡音频相对路径
     * @param audioSha     明文音频 SHA-256
     * @param retainUntil  音频保留截止日期
     */
    public void record(VoiceAttempt attempt, String relativePath,
                       String audioSha, LocalDate retainUntil) {
        this.requiresNew.executeWithoutResult(status -> {
            attemptMapper.insert(attempt);

            MediaObject media = new MediaObject();
            media.setOwnerType(OWNER_TYPE_CHECKIN);
            media.setOwnerId(attempt.getId());
            media.setLocalPath(relativePath);
            media.setSha256(audioSha);
            media.setRetainUntil(retainUntil);
            media.setDeleted(0);
            media.setCreatedAt(LocalDateTime.now());
            mediaObjectMapper.insert(media);

            if (attempt.getChallengeId() != null) {
                challengeMapper.incrementAttempts(attempt.getChallengeId());
            }
        });
    }
}
