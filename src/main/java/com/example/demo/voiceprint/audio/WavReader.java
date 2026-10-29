package com.example.demo.voiceprint.audio;

import com.example.demo.common.BusinessException;
import org.springframework.http.HttpStatus;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * WAV 读取与格式校验。
 *
 * <p>只接受 16000Hz / 16bit / 单声道 / PCM signed little-endian，不做重采样。
 * 格式不对统一返回 400，提示语直接给用户看。</p>
 */
public final class WavReader {

    /** 音频采样率，全项目固定 16kHz */
    public static final int SAMPLE_RATE = 16000;

    /** 业务上限 500KB；框架上限 1MB 由 multipart 配置兜底 */
    public static final long MAX_BYTES = 500L * 1024L;

    private static final String FORMAT_MSG = "录音格式不正确，请上传 16kHz / 16bit / 单声道 WAV";

    private WavReader() {
    }

    /**
     * 解析后的音频数据。
     *
     * @param samples    16bit PCM 样本
     * @param sampleRate 采样率（固定 16000）
     * @param durationMs 录音时长（毫秒）
     */
    public record AudioData(short[] samples, int sampleRate, int durationMs) {
    }

    /**
     * 读取并校验 WAV。
     *
     * @param bytes      原始 WAV 文件字节
     * @param minSeconds 允许的最短时长
     * @param maxSeconds 允许的最长时长
     */
    public static AudioData read(byte[] bytes, double minSeconds, double maxSeconds) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "录音文件为空");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "录音文件不能超过 500KB");
        }

        short[] samples;
        try (AudioInputStream input = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
            AudioFormat format = input.getFormat();
            WavReader.checkFormat(format);

            byte[] pcm = input.readAllBytes();
            if (pcm.length < 2) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "录音内容为空");
            }
            samples = new short[pcm.length / 2];
            for (int i = 0; i < samples.length; i++) {
                int low = pcm[i * 2] & 0xFF;
                int high = pcm[i * 2 + 1] << 8;
                samples[i] = (short) (low | high);
            }
        } catch (UnsupportedAudioFileException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, FORMAT_MSG);
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "录音文件读取失败，请重新上传");
        }

        int durationMs = (int) Math.round(samples.length * 1000.0 / SAMPLE_RATE);
        double durationSeconds = durationMs / 1000.0;
        if (durationSeconds < minSeconds) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "录音太短，请重新录制（至少 " + secondsText(minSeconds) + " 秒）");
        }
        if (durationSeconds > maxSeconds) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "录音太长，请重新录制（" + secondsText(minSeconds)
                            + " ~ " + secondsText(maxSeconds) + " 秒）");
        }
        return new AudioData(samples, SAMPLE_RATE, durationMs);
    }

    private static void checkFormat(AudioFormat format) {
        if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                || format.getSampleSizeInBits() != 16
                || Math.round(format.getSampleRate()) != SAMPLE_RATE
                || format.getChannels() != 1
                || format.isBigEndian()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, FORMAT_MSG);
        }
    }

    private static String secondsText(double seconds) {
        return seconds == Math.rint(seconds) ? String.valueOf((int) seconds) : String.valueOf(seconds);
    }
}
