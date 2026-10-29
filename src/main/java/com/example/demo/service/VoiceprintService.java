package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.config.VoiceprintProperties;
import com.example.demo.mapper.MediaObjectMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.mapper.VoiceChallengeMapper;
import com.example.demo.mapper.VoiceprintAccountMapper;
import com.example.demo.mapper.VoiceprintProfileMapper;
import com.example.demo.pojo.entity.MediaObject;
import com.example.demo.pojo.entity.User;
import com.example.demo.pojo.entity.VoiceChallenge;
import com.example.demo.pojo.entity.VoiceprintAccount;
import com.example.demo.pojo.entity.VoiceprintProfile;
import com.example.demo.pojo.vo.EnrollResult;
import com.example.demo.pojo.vo.VoiceprintStatusVO;
import com.example.demo.voiceprint.audio.DtwMatcher;
import com.example.demo.voiceprint.audio.MfccExtractor;
import com.example.demo.voiceprint.audio.QualityScorer;
import com.example.demo.voiceprint.audio.SpeechValidator;
import com.example.demo.voiceprint.audio.VadSplitter;
import com.example.demo.voiceprint.audio.WavReader;
import com.example.demo.voiceprint.crypto.TemplateCipher;
import com.example.demo.voiceprint.storage.AudioStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 声纹注册、状态查询、管理员重置。
 *
 * <p>注册校验顺序：phraseTag 合法 → WAV 格式/时长/大小 → 模板 SHA 去重 → 该数字配额
 * → MFCC + 质量分 → 其他用户同数字 1:N 查重 → 加密落盘 + 入库。</p>
 *
 * <p>文件系统不在数据库事务里：先落盘，入库失败时补偿删除文件。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceprintService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String OWNER_TYPE_ENROLL = "VP_ENROLL";
    private static final int REQUIRED_DIGIT_COUNT = 10;
    private static final int MAX_TEMPLATES_PER_DIGIT = 2;
    private static final double ENROLL_MIN_SECONDS = 0.5;
    private static final double ENROLL_MAX_SECONDS = 1.5;
    /** 语音段占整段录音的比例上限；超过说明没有留静音，或连续念了多个数字 */
    private static final double MAX_SPEECH_RATIO = 0.95;
    /** 语音段 RMS 的绝对下限（dBFS），低于它认为声音太小或只是底噪 */
    private static final double MIN_SPEECH_DBFS = -45.0;
    private static final Set<String> DIGIT_TAGS = IntStream.range(0, 10)
            .mapToObj(index -> "DIGIT_" + index)
            .collect(Collectors.toUnmodifiableSet());

    private final UserMapper userMapper;
    private final VoiceprintAccountMapper accountMapper;
    private final VoiceprintProfileMapper profileMapper;
    private final MediaObjectMapper mediaObjectMapper;
    private final VoiceChallengeMapper challengeMapper;
    private final TemplateCipher templateCipher;
    private final AudioStorage audioStorage;
    private final VoiceprintProperties properties;
    private final TransactionTemplate transactionTemplate;

    /**
     * 注册一个数字模板。所有登录用户都可以调用。
     */
    public EnrollResult enroll(Long userId, String rawPhraseTag, MultipartFile audio) {
        this.requireActiveUser(userId);
        String phraseTag = this.normalizePhraseTag(rawPhraseTag);
        byte[] wavBytes = this.readAudio(audio);

        WavReader.AudioData audioData =
                WavReader.read(wavBytes, ENROLL_MIN_SECONDS, ENROLL_MAX_SECONDS);
        List<VadSplitter.Segment> segments =
                VadSplitter.split(audioData.samples(), audioData.sampleRate());
        if (segments.isEmpty()) {
            log.debug("用户 {} 注册 {}：VAD 没有检测到有效语音", userId, phraseTag);
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "没有检测到有效语音，请重新录制");
        }
        if (segments.size() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "录音中只能包含一个数字，请重新录制");
        }

        VadSplitter.Segment speech = segments.get(0);
        short[] speechSamples = Arrays.copyOfRange(
                audioData.samples(), speech.startSample(), speech.endSample());

        // 语音段几乎铺满整段录音：要么没留静音，要么一口气念了多个数字
        double speechRatio = speechSamples.length / (double) audioData.samples().length;
        if (speechRatio > MAX_SPEECH_RATIO) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "录音前后请留出一点静音，并只念一个数字");
        }
        // 绝对音量太低：手机麦克风没收到有效声音，或只是轻微底噪
        double speechDbfs = this.rmsDbfs(speechSamples);
        if (speechDbfs < MIN_SPEECH_DBFS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "录音声音太小，请靠近麦克风重录");
        }
        // 不像人声：咳嗽、碰撞、键盘声、起伏的空调噪声也能通过 VAD 和音量检查
        SpeechValidator.VoiceMetrics voice =
                SpeechValidator.analyze(speechSamples, audioData.sampleRate());
        log.debug("用户 {} 注册 {}：语音段={}ms，占比={}，音量={}dBFS，浊音={}，频带={}，频谱变化={}",
                userId, phraseTag, speechSamples.length * 1000 / audioData.sampleRate(),
                String.format("%.2f", speechRatio), String.format("%.1f", speechDbfs),
                String.format("%.2f", voice.voicedRatio()),
                String.format("%.2f", voice.speechBandRatio()),
                String.format("%.3f", voice.spectralFlux()));
        if (voice.voicedRatio() < SpeechValidator.MIN_VOICED_RATIO) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "没有检测到清晰的人声，请到安静环境重录");
        }
        // 低频轰鸣/电流声：周期性很强，但能量几乎都在语音频带以下
        if (voice.speechBandRatio() < SpeechValidator.MIN_SPEECH_BAND_RATIO) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "录音环境噪声太大，请到安静环境重录");
        }
        // 蜂鸣声/持续单音：频谱几乎不变，而念数字时频谱一直在变化
        if (voice.spectralFlux() < SpeechValidator.MIN_SPECTRAL_FLUX) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "没有检测到清晰的人声，请正常朗读一个数字");
        }

        float[][] mfcc = MfccExtractor.extract(speechSamples);
        if (mfcc.length == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "录音太短，请重新录制");
        }

        byte[] matrixBytes = TemplateCipher.encodeMatrix(mfcc);
        String templateSha256 = TemplateCipher.sha256Hex(matrixBytes);
        if (profileMapper.countByTemplateShaIncludingDeleted(templateSha256) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "这段音频已被注册");
        }
        if (profileMapper.countEffectiveByUserAndTag(userId, phraseTag)
                >= MAX_TEMPLATES_PER_DIGIT) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "该数字已录满 2 条，请先重置声纹再重新录制");
        }

        int quality = QualityScorer.score(audioData.samples(), audioData.sampleRate(), segments);
        if (quality < QualityScorer.MIN_ACCEPTABLE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "录音质量太低，请重录");
        }

        this.checkNotUsedByOtherUser(userId, phraseTag, mfcc);

        byte[] encryptedTemplate = templateCipher.encrypt(matrixBytes);
        String relativePath = audioStorage.storeEnroll(userId, phraseTag, wavBytes);
        try {
            EnrollResult result = transactionTemplate.execute(status -> this.saveEnrollment(
                    userId, phraseTag, encryptedTemplate, templateSha256, mfcc.length,
                    audioData.durationMs(), quality, wavBytes, relativePath));
            log.info("用户 {} 注册数字模板 {}，profileId={}，frames={}，quality={}，"
                            + "语音段={}ms，占比={}，音量={}dBFS，浊音占比={}，语音频带占比={}，频谱变化={}",
                    userId, phraseTag, Objects.requireNonNull(result).getProfileId(),
                    result.getFrames(), result.getQuality(), speechSamples.length * 1000
                            / audioData.sampleRate(),
                    String.format("%.2f", speechRatio), String.format("%.1f", speechDbfs),
                    String.format("%.2f", voice.voicedRatio()),
                    String.format("%.2f", voice.speechBandRatio()),
                    String.format("%.3f", voice.spectralFlux()));
            return result;
        } catch (RuntimeException e) {
            // 数据库事务已回滚，磁盘文件必须补偿删除
            audioStorage.deleteByRelativePath(relativePath);
            throw e;
        }
    }

    /** 查询当前登录用户的声纹注册状态。 */
    public VoiceprintStatusVO getStatus(Long userId) {
        this.requireActiveUser(userId);

        VoiceprintAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<VoiceprintAccount>()
                        .eq(VoiceprintAccount::getUserId, userId));

        Set<Integer> presentDigits = new HashSet<>();
        for (String tag : profileMapper.selectDistinctDigitTags(userId)) {
            presentDigits.add(this.digitOf(tag));
        }
        int digitCount = presentDigits.size();
        List<String> missingDigits = IntStream.range(0, REQUIRED_DIGIT_COUNT)
                .filter(digit -> !presentDigits.contains(digit))
                .mapToObj(String::valueOf)
                .toList();

        String status;
        boolean readyForCheckin;
        LocalDateTime enrolledAt;
        if (account == null) {
            status = "NOT_ENROLLED";
            readyForCheckin = false;
            enrolledAt = null;
        } else if (Integer.valueOf(2).equals(account.getStatus())) {
            status = "REVOKED";
            readyForCheckin = false;
            enrolledAt = null;
        } else if (digitCount >= REQUIRED_DIGIT_COUNT) {
            status = "ACTIVE";
            readyForCheckin = true;
            enrolledAt = account.getEnrolledAt();
        } else {
            status = "ENROLLING";
            readyForCheckin = false;
            enrolledAt = account.getEnrolledAt();
        }

        return new VoiceprintStatusVO(status, false, digitCount, missingDigits,
                readyForCheckin, enrolledAt);
    }

    /**
     * 管理员重置某人的声纹。
     *
     * <p>账号行永不删除，只置 {@code status=2}，否则 {@code UNIQUE(user_id)}
     * 会让重新注册插不进去；模板逻辑删除、未用挑战码作废、注册音频物理删除。</p>
     */
    public void reset(Long operatorId, Long targetUserId) {
        this.requireAdmin(operatorId);

        User target = userMapper.selectByIdIgnoreLogicDelete(targetUserId);
        if (target == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        Boolean hadAccount = transactionTemplate.execute(status -> {
            VoiceprintAccount account = accountMapper.selectOne(
                    new LambdaQueryWrapper<VoiceprintAccount>()
                            .eq(VoiceprintAccount::getUserId, targetUserId));
            if (account == null) {
                // 没有档案就是幂等成功
                return false;
            }

            // 在事务里取模板 ID，避免和并发注册交错后漏删 media_object
            List<Long> profileIds = profileMapper.selectList(
                            new LambdaQueryWrapper<VoiceprintProfile>()
                                    .eq(VoiceprintProfile::getUserId, targetUserId))
                    .stream().map(VoiceprintProfile::getId).toList();

            accountMapper.update(null, new LambdaUpdateWrapper<VoiceprintAccount>()
                    .eq(VoiceprintAccount::getUserId, targetUserId)
                    .set(VoiceprintAccount::getStatus, 2)
                    .set(VoiceprintAccount::getRevokedAt, now)
                    .set(VoiceprintAccount::getEnrolledAt, null));

            profileMapper.delete(new LambdaQueryWrapper<VoiceprintProfile>()
                    .eq(VoiceprintProfile::getUserId, targetUserId));

            if (!profileIds.isEmpty()) {
                mediaObjectMapper.delete(new LambdaQueryWrapper<MediaObject>()
                        .eq(MediaObject::getOwnerType, OWNER_TYPE_ENROLL)
                        .in(MediaObject::getOwnerId, profileIds));
            }

            // 未使用的挑战码全部作废，避免重置后用旧码打卡
            challengeMapper.update(null, new LambdaUpdateWrapper<VoiceChallenge>()
                    .eq(VoiceChallenge::getUserId, targetUserId)
                    .isNull(VoiceChallenge::getUsedAt)
                    .set(VoiceChallenge::getUsedAt, now));
            return true;
        });

        if (!Boolean.TRUE.equals(hadAccount)) {
            return;
        }

        // 磁盘文件物理删除放在事务提交之后
        audioStorage.deleteEnrollDir(targetUserId);
        log.info("管理员 {} 重置了用户 {} 的声纹", operatorId, targetUserId);
    }

    /** 事务内：写模板 + 音频元数据 + 刷新档案状态。 */
    private EnrollResult saveEnrollment(Long userId, String phraseTag,
                                        byte[] encryptedTemplate, String templateSha256,
                                        int frames, int durationMs, int quality,
                                        byte[] wavBytes, String relativePath) {
        LocalDateTime now = LocalDateTime.now();

        VoiceprintAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<VoiceprintAccount>()
                        .eq(VoiceprintAccount::getUserId, userId));
        if (account == null) {
            account = new VoiceprintAccount();
            account.setUserId(userId);
            account.setStatus(0);
            account.setCreatedAt(now);
            try {
                accountMapper.insert(account);
            } catch (DuplicateKeyException e) {
                // 并发首次注册：另一个请求已经建好档案，直接复用
                account = accountMapper.selectOne(new LambdaQueryWrapper<VoiceprintAccount>()
                        .eq(VoiceprintAccount::getUserId, userId));
                if (account == null) {
                    // 并发首次注册且对方事务尚未提交时可能查不到，转成 409 让客户端重试
                    throw new BusinessException(HttpStatus.CONFLICT, "注册请求冲突，请重试");
                }
            }
        }

        VoiceprintProfile profile = new VoiceprintProfile();
        profile.setUserId(userId);
        profile.setPhraseTag(phraseTag);
        profile.setTemplateCipher(encryptedTemplate);
        profile.setTemplateSha256(templateSha256);
        profile.setFrames(frames);
        profile.setDurationMs(durationMs);
        profile.setQuality(quality);
        profile.setDeleted(0);
        profile.setCreatedAt(now);
        try {
            profileMapper.insert(profile);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "这段音频已被注册");
        }

        MediaObject media = new MediaObject();
        media.setOwnerType(OWNER_TYPE_ENROLL);
        media.setOwnerId(profile.getId());
        media.setLocalPath(relativePath);
        media.setSha256(TemplateCipher.sha256Hex(wavBytes));
        media.setRetainUntil(null);
        media.setDeleted(0);
        media.setCreatedAt(now);
        mediaObjectMapper.insert(media);

        int digitCount = profileMapper.countDistinctDigitTags(userId);
        boolean complete = digitCount >= REQUIRED_DIGIT_COUNT;
        accountMapper.update(null, new LambdaUpdateWrapper<VoiceprintAccount>()
                .eq(VoiceprintAccount::getUserId, userId)
                .set(VoiceprintAccount::getStatus, complete ? 1 : 0)
                .set(VoiceprintAccount::getEnrolledAt, complete ? now : null)
                .set(VoiceprintAccount::getRevokedAt, null));

        return new EnrollResult(profile.getId(), phraseTag, frames, durationMs, quality);
    }

    /** 和其他用户同 phraseTag 的生效模板做 1:N DTW，最高分必须低于阈值。 */
    private void checkNotUsedByOtherUser(Long userId, String phraseTag, float[][] mfcc) {
        List<VoiceprintProfile> others =
                profileMapper.selectOtherUsersEffectiveByTag(userId, phraseTag);
        for (VoiceprintProfile other : others) {
            float[][] otherMatrix;
            try {
                otherMatrix = TemplateCipher.decodeMatrix(
                        templateCipher.decrypt(other.getTemplateCipher()), other.getFrames());
            } catch (RuntimeException e) {
                // 单条模板损坏不应阻塞其他人注册
                log.warn("跳过损坏的声纹模板 profileId={}", other.getId());
                continue;
            }
            double similarity = DtwMatcher.similarity(mfcc, otherMatrix);
            if (similarity >= properties.getDuplicateThreshold()) {
                throw new BusinessException(HttpStatus.CONFLICT, "该声纹已被其他账号使用");
            }
        }
    }

    private byte[] readAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请上传录音文件 audio");
        }
        if (audio.getSize() > WavReader.MAX_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "录音文件不能超过 500KB");
        }
        try {
            return audio.getBytes();
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "录音文件读取失败，请重新上传");
        }
    }

    private String normalizePhraseTag(String rawPhraseTag) {
        if (rawPhraseTag == null || rawPhraseTag.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "phraseTag 不能为空");
        }
        String phraseTag = rawPhraseTag.trim().toUpperCase(Locale.ROOT);
        if ("FIXED".equals(phraseTag)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "本期只支持 DIGIT_0 ~ DIGIT_9，暂不支持整句模板");
        }
        if (!DIGIT_TAGS.contains(phraseTag)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "phraseTag 只能是 DIGIT_0 ~ DIGIT_9");
        }
        return phraseTag;
    }

    private int digitOf(String phraseTag) {
        return Integer.parseInt(phraseTag.substring("DIGIT_".length()));
    }

    /** 计算样本的 RMS 电平（dBFS）。 */
    private double rmsDbfs(short[] samples) {
        if (samples == null || samples.length == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        double sum = 0;
        for (short sample : samples) {
            sum += (double) sample * sample;
        }
        double rms = Math.sqrt(sum / samples.length);
        return 20 * Math.log10(Math.max(rms / 32768.0, 1e-12));
    }

    /** 注册只对未冻结用户开放；冻结用户即使 token 还没过期也不能继续录。 */
    private void requireActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户不存在或已被冻结，请重新登录");
        }
    }

    /** 管理员权限从数据库实时查询，不信任 token 里的旧角色。 */
    private void requireAdmin(Long operatorId) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !ROLE_ADMIN.equals(operator.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "没有访问权限");
        }
    }
}
