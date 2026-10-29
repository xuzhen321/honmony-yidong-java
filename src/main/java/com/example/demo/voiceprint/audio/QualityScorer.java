package com.example.demo.voiceprint.audio;

import java.util.List;

/**
 * 注册音频质量分（0~100）。
 *
 * <p>由五项启发式加权：时长、VAD 段数、平均能量、削波比例、静音占比。
 * 低于 60 分拒绝入库，避免脏模板。</p>
 */
public final class QualityScorer {

    /** 低于这个分数拒绝注册 */
    public static final int MIN_ACCEPTABLE = 60;

    private static final int DURATION_SCORE = 25;
    private static final int SEGMENT_SCORE = 20;
    private static final int ENERGY_SCORE = 25;
    private static final int CLIPPING_SCORE = 15;
    private static final int SILENCE_SCORE = 15;

    private QualityScorer() {
    }

    public static int score(short[] samples, int sampleRate, List<VadSplitter.Segment> segments) {
        if (samples == null || samples.length == 0) {
            return 0;
        }

        double durationSeconds = samples.length / (double) sampleRate;
        double duration = durationSeconds >= 0.5 && durationSeconds <= 1.5 ? DURATION_SCORE : 0;

        double segment = segments != null && segments.size() == 1 ? SEGMENT_SCORE : 0;

        double rms = rms(samples);
        double dbfs = 20 * Math.log10(Math.max(rms / 32768.0, 1e-12));
        double energy;
        if (dbfs >= -20) {
            energy = ENERGY_SCORE;
        } else if (dbfs <= -45) {
            energy = 0;
        } else {
            energy = ENERGY_SCORE * (dbfs + 45) / 25;
        }

        double clippingRatio = clippingRatio(samples);
        double clipping;
        if (clippingRatio <= 0.0005) {
            clipping = CLIPPING_SCORE;
        } else if (clippingRatio >= 0.05) {
            clipping = 0;
        } else {
            clipping = CLIPPING_SCORE * (0.05 - clippingRatio) / (0.05 - 0.0005);
        }

        int speechSamples = segments == null ? 0
                : segments.stream().mapToInt(VadSplitter.Segment::length).sum();
        double speechRatio = (double) speechSamples / samples.length;
        double silence;
        if (speechRatio >= 0.2 && speechRatio <= 0.9) {
            silence = SILENCE_SCORE;
        } else if (speechRatio < 0.2) {
            silence = SILENCE_SCORE * speechRatio / 0.2;
        } else {
            silence = SILENCE_SCORE * (1.0 - speechRatio) / 0.1;
        }

        double total = duration + segment + energy + clipping + Math.max(0, silence);
        return (int) Math.round(Math.max(0, Math.min(100, total)));
    }

    private static double rms(short[] samples) {
        double sum = 0;
        for (short sample : samples) {
            sum += (double) sample * sample;
        }
        return Math.sqrt(sum / samples.length);
    }

    private static double clippingRatio(short[] samples) {
        int clipped = 0;
        for (short sample : samples) {
            if (Math.abs((int) sample) >= 32000) {
                clipped++;
            }
        }
        return (double) clipped / samples.length;
    }
}
