package com.example.demo.voiceprint.crypto;

import com.example.demo.common.BusinessException;
import com.example.demo.config.VoiceprintProperties;
import com.example.demo.voiceprint.audio.MfccExtractor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 模板与音频文件加密（AES-256-GCM）。
 *
 * <p>密文格式：{@code nonce(12) || ciphertext || tag(16)}，每次加密随机 nonce。
 * 密钥来自 {@code app.voiceprint.aes-key}（Base64 编码的 32 字节），
 * 未配置或长度不对时启动直接失败，避免“裸奔”运行。</p>
 */
@Component
public class TemplateCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = 16;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    public TemplateCipher(VoiceprintProperties properties) {
        String encoded = properties.getAesKey();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "未配置 app.voiceprint.aes-key，请设置环境变量 VOICEPRINT_AES_KEY（Base64 的 32 字节）");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.voiceprint.aes-key 不是合法的 Base64 字符串", e);
        }
        if (raw.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "app.voiceprint.aes-key 必须是 32 字节（AES-256），当前为 " + raw.length + " 字节");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** 加密任意字节，返回 nonce + 密文 + tag。 */
    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);

            ByteBuffer buffer = ByteBuffer.allocate(NONCE_LENGTH + ciphertext.length);
            buffer.put(nonce);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "模板加密失败");
        }
    }

    /** 解密 {@link #encrypt(byte[])} 的产物；密文被篡改会抛异常。 */
    public byte[] decrypt(byte[] payload) {
        if (payload == null || payload.length < NONCE_LENGTH + TAG_LENGTH_BYTES) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "模板数据损坏");
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            System.arraycopy(payload, 0, nonce, 0, NONCE_LENGTH);
            byte[] ciphertext = new byte[payload.length - NONCE_LENGTH];
            System.arraycopy(payload, NONCE_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "模板解密失败");
        }
    }

    /** MFCC 矩阵序列化成 float32 小端字节；同样的矩阵得到同样的字节。 */
    public static byte[] encodeMatrix(float[][] matrix) {
        int frames = matrix.length;
        ByteBuffer buffer = ByteBuffer.allocate(frames * MfccExtractor.COEFFICIENTS * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float[] row : matrix) {
            for (int d = 0; d < MfccExtractor.COEFFICIENTS; d++) {
                buffer.putFloat(d < row.length ? row[d] : 0f);
            }
        }
        return buffer.array();
    }

    /** 把 {@link #encodeMatrix(float[][])} 的字节还原成矩阵。 */
    public static float[][] decodeMatrix(byte[] plaintext, int frames) {
        float[][] matrix = new float[frames][MfccExtractor.COEFFICIENTS];
        ByteBuffer buffer = ByteBuffer.wrap(plaintext).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            for (int d = 0; d < MfccExtractor.COEFFICIENTS; d++) {
                matrix[i][d] = buffer.getFloat();
            }
        }
        return matrix;
    }

    /** SHA-256 十六进制小写。 */
    public static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", e);
        }
    }
}
