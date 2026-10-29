package com.example.demo.voiceprint.audio;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * “像不像人声”的最小校验。
 *
 * <p>VAD 只回答“这段有没有能量”，挡不住咳嗽、碰撞、键盘声、空调噪声、低频轰鸣。
 * 这里用两个互补的指标：</p>
 * <ul>
 *   <li><b>浊音比例</b>：人声（数字里的元音）在 60~400Hz 有明显的周期性，
 *       归一化自相关会出现高峰；噪声一般没有。</li>
 *   <li><b>语音频带能量占比</b>：人声主要能量在 150~3400Hz；
 *       电流声、发动机/空调低频轰鸣能量集中在 150Hz 以下。</li>
 * </ul>
 *
 * <p>注意：这不能识别“念的是哪个数字”，只能判断“是不是清晰的人声”。
 * 内容是否正确仍由前端提示 + 后续打卡的 DTW 判定负责。</p>
 */
public final class SpeechValidator {

    /** 浊音帧占比低于这个值，认为不是清晰人声 */
    public static final double MIN_VOICED_RATIO = 0.25;

    /** 150~3400Hz 频带能量占比低于这个值，认为环境噪声/低频轰鸣太重 */
    public static final double MIN_SPEECH_BAND_RATIO = 0.25;

    /** 相邻帧归一化频谱的 L1 距离低于这个值，认为声音太“平”（蜂鸣声/持续单音） */
    public static final double MIN_SPECTRAL_FLUX = 0.10;

    private static final int FRAME_LENGTH = 400;   // 25ms
    private static final int FRAME_SHIFT = 160;    // 10ms
    private static final int FFT_SIZE = 512;
    /** 帧能量达到最大帧能量的这个比例才算“有效帧” */
    private static final double ACTIVE_ENERGY_RATIO = 0.05;
    /** 归一化自相关峰值超过它才算浊音帧 */
    private static final double VOICING_THRESHOLD = 0.35;
    private static final double MIN_PITCH_HZ = 60;
    private static final double MAX_PITCH_HZ = 400;
    private static final int MIN_ACTIVE_FRAMES = 3;
    private static final double SPEECH_BAND_LOW_HZ = 150;
    private static final double SPEECH_BAND_HIGH_HZ = 3400;

    private SpeechValidator() {
    }

    /**
     * 语音段的声学指标。
     *
     * @param voicedRatio     浊音帧占有效帧的比例，0~1
     * @param speechBandRatio 150~3400Hz 能量占全部能量的比例，0~1
     * @param spectralFlux    相邻有效帧归一化频谱的平均 L1 距离，越大越像在说话
     */
    public record VoiceMetrics(double voicedRatio, double speechBandRatio, double spectralFlux) {

        public boolean humanLike() {
            return voicedRatio >= MIN_VOICED_RATIO
                    && speechBandRatio >= MIN_SPEECH_BAND_RATIO
                    && spectralFlux >= MIN_SPECTRAL_FLUX;
        }
    }

