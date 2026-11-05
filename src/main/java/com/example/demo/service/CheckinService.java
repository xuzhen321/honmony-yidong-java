package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.config.VoiceprintProperties;
import com.example.demo.mapper.AttendanceRecordMapper;
import com.example.demo.mapper.AttendanceTaskMapper;
import com.example.demo.mapper.AttendanceTaskUserMapper;
import com.example.demo.mapper.MediaObjectMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.mapper.VoiceAttemptMapper;
import com.example.demo.mapper.VoiceChallengeMapper;
import com.example.demo.mapper.VoiceprintProfileMapper;
import com.example.demo.pojo.entity.AttendanceRecord;
import com.example.demo.pojo.entity.AttendanceTask;
import com.example.demo.pojo.entity.AttendanceTaskUser;
import com.example.demo.pojo.entity.MediaObject;
import com.example.demo.pojo.entity.User;
import com.example.demo.pojo.entity.VoiceAttempt;
import com.example.demo.pojo.entity.VoiceChallenge;
import com.example.demo.pojo.entity.VoiceprintProfile;
import com.example.demo.pojo.vo.ChallengeVO;
import com.example.demo.pojo.vo.CheckinResult;
import com.example.demo.voiceprint.audio.DtwMatcher;
import com.example.demo.voiceprint.audio.MfccExtractor;
import com.example.demo.voiceprint.audio.VadSplitter;
import com.example.demo.voiceprint.audio.WavReader;
import com.example.demo.voiceprint.challenge.ChallengeCodeGenerator;
import com.example.demo.voiceprint.crypto.TemplateCipher;
import com.example.demo.voiceprint.geo.GeoDistance;
import com.example.demo.voiceprint.storage.AudioStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 声纹 + 位置打卡。
 *
 * <p>打卡是“挑战码 + 声纹 + 位置”三件事的组合：</p>
 * <ol>
 *   <li>申请挑战码：绑定 user + task，60 秒有效；</li>
 *   <li>提交打卡：先判任务/名单/nonce，再判位置，最后做 VAD + MFCC + DTW；</li>
 *   <li>两项都过才写 {@code attendance_record} 并更新 {@code attendance_task_user}。</li>
 * </ol>
 *
 * <p>失败尝试必须用独立事务记录，见 {@link AttemptRecorder}。
 * 成功路径在一个事务里写 attempt / record / media、消费挑战码、回写名单。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final String SOURCE_VOICE = "VOICE";
    private static final String OWNER_TYPE_CHECKIN = "CHECKIN";
    private static final int REQUIRED_DIGIT_COUNT = 10;
    private static final int EXPECTED_SEGMENTS = 4;
    private static final int CHECKIN_AUDIO_RETENTION_DAYS = 7;
    private static final int DEFAULT_RADIUS_M = 200;
    /**
     * 二次 VAD 时向两侧扩展的时长。
     *
     * <p>整段 4.95 秒音频里的最大能量可能来自别的数字，导致第一遍 VAD 的阈值偏高，
     * 边缘少切一帧；注册时单数字音频的阈值更低，会多切一帧。同一段波形因此可能
     * 得到 77 / 78 帧两种 MFCC，分数掉到 0.5 左右。把粗切出的段向两侧各扩 100ms
     * 再单独跑一次 VAD，阈值只看这一个数字，边界就和注册时一致了。</p>
     */
    private static final double REFINE_MARGIN_SECONDS = 0.1;

    private final UserMapper userMapper;
    private final VoiceprintProfileMapper profileMapper;
    private final VoiceChallengeMapper challengeMapper;
    private final VoiceAttemptMapper attemptMapper;
    private final MediaObjectMapper mediaObjectMapper;
    private final AttendanceTaskMapper attendanceTaskMapper;
    private final AttendanceTaskUserMapper attendanceTaskUserMapper;
    private final AttendanceRecordMapper attendanceRecordMapper;
    private final TemplateCipher templateCipher;
    private final AudioStorage audioStorage;
    private final VoiceprintProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final AttemptRecorder attemptRecorder;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 申请 4 位挑战码。旧码会在同一事务里作废，避免用户拿旧码打卡。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChallengeVO issueChallenge(Long userId, Long taskId) {
        this.requireActiveUser(userId);
        if (taskId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "taskId 不能为空");
        }
        AttendanceTask task = attendanceTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "考勤任务不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        this.requireTaskOpen(task, now);
        AttendanceTaskUser taskUser = this.findTaskUser(taskId, userId);
        if (taskUser == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "你不在本次考勤名单中");
        }
        if (taskUser.getRecordId() != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "本次考勤已完成打卡");
        }
        if (profileMapper.countDistinctDigitTags(userId) < REQUIRED_DIGIT_COUNT) {
            throw new BusinessException(HttpStatus.CONFLICT, "请先完成声纹注册");
        }
        int recent = challengeMapper.countCreatedSince(userId, now.minusMinutes(1));
        if (recent >= properties.getChallengeRateLimitPerMinute()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }

        String digits = ChallengeCodeGenerator.generate(secureRandom);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expireAt = now.plusSeconds(properties.getChallengeTtlSeconds());

        challengeMapper.invalidateUnused(userId, taskId, now);

        VoiceChallenge challenge = new VoiceChallenge();
        challenge.setUserId(userId);
        challenge.setTaskId(taskId);
        challenge.setNonce(nonce);
        challenge.setDigits(digits);
        challenge.setExpireAt(expireAt);
        challenge.setAttempts(0);
        challenge.setCreatedAt(now);
        challengeMapper.insert(challenge);

        log.info("用户 {} 为任务 {} 申请声纹挑战码", userId, taskId);
        return new ChallengeVO(taskId, nonce, digits, expireAt,
                properties.getChallengeTtlSeconds(), this.promptOf(digits));
    }

    /**
     * 提交音频 + 坐标打卡。
     *
     * <p>位置失败不消耗挑战码失败次数，用户可以在有效期内挪到范围内重试；
     * 音频/声纹失败会写一条 FAIL attempt 并把 {@code attempts + 1}。</p>
     */
    public CheckinResult checkin(Long userId, Long taskId, String nonce, String clientDigits,
                                 String latitude, String longitude, String accuracy,
                                 boolean isMock, MultipartFile audio) {
        this.requireActiveUser(userId);
        if (taskId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "taskId 不能为空");
        }
        AttendanceTask task = attendanceTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "考勤任务不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        this.requireTaskOpen(task, now);
        AttendanceTaskUser taskUser = this.findTaskUser(taskId, userId);
        if (taskUser == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "你不在本次考勤名单中");
        }
        if (taskUser.getRecordId() != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "本次考勤已完成打卡");
        }

        VoiceChallenge challenge = this.requireChallenge(userId, taskId, nonce, clientDigits, now);
        if (profileMapper.countDistinctDigitTags(userId) < REQUIRED_DIGIT_COUNT) {
            throw new BusinessException(HttpStatus.CONFLICT, "请先完成声纹注册");
        }

        // 位置先判，避免白跑一遍 MFCC/DTW；位置失败不记录失败尝试
        LocationCheck location = this.checkLocation(task, latitude, longitude, accuracy, isMock);

        byte[] wavBytes = this.readAudio(audio);
        String audioSha = TemplateCipher.sha256Hex(wavBytes);
        if (attemptMapper.countByAudioSha(audioSha) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "这段录音已经提交过，请重新录制");
        }

        String relativePath = audioStorage.storeCheckin(taskId, userId, wavBytes);
        LocalDate retainUntil = LocalDate.now().plusDays(CHECKIN_AUDIO_RETENTION_DAYS);

        WavReader.AudioData audioData;
        try {
            audioData = WavReader.read(wavBytes,
                    properties.getCheckinMinSeconds(), properties.getCheckinMaxSeconds());
        } catch (BusinessException e) {
            this.recordFailure(userId, taskId, challenge, audioSha, relativePath,
                    null, 0, null, null, null, null, null, e.getMessage(), retainUntil);
            throw e;
        }

        Detection detection;
        try {
            detection = this.analyze(userId, challenge.getDigits(), audioData);
        } catch (BusinessException e) {
            this.recordFailure(userId, taskId, challenge, audioSha, relativePath,
                    audioData.durationMs(), 0, null, null, null, null, null,
                    e.getMessage(), retainUntil);
            throw e;
        } catch (RuntimeException e) {
            log.error("用户 {} 打卡算法异常，taskId={}", userId, taskId, e);
            this.recordFailure(userId, taskId, challenge, audioSha, relativePath,
                    audioData.durationMs(), 0, null, null, null, null, null,
                    "声纹处理失败，请重试", retainUntil);
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "声纹处理失败，请重试");
        }

        if (!detection.passed()) {
            this.recordFailure(userId, taskId, challenge, audioSha, relativePath,
                    audioData.durationMs(), detection.segCount(), detection.detectedDigits(),
                    detection.segScores(), detection.avgScore(), detection.minScore(),
                    detection.profileId(), detection.failReason(), retainUntil);
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, detection.failReason());
        }

        try {
            return this.commitSuccess(userId, task, challenge, detection, location,
                    audioSha, relativePath, audioData.durationMs(), retainUntil);
        } catch (RuntimeException e) {
            // 数据库事务已回滚，磁盘上刚写的录音必须补偿删除
            audioStorage.deleteByRelativePath(relativePath);
            throw e;
        }
    }

    /** 成功路径：attempt + record + media + 消费挑战码 + 回写名单，全部在一个事务里。 */
    private CheckinResult commitSuccess(Long userId, AttendanceTask task, VoiceChallenge challenge,
                                        Detection detection, LocationCheck location,
                                        String audioSha, String relativePath,
                                        int durationMs, LocalDate retainUntil) {
        LocalDateTime checkedAt = LocalDateTime.now();
        LocalDate recordDate = task.getStartAt().toLocalDate();

        CheckinResult result = transactionTemplate.execute(status -> {
            VoiceAttempt attempt = new VoiceAttempt();
            attempt.setChallengeId(challenge.getId());
            attempt.setUserId(userId);
            attempt.setTaskId(task.getId());
            attempt.setAudioSha256(audioSha);
            attempt.setDurationMs(durationMs);
            attempt.setSegCount(detection.segCount());
            attempt.setDetectedDigits(detection.detectedDigits());
            attempt.setSegScores(detection.segScores());
            attempt.setAvgScore(this.decimal(detection.avgScore()));
            attempt.setMinScore(this.decimal(detection.minScore()));
            attempt.setResult("PASS");
            attempt.setVpProfileId(detection.profileId());
            attempt.setCreatedAt(checkedAt);
            attemptMapper.insert(attempt);

            MediaObject media = new MediaObject();
            media.setOwnerType(OWNER_TYPE_CHECKIN);
            media.setOwnerId(attempt.getId());
            media.setLocalPath(relativePath);
            media.setSha256(audioSha);
            media.setRetainUntil(retainUntil);
            media.setDeleted(0);
            media.setCreatedAt(checkedAt);
            mediaObjectMapper.insert(media);

            AttendanceRecord record = new AttendanceRecord();
            record.setUserId(userId);
            record.setTaskId(task.getId());
            record.setRecordDate(recordDate);
            record.setCheckType(task.getCheckType());
            record.setChallengeId(challenge.getId());
            record.setDetectedDigits(detection.detectedDigits());
            record.setVpProfileId(detection.profileId());
            record.setMatchScore(this.decimal(detection.bestScore()));
            record.setAudioSha256(audioSha);
            record.setCheckLatitude(location.latitude());
            record.setCheckLongitude(location.longitude());
            record.setDistanceM(location.distanceM());
            record.setAccuracyM(location.accuracy());
            record.setIsMock(location.isMock());
            record.setSource(SOURCE_VOICE);
            record.setDeleted(0);
            record.setCreatedAt(checkedAt);
            attendanceRecordMapper.upsert(record);

            AttendanceRecord saved = attendanceRecordMapper
                    .selectByTaskAndUserIncludingDeleted(task.getId(), userId);
            if (saved == null) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "打卡记录写入失败");
            }

            int marked = attendanceTaskUserMapper.markChecked(
                    task.getId(), userId, saved.getId(), checkedAt);
            if (marked != 1) {
                throw new BusinessException(HttpStatus.CONFLICT, "本次考勤已完成打卡");
            }
            int consumed = challengeMapper.consume(challenge.getId(), checkedAt);
            if (consumed != 1) {
                throw new BusinessException(HttpStatus.GONE, "挑战码已失效，请重新获取");
            }

            return new CheckinResult(
                    saved.getId(), task.getId(), task.getCheckType(), checkedAt,
                    location.distanceM(), location.radiusM(), location.verified(),
                    location.accuracy(), location.isMock(),
                    this.round4(detection.avgScore()), this.round4(detection.minScore()),
                    detection.detectedDigits());
        });

        if (result == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "打卡失败，请稍后重试");
        }
        log.info("用户 {} 在任务 {} 声纹打卡成功，recordId={}，avg={}，min={}，distance={}",
                userId, task.getId(), result.getRecordId(), result.getAvgScore(),
                result.getMinScore(), result.getDistanceM());
        return result;
    }

    /**
     * VAD + MFCC + DTW 判定。
     *
     * <p>只和挑战码里出现的数字比，每段取相似度最高的数字作为识别结果；
     * 四项全过才 PASS：内容一致、平均分、最低分、最佳与次佳 margin。</p>
     */
    private Detection analyze(Long userId, String expectedDigits, WavReader.AudioData audioData) {
        List<VadSplitter.Segment> segments =
                VadSplitter.split(audioData.samples(), audioData.sampleRate());
        if (segments.size() != EXPECTED_SEGMENTS) {
            return Detection.failed(segments.size(),
                    "请每个数字之间停顿约 0.2 秒后重试（检测到 " + segments.size() + " 段）");
        }

        Map<Integer, List<Template>> templates = this.loadTemplates(userId);
        List<Integer> candidates = expectedDigits.chars()
                .map(character -> character - '0')
                .distinct()
                .sorted()
                .boxed()
                .toList();
        for (Integer digit : candidates) {
            if (!templates.containsKey(digit)) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "声纹数据异常，请联系管理员重置后重新注册");
            }
        }

        StringBuilder detected = new StringBuilder(EXPECTED_SEGMENTS);
        double[] bestScores = new double[EXPECTED_SEGMENTS];
        double[] margins = new double[EXPECTED_SEGMENTS];
        double overallBest = -1;
        Long overallProfileId = null;

        for (int i = 0; i < segments.size(); i++) {
            VadSplitter.Segment segment = segments.get(i);
            short[] speech = this.refineSegment(
                    audioData.samples(), segment, audioData.sampleRate());
            float[][] mfcc = MfccExtractor.extract(speech);
            if (mfcc.length == 0) {
                return Detection.failed(segments.size(), "录音太短，请重新录制");
            }

            double best = -1;
            double second = -1;
            int bestDigit = -1;
            Long bestProfileId = null;

            for (Integer digit : candidates) {
                double digitBest = -1;
                Long digitProfileId = null;
                for (Template template : templates.get(digit)) {
                    double similarity = DtwMatcher.similarity(mfcc, template.matrix());
                    if (similarity > digitBest) {
                        digitBest = similarity;
                        digitProfileId = template.id();
                    }
                }
                if (digitBest > best) {
                    second = best;
                    best = digitBest;
                    bestDigit = digit;
                    bestProfileId = digitProfileId;
                } else if (digitBest > second) {
                    second = digitBest;
                }
            }

            log.debug("打卡分段 {}：{}..{}，候选={}，最佳={}（{}），次佳={}",
                    i, segment.startSample(), segment.endSample(), candidates,
                    String.format(Locale.ROOT, "%.4f", best), bestDigit,
                    String.format(Locale.ROOT, "%.4f", second));

            detected.append(bestDigit);
            bestScores[i] = Math.max(0, best);
            margins[i] = Math.max(0, best - Math.max(0, second));
            if (best > overallBest) {
                overallBest = best;
                overallProfileId = bestProfileId;
            }
        }

        double avg = Arrays.stream(bestScores).average().orElse(0);
        double min = Arrays.stream(bestScores).min().orElse(0);
        double margin = Arrays.stream(margins).min().orElse(0);
        String segScores = this.jsonScores(bestScores);
        String detectedDigits = detected.toString();

        if (!detectedDigits.equals(expectedDigits)) {
            return new Detection(EXPECTED_SEGMENTS, detectedDigits, segScores,
                    avg, min, Math.max(0, overallBest), overallProfileId,
                    false, "朗读内容不正确，请重新念");
        }
        if (avg < properties.getThresholds().getAvg()) {
            return new Detection(EXPECTED_SEGMENTS, detectedDigits, segScores,
                    avg, min, Math.max(0, overallBest), overallProfileId,
                    false, "声纹相似度不足，请重新打卡");
        }
        if (min < properties.getThresholds().getMin()) {
            return new Detection(EXPECTED_SEGMENTS, detectedDigits, segScores,
                    avg, min, Math.max(0, overallBest), overallProfileId,
                    false, "声纹相似度不足，请重新打卡");
        }
        if (margin < properties.getThresholds().getMargin()) {
            return new Detection(EXPECTED_SEGMENTS, detectedDigits, segScores,
                    avg, min, Math.max(0, overallBest), overallProfileId,
                    false, "发音不够清晰，请重新朗读");
        }
        return new Detection(EXPECTED_SEGMENTS, detectedDigits, segScores,
                avg, min, Math.max(0, overallBest), overallProfileId, true, null);
    }

    /**
     * 对粗切出的单个数字做二次 VAD。
     *
     * <p>向两侧各扩 100ms，让这一段拥有自己的最大能量和底噪，切出来的边界才和注册时
     * 单数字音频的 VAD 一致。扩边后如果切出多段（用户停顿特别短），取最长的一段。</p>
     */
    private short[] refineSegment(short[] samples, VadSplitter.Segment coarse,
                                  int sampleRate) {
        int margin = (int) Math.round(REFINE_MARGIN_SECONDS * sampleRate);
        int start = Math.max(0, coarse.startSample() - margin);
        int end = Math.min(samples.length, coarse.endSample() + margin);
        short[] expanded = Arrays.copyOfRange(samples, start, end);
        List<VadSplitter.Segment> refined = VadSplitter.split(expanded, sampleRate);
        if (refined.size() == 1) {
            VadSplitter.Segment segment = refined.get(0);
            return Arrays.copyOfRange(expanded,
                    segment.startSample(), segment.endSample());
        }
        if (refined.size() > 1) {
            VadSplitter.Segment longest = refined.stream()
                    .max(Comparator.comparingInt(VadSplitter.Segment::length))
                    .orElse(refined.get(0));
            return Arrays.copyOfRange(expanded,
                    longest.startSample(), longest.endSample());
        }
        return Arrays.copyOfRange(samples, coarse.startSample(), coarse.endSample());
    }

    /** 解密当前用户全部数字模板；单条损坏只跳过，不阻塞打卡。 */
    private Map<Integer, List<Template>> loadTemplates(Long userId) {
        Map<Integer, List<Template>> templates = new HashMap<>();
        for (VoiceprintProfile profile : profileMapper.selectEffectiveByUser(userId)) {
            int digit;
            try {
                digit = Integer.parseInt(profile.getPhraseTag()
                        .substring("DIGIT_".length()));
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                continue;
            }
            try {
                float[][] matrix = TemplateCipher.decodeMatrix(
                        templateCipher.decrypt(profile.getTemplateCipher()),
                        profile.getFrames());
                templates.computeIfAbsent(digit, key -> new ArrayList<>())
                        .add(new Template(profile.getId(), matrix));
            } catch (RuntimeException e) {
                log.warn("跳过损坏的声纹模板 profileId={}", profile.getId());
            }
        }
        return templates;
    }

    /** 位置校验；任务没有坐标时只尽量记录，不拦截。 */
    private LocationCheck checkLocation(AttendanceTask task, String latitudeText,
                                        String longitudeText, String accuracyText,
                                        boolean isMock) {
        Double latitude = this.parseDouble(latitudeText);
        Double longitude = this.parseDouble(longitudeText);
        Double accuracy = this.parseDouble(accuracyText);

        boolean taskHasLocation = task.getLatitude() != null && task.getLongitude() != null;
        if (!taskHasLocation) {
            if (latitude == null || longitude == null
                    || !this.validCoordinate(latitude, longitude)) {
                return LocationCheck.none();
            }
            return new LocationCheck(latitude, longitude, null, null,
                    accuracy != null && accuracy >= 0 ? accuracy : null, isMock, false);
        }

        if (latitude == null || longitude == null || accuracy == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "定位信息不完整");
        }
        if (!this.validCoordinate(latitude, longitude)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "定位坐标不合法");
        }
        if (isMock) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "检测到模拟位置，请关闭模拟定位后重试");
        }
        if (accuracy <= 0 || accuracy > properties.getMaxLocationAccuracyMeters()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "定位精度太低，请到空旷处重试");
        }

        int radius = task.getRadiusM() == null ? DEFAULT_RADIUS_M : task.getRadiusM();
        double distance = GeoDistance.haversineMeters(
                latitude, longitude, task.getLatitude(), task.getLongitude());
        double rounded = this.round1(distance);
        if (rounded > radius) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, String.format(Locale.ROOT,
                    "你距离打卡点约 %.0f 米，超出 %d 米范围", rounded, radius));
        }
        return new LocationCheck(latitude, longitude, rounded, radius, accuracy, false, true);
    }

    /** 校验挑战码：存在、属于本人和本任务、未用、未过期、失败次数未超限、客户端 digits 一致。 */
    private VoiceChallenge requireChallenge(Long userId, Long taskId, String nonce,
                                            String clientDigits, LocalDateTime now) {
        if (nonce == null || nonce.isBlank()) {
            throw new BusinessException(HttpStatus.GONE, "挑战码已失效，请重新获取");
        }
        VoiceChallenge challenge = challengeMapper.selectOne(
                new LambdaQueryWrapper<VoiceChallenge>()
                        .eq(VoiceChallenge::getNonce, nonce.trim()));
        if (challenge == null
                || !userId.equals(challenge.getUserId())
                || !taskId.equals(challenge.getTaskId())
                || challenge.getUsedAt() != null
                || !challenge.getExpireAt().isAfter(now)) {
            throw new BusinessException(HttpStatus.GONE, "挑战码已失效，请重新获取");
        }
        if (challenge.getAttempts() != null
                && challenge.getAttempts() >= properties.getChallengeMaxAttempts()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "失败次数过多，请重新获取挑战码");
        }
        if (clientDigits == null || !clientDigits.trim().equals(challenge.getDigits())) {
            throw new BusinessException(HttpStatus.GONE, "挑战码已失效，请重新获取");
        }
        return challenge;
    }

    private void recordFailure(Long userId, Long taskId, VoiceChallenge challenge,
                               String audioSha, String relativePath, Integer durationMs,
                               int segCount, String detectedDigits, String segScores,
                               Double avgScore, Double minScore, Long profileId,
                               String failReason, LocalDate retainUntil) {
        try {
            VoiceAttempt attempt = new VoiceAttempt();
            attempt.setChallengeId(challenge.getId());
            attempt.setUserId(userId);
            attempt.setTaskId(taskId);
            attempt.setAudioSha256(audioSha);
            attempt.setDurationMs(durationMs);
            attempt.setSegCount(segCount);
            attempt.setDetectedDigits(detectedDigits);
            attempt.setSegScores(segScores);
            attempt.setAvgScore(this.decimal(avgScore));
            attempt.setMinScore(this.decimal(minScore));
            attempt.setResult("FAIL");
            attempt.setFailReason(this.truncate(failReason, 128));
            attempt.setVpProfileId(profileId);
            attempt.setCreatedAt(LocalDateTime.now());
            attemptRecorder.record(attempt, relativePath, audioSha, retainUntil);
        } catch (RuntimeException e) {
            log.error("记录打卡失败尝试出错，userId={}，taskId={}", userId, taskId, e);
            audioStorage.deleteByRelativePath(relativePath);
        }
    }

    private AttendanceTaskUser findTaskUser(Long taskId, Long userId) {
        return attendanceTaskUserMapper.selectOne(
                new LambdaQueryWrapper<AttendanceTaskUser>()
                        .eq(AttendanceTaskUser::getTaskId, taskId)
                        .eq(AttendanceTaskUser::getUserId, userId));
    }

    private void requireTaskOpen(AttendanceTask task, LocalDateTime now) {
        if (now.isBefore(task.getStartAt())) {
            throw new BusinessException(HttpStatus.CONFLICT, "考勤还没开始");
        }
        if (!now.isBefore(task.getEndAt())) {
            throw new BusinessException(HttpStatus.CONFLICT, "考勤已结束");
        }
    }

    /** 打卡只对未冻结用户开放；冻结用户即使 token 还没过期也不能打卡。 */
    private void requireActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "用户不存在或已被冻结，请重新登录");
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

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean validCoordinate(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }

    private String promptOf(String digits) {
        return "请匀速朗读：" + String.join(" ", digits.split("")) + "，每个数字之间停顿约 0.2 秒";
    }

    private String jsonScores(double[] scores) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < scores.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%.4f", scores[i]));
        }
        return builder.append(']').toString();
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Double round4(Double value) {
        return value == null ? null : Math.round(value * 10000.0) / 10000.0;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record Template(Long id, float[][] matrix) {
    }

    private record LocationCheck(Double latitude, Double longitude, Double distanceM,
                                 Integer radiusM, Double accuracy, boolean isMock,
                                 boolean verified) {

        static LocationCheck none() {
            return new LocationCheck(null, null, null, null, null, false, false);
        }
    }

    private record Detection(int segCount, String detectedDigits, String segScores,
                             Double avgScore, Double minScore, Double bestScore,
                             Long profileId, boolean passed, String failReason) {

        static Detection failed(int segCount, String reason) {
            return new Detection(segCount, null, null, null, null, null, null, false, reason);
        }
    }
}
