package com.example.demo.voiceprint.audio;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * MFCC 特征提取。
 *
 * <p>参数按设计文档固定：16kHz、预加重 0.97、25ms 汉明窗、10ms 帧移、
 * 512 点 FFT、26 个 Mel 滤波器、频率范围 20~8000Hz、13 维倒谱系数，最后做逐维 CMVN。</p>
 */
public final class MfccExtractor {

    public static final int SAMPLE_RATE = 16000;
    public static final int FRAME_LENGTH = 400;   // 25ms
    public static final int FRAME_SHIFT = 160;    // 10ms
    public static final int FFT_SIZE = 512;
    public static final int MEL_FILTERS = 26;
    public static final int COEFFICIENTS = 13;

    private static final double PRE_EMPHASIS = 0.97;
    private static final double MIN_FREQ_HZ = 20;
    private static final double MAX_FREQ_HZ = 8000;
    private static final double LOG_FLOOR = 1e-10;

    private static final double[][] MEL_FILTER_BANK = buildMelFilterBank();

    private MfccExtractor() {
    }

    /**
     * 提取一个语音段的 MFCC 矩阵。
     *
     * @param samples 语音段样本（不建议包含大段静音）
     * @return {@code frames × 13} 的矩阵，已逐维 CMVN
     */
    public static float[][] extract(short[] samples) {
        if (samples == null || samples.length == 0) {
            return new float[0][COEFFICIENTS];
        }

        double[] emphasized = new double[samples.length];
        emphasized[0] = samples[0];
        for (int i = 1; i < samples.length; i++) {
            emphasized[i] = samples[i] - PRE_EMPHASIS * samples[i - 1];
        }

        int frameCount = samples.length < FRAME_LENGTH
                ? 1
                : 1 + (samples.length - FRAME_LENGTH) / FRAME_SHIFT;

        double[] window = hammingWindow(FRAME_LENGTH);
        DoubleFFT_1D fft = new DoubleFFT_1D(FFT_SIZE);
        double[] buffer = new double[FFT_SIZE];
        float[][] mfcc = new float[frameCount][COEFFICIENTS];

        for (int frame = 0; frame < frameCount; frame++) {
            int start = frame * FRAME_SHIFT;
            int end = Math.min(samples.length, start + FRAME_LENGTH);

            // 帧长不足 400 时右侧补零，保证每帧都做 512 点 FFT
            for (int i = 0; i < FFT_SIZE; i++) {
                buffer[i] = i < end - start ? emphasized[start + i] * window[i] : 0;
            }
            fft.realForward(buffer);

            double[] logEnergies = melLogEnergies(buffer);
            for (int k = 0; k < COEFFICIENTS; k++) {
                double sum = 0;
                for (int m = 0; m < MEL_FILTERS; m++) {
                    sum += logEnergies[m] * Math.cos(Math.PI * k * (m + 0.5) / MEL_FILTERS);
                }
                double scale = k == 0 ? Math.sqrt(1.0 / MEL_FILTERS) : Math.sqrt(2.0 / MEL_FILTERS);
                mfcc[frame][k] = (float) (sum * scale);
            }
        }

        cepstralMeanVarianceNormalization(mfcc);
        return mfcc;
    }

    /** JTransforms 的 realForward 输出是打包格式：a[0]=DC，a[1]=Nyquist，之后是 Re/Im。 */
    private static double[] melLogEnergies(double[] spectrum) {
        double[] power = new double[FFT_SIZE / 2 + 1];
        power[0] = spectrum[0] * spectrum[0];
        power[FFT_SIZE / 2] = spectrum[1] * spectrum[1];
        for (int k = 1; k < FFT_SIZE / 2; k++) {
            double real = spectrum[2 * k];
            double imaginary = spectrum[2 * k + 1];
            power[k] = real * real + imaginary * imaginary;
        }

        double[] logEnergies = new double[MEL_FILTERS];
        for (int m = 0; m < MEL_FILTERS; m++) {
            double sum = 0;
            for (int k = 0; k < power.length; k++) {
                sum += power[k] * MEL_FILTER_BANK[m][k];
            }
            logEnergies[m] = Math.log(Math.max(sum, LOG_FLOOR));
        }
        return logEnergies;
    }

    private static double[][] buildMelFilterBank() {
        double minMel = hzToMel(MIN_FREQ_HZ);
        double maxMel = hzToMel(MAX_FREQ_HZ);
        int points = MEL_FILTERS + 2;
        double[] melPoints = new double[points];
        for (int i = 0; i < points; i++) {
            melPoints[i] = minMel + (maxMel - minMel) * i / (points - 1);
        }

        int[] bins = new int[points];
        for (int i = 0; i < points; i++) {
            double hz = melToHz(melPoints[i]);
            bins[i] = (int) Math.floor((FFT_SIZE + 1) * hz / SAMPLE_RATE);
            bins[i] = Math.max(0, Math.min(FFT_SIZE / 2, bins[i]));
        }

        double[][] filters = new double[MEL_FILTERS][FFT_SIZE / 2 + 1];
        for (int m = 1; m <= MEL_FILTERS; m++) {
            int left = bins[m - 1];
            int center = bins[m];
            int right = bins[m + 1];
            for (int k = left; k < center; k++) {
                if (center > left) {
                    filters[m - 1][k] = (double) (k - left) / (center - left);
                }
            }
            for (int k = center; k <= right; k++) {
                if (right > center) {
                    filters[m - 1][k] = (double) (right - k) / (right - center);
                }
            }
        }
        return filters;
    }

    private static double[] hammingWindow(int length) {
        double[] window = new double[length];
        for (int i = 0; i < length; i++) {
            window[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (length - 1));
        }
        return window;
    }

    /** 逐维减均值、除标准差；标准差过小时保持原值。 */
    private static void cepstralMeanVarianceNormalization(float[][] matrix) {
        int frames = matrix.length;
        if (frames == 0) {
            return;
        }
        for (int d = 0; d < COEFFICIENTS; d++) {
            double mean = 0;
            for (float[] row : matrix) {
                mean += row[d];
            }
            mean /= frames;

            double variance = 0;
            for (float[] row : matrix) {
                double delta = row[d] - mean;
                variance += delta * delta;
            }
            variance /= frames;
            double std = Math.sqrt(variance);
            if (std < 1e-8) {
                std = 1.0;
            }
            for (float[] row : matrix) {
                row[d] = (float) ((row[d] - mean) / std);
            }
        }
    }

    private static double hzToMel(double hz) {
        return 2595.0 * Math.log10(1.0 + hz / 700.0);
    }

    private static double melToHz(double mel) {
        return 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0);
    }
}
