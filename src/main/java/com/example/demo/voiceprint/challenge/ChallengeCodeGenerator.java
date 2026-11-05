package com.example.demo.voiceprint.challenge;

import java.security.SecureRandom;

/**
 * 4 位数字挑战码生成器。
 *
 * <p>允许重复数字，但相邻两位不能相同：连读两个相同数字时 VAD 无法切分，
 * 用户也不可能在中间停顿出可检测的静音。</p>
 */
public final class ChallengeCodeGenerator {

    public static final int DIGIT_COUNT = 4;

    private ChallengeCodeGenerator() {
    }

    public static String generate(SecureRandom random) {
        StringBuilder builder = new StringBuilder(DIGIT_COUNT);
        int previous = -1;
        for (int i = 0; i < DIGIT_COUNT; i++) {
            int digit;
            do {
                digit = random.nextInt(10);
            } while (digit == previous);
            builder.append(digit);
            previous = digit;
        }
        return builder.toString();
    }
}
