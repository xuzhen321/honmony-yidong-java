package com.example.demo.voiceprint.audio;

import java.util.Arrays;

/**
 * DTW（动态时间规整）距离与相似度。
 *
 * <p>代价为 13 维欧氏距离；带 Sakoe-Chiba 带宽 {@code 0.2 * max(n, m)}，
 * 超出带宽的路径直接视为不可达；归一化距离 = 累积距离 / 路径长度，
 * 相似度 = {@code exp(-6.0 * distNorm)}。</p>
 */
public final class DtwMatcher {

    private static final double BAND_RATIO = 0.2;
    private static final double SIMILARITY_DECAY = 6.0;

    private DtwMatcher() {
    }

    /**
     * 两个 MFCC 矩阵的相似度，取值 0~1，越大越像。
     *
     * <p>完全相同的序列返回 1；无法在带宽内对齐时返回 0。</p>
     */
    public static double similarity(float[][] first, float[][] second) {
        if (first == null || second == null || first.length == 0 || second.length == 0) {
            return 0;
        }
        double distance = normalizedDistance(first, second);
        if (!Double.isFinite(distance)) {
            return 0;
        }
        return Math.min(1.0, Math.exp(-SIMILARITY_DECAY * distance));
    }

    /**
     * 带宽约束下的归一化 DTW 距离；路径不可达时返回 {@link Double#POSITIVE_INFINITY}。
     */
    public static double normalizedDistance(float[][] first, float[][] second) {
        int n = first.length;
        int m = second.length;
        if (n == 0 || m == 0) {
            return Double.POSITIVE_INFINITY;
        }

        int band = Math.max(1, (int) Math.ceil(BAND_RATIO * Math.max(n, m)));

        double infinity = Double.POSITIVE_INFINITY;
        double[] previousCost = new double[m];
        int[] previousLength = new int[m];
        Arrays.fill(previousCost, infinity);

        for (int i = 0; i < n; i++) {
            double[] currentCost = new double[m];
            int[] currentLength = new int[m];
            Arrays.fill(currentCost, infinity);

            int from = Math.max(0, i - band);
            int to = Math.min(m - 1, i + band);

            for (int j = from; j <= to; j++) {
                double stepCost = euclidean(first[i], second[j]);
                if (i == 0 && j == 0) {
                    currentCost[j] = stepCost;
                    currentLength[j] = 1;
                    continue;
                }

                double bestCost = infinity;
                int bestLength = 0;

                if (j > 0 && previousCost[j - 1] < bestCost) {
                    bestCost = previousCost[j - 1];
                    bestLength = previousLength[j - 1];
                }
                if (previousCost[j] < bestCost) {
                    bestCost = previousCost[j];
                    bestLength = previousLength[j];
                }
                if (j > 0 && currentCost[j - 1] < bestCost) {
                    bestCost = currentCost[j - 1];
                    bestLength = currentLength[j - 1];
                }

                if (Double.isFinite(bestCost)) {
                    currentCost[j] = bestCost + stepCost;
                    currentLength[j] = bestLength + 1;
                }
            }

            previousCost = currentCost;
            previousLength = currentLength;
        }

        if (!Double.isFinite(previousCost[m - 1]) || previousLength[m - 1] <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return previousCost[m - 1] / previousLength[m - 1];
    }

    private static double euclidean(float[] first, float[] second) {
        int dimensions = Math.min(first.length, second.length);
        double sum = 0;
        for (int d = 0; d < dimensions; d++) {
            double delta = first[d] - second[d];
            sum += delta * delta;
        }
        return Math.sqrt(sum);
    }
}