    public static VoiceMetrics analyze(short[] samples, int sampleRate) {
        if (samples == null || samples.length < FRAME_LENGTH) {
            return new VoiceMetrics(0, 0, 0);
        }

        int frameCount = 1 + (samples.length - FRAME_LENGTH) / FRAME_SHIFT;
        double[] energies = new double[frameCount];
        double maxEnergy = 0;
        for (int frame = 0; frame < frameCount; frame++) {
            int start = frame * FRAME_SHIFT;
            double energy = 0;
            for (int i = 0; i < FRAME_LENGTH; i++) {
                double value = samples[start + i];
                energy += value * value;
            }
            energies[frame] = energy;
            maxEnergy = Math.max(maxEnergy, energy);
        }
        if (maxEnergy <= 0) {
            return new VoiceMetrics(0, 0, 0);
        }

        int minLag = Math.max(1, (int) Math.floor(sampleRate / MAX_PITCH_HZ));
        int maxLag = Math.min(FRAME_LENGTH - 1, (int) Math.ceil(sampleRate / MIN_PITCH_HZ));
        double activeThreshold = maxEnergy * ACTIVE_ENERGY_RATIO;
        double[] window = hammingWindow();

        int activeFrames = 0;
        int voicedFrames = 0;
        double totalPower = 0;
        double speechBandPower = 0;
        double fluxSum = 0;
        int fluxPairs = 0;
        double[] previousPower = null;
        DoubleFFT_1D fft = new DoubleFFT_1D(FFT_SIZE);
        double[] spectrum = new double[FFT_SIZE];
        double[] power = new double[FFT_SIZE / 2 + 1];

        for (int frame = 0; frame < frameCount; frame++) {
            if (energies[frame] < activeThreshold) {
                continue;
            }
            activeFrames++;
            int start = frame * FRAME_SHIFT;
            if (maxNormalizedAutocorrelation(samples, start, minLag, maxLag) >= VOICING_THRESHOLD) {
                voicedFrames++;
            }

            double mean = 0;
            for (int i = 0; i < FRAME_LENGTH; i++) {
                mean += samples[start + i];
            }
            mean /= FRAME_LENGTH;
            for (int i = 0; i < FFT_SIZE; i++) {
                spectrum[i] = i < FRAME_LENGTH ? (samples[start + i] - mean) * window[i] : 0;
            }
            fft.realForward(spectrum);

            power[0] = spectrum[0] * spectrum[0];
            power[FFT_SIZE / 2] = spectrum[1] * spectrum[1];
            double frameTotal = power[0] + power[FFT_SIZE / 2];
            double frameBand = 0;
            for (int bin = 1; bin < FFT_SIZE / 2; bin++) {
                double value = spectrum[2 * bin] * spectrum[2 * bin]
                        + spectrum[2 * bin + 1] * spectrum[2 * bin + 1];
                power[bin] = value;
                frameTotal += value;
            }
            for (int bin = 0; bin <= FFT_SIZE / 2; bin++) {
                double hz = bin * (double) sampleRate / FFT_SIZE;
                if (hz >= SPEECH_BAND_LOW_HZ && hz <= SPEECH_BAND_HIGH_HZ) {
                    frameBand += power[bin];
                }
            }
            totalPower += frameTotal;
            speechBandPower += frameBand;

            if (frameTotal > 0) {
                double[] normalized = new double[power.length];
                for (int bin = 0; bin < power.length; bin++) {
                    normalized[bin] = power[bin] / frameTotal;
                }
                if (previousPower != null) {
                    double flux = 0;
                    for (int bin = 0; bin < normalized.length; bin++) {
                        flux += Math.abs(normalized[bin] - previousPower[bin]);
                    }
                    fluxSum += flux;
                    fluxPairs++;
                }
                previousPower = normalized;
            }
        }

        if (activeFrames < MIN_ACTIVE_FRAMES) {
            return new VoiceMetrics(0, 0, 0);
        }
        double voicedRatio = (double) voicedFrames / activeFrames;
        double bandRatio = totalPower > 0 ? speechBandPower / totalPower : 0;
        double spectralFlux = fluxPairs > 0 ? fluxSum / fluxPairs : 0;
        return new VoiceMetrics(voicedRatio, bandRatio, spectralFlux);
    }

    private static double[] hammingWindow() {
        double[] window = new double[FRAME_LENGTH];
        for (int i = 0; i < FRAME_LENGTH; i++) {
            window[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (FRAME_LENGTH - 1));
        }
        return window;
    }

    /**
     * 一帧内 60~400Hz 对应滞后范围内的最大归一化自相关。
     */
    private static double maxNormalizedAutocorrelation(short[] samples, int start,
                                                       int minLag, int maxLag) {
        double mean = 0;
        for (int i = 0; i < FRAME_LENGTH; i++) {
            mean += samples[start + i];
        }
        mean /= FRAME_LENGTH;

        double[] frame = new double[FRAME_LENGTH];
        double totalEnergy = 0;
        for (int i = 0; i < FRAME_LENGTH; i++) {
            frame[i] = samples[start + i] - mean;
            totalEnergy += frame[i] * frame[i];
        }
        if (totalEnergy <= 1e-6) {
            return 0;
        }

        double best = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double cross = 0;
            double energyFirst = 0;
            double energySecond = 0;
            int overlap = FRAME_LENGTH - lag;
            for (int i = 0; i < overlap; i++) {
                double first = frame[i];
                double second = frame[i + lag];
                cross += first * second;
                energyFirst += first * first;
                energySecond += second * second;
            }
            if (energyFirst > 1e-6 && energySecond > 1e-6) {
                double normalized = cross / Math.sqrt(energyFirst * energySecond);
                if (normalized > best) {
                    best = normalized;
                }
            }
        }
        return best;
    }
}
