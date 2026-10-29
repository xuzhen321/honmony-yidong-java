package com.example.demo.voiceprint.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 基于短时能量的 VAD（语音活动检测）。
 *
 * <p>参数按设计文档固定：25ms 帧长 / 10ms 帧移；短时能量 &gt; 底噪 × 3 判为语音；
 * 底噪取全部帧能量的 10% 分位数；最短语音段 150ms；更短的静音间隔（&lt;200ms）合并；
 * 段两端各留 50ms 余量。</p>
 */
public final class VadSplitter {

    private static final double FRAME_SECONDS = 0.025;
    private static final double HOP_SECONDS = 0.010;
    private static final double MIN_SPEECH_SECONDS = 0.150;
    private static final double MIN_SILENCE_SECONDS = 0.200;
    private static final double PADDING_SECONDS = 0.050;
    private static final double NOISE_FLOOR_PERCENTILE = 0.10;

    /** 最大帧能量至少是底噪的 4 倍，否则认为整段只有环境噪声、没有语音 */
    private static final double MIN_SNR = 4.0;

    private VadSplitter() {
    }

    /**
     * 一个语音段，下标是样本下标，左闭右开。
     */
    public record Segment(int startSample, int endSample) {

        public int length() {
            return endSample - startSample;
        }
    }

    /**
     * 把整段录音切成若干语音段。
     *
     * @return 语音段列表；全静音时返回空列表
     */
    public static List<Segment> split(short[] samples, int sampleRate) {
        if (samples == null || samples.length == 0) {
            return List.of();
        }

        int frameLength = Math.max(1, (int) Math.round(FRAME_SECONDS * sampleRate));
        int hop = Math.max(1, (int) Math.round(HOP_SECONDS * sampleRate));
        int frameCount = samples.length < frameLength
                ? 1
                : 1 + (samples.length - frameLength) / hop;

        double[] energies = new double[frameCount];
        for (int i = 0; i < frameCount; i++) {
            int start = i * hop;
            int end = Math.min(samples.length, start + frameLength);
            energies[i] = energy(samples, start, end);
        }

        double noiseFloor = percentile(energies, NOISE_FLOOR_PERCENTILE);
        double maxEnergy = Arrays.stream(energies).max().orElse(0);
        if (maxEnergy <= 0) {
            return List.of();
        }
        // 没有明显的信噪比：整段都是环境噪声 / 空调声 / 持续蜂鸣，直接判为无语音
        if (noiseFloor > 0 && maxEnergy < noiseFloor * MIN_SNR) {
            return List.of();
        }

        double threshold = noiseFloor * 3.0;
        if (threshold <= 0) {
            threshold = Math.max(1.0, maxEnergy * 0.01);
        }

        // 先把连续的语音帧合成 run（样本区间），再按最短静音间隔合并相邻 run
        List<int[]> runs = new ArrayList<>();
        int runStart = -1;
        for (int i = 0; i < frameCount; i++) {
            boolean voiced = energies[i] > threshold;
            if (voiced && runStart < 0) {
                runStart = i;
            } else if (!voiced && runStart >= 0) {
                runs.add(new int[]{runStart * hop, Math.min(samples.length, i * hop + frameLength)});
                runStart = -1;
            }
        }
        if (runStart >= 0) {
            runs.add(new int[]{runStart * hop,
                    Math.min(samples.length, (frameCount - 1) * hop + frameLength)});
        }

        int minSilenceSamples = (int) Math.round(MIN_SILENCE_SECONDS * sampleRate);
        List<int[]> merged = new ArrayList<>();
        for (int[] run : runs) {
            if (!merged.isEmpty() && run[0] - merged.get(merged.size() - 1)[1] < minSilenceSamples) {
                merged.get(merged.size() - 1)[1] = run[1];
            } else {
                merged.add(new int[]{run[0], run[1]});
            }
        }

        int minSpeechSamples = (int) Math.round(MIN_SPEECH_SECONDS * sampleRate);
        int padding = (int) Math.round(PADDING_SECONDS * sampleRate);
        List<Segment> segments = new ArrayList<>();
        for (int[] run : merged) {
            if (run[1] - run[0] < minSpeechSamples) {
                continue;
            }
            int start = Math.max(0, run[0] - padding);
            int end = Math.min(samples.length, run[1] + padding);
            if (!segments.isEmpty() && start <= segments.get(segments.size() - 1).endSample()) {
                Segment previous = segments.remove(segments.size() - 1);
                start = previous.startSample();
            }
            segments.add(new Segment(start, end));
        }
        return segments;
    }

    private static double energy(short[] samples, int start, int end) {
        double sum = 0;
        for (int i = start; i < end; i++) {
            double value = samples[i];
            sum += value * value;
        }
        return sum;
    }

    /** 线性插值分位数，p ∈ [0,1]。 */
    private static double percentile(double[] values, double p) {
        if (values.length == 0) {
            return 0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double position = p * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sorted[lower];
        }
        double weight = position - lower;
        return sorted[lower] * (1 - weight) + sorted[upper] * weight;
    }
}
