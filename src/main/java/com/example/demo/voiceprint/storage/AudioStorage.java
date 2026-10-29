package com.example.demo.voiceprint.storage;

import com.example.demo.common.BusinessException;
import com.example.demo.config.VoiceprintProperties;
import com.example.demo.voiceprint.crypto.TemplateCipher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 注册音频落盘。
 *
 * <p>音频属于生物识别数据，整文件 AES-GCM 加密后写 {@code .wav.enc}；
 * 文件名由服务端 UUID 生成，绝不使用客户端上传的文件名；
 * 音频根目录不映射成静态资源，不能通过 URL 直接下载。</p>
 */
@Component
@RequiredArgsConstructor
public class AudioStorage {

    private static final Logger log = LoggerFactory.getLogger(AudioStorage.class);

    private final VoiceprintProperties properties;
    private final TemplateCipher cipher;

    /**
     * 保存注册音频，返回相对音频根目录的路径（统一用 {@code /}）。
     *
     * <p>先写同目录临时文件再原子移动，避免半截文件被当成有效音频。</p>
     */
    public String storeEnroll(long userId, String phraseTag, byte[] wavBytes) {
        String relative = "enroll/" + userId + "/" + phraseTag + "/"
                + UUID.randomUUID().toString().replace("-", "") + ".wav.enc";
        Path target = resolveSafely(relative);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.write(temp, cipher.encrypt(wavBytes));
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return relative;
        } catch (IOException e) {
            deleteQuietly(temp);
            deleteQuietly(target);
            log.error("保存注册音频失败: {}", relative, e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "录音保存失败，请稍后重试");
        }
    }

    /** 删除单个相对路径对应的文件；文件不存在也视为成功（幂等）。 */
    public void deleteByRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        deleteQuietly(resolveSafely(relativePath));
    }

    /**
     * 删除某用户的整个注册音频目录 {@code enroll/{userId}}。
     *
     * <p>先 normalize 再校验 startsWith，防止路径穿越删到根目录外面。</p>
     */
    public void deleteEnrollDir(long userId) {
        Path enrollRoot = resolveSafely("enroll");
        Path userDir = enrollRoot.resolve(String.valueOf(userId)).normalize();
        if (!userDir.startsWith(enrollRoot) || userDir.equals(enrollRoot)) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "音频路径不合法");
        }
        if (!Files.exists(userDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(userDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (IOException e) {
            log.warn("删除注册音频目录失败（数据库已重置，文件可稍后清理）: {}", userDir, e);
        }
    }

    private Path resolveSafely(String relativePath) {
        Path root = Path.of(properties.getAudioRoot()).toAbsolutePath().normalize();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "音频路径不合法");
        }
        return resolved;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除文件失败: {}", path, e);
        }
    }
}
