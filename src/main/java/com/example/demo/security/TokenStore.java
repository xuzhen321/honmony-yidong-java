package com.example.demo.security;

import com.example.demo.common.BusinessException;
import com.example.demo.pojo.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简 Token 仓库：token -> 会话信息。
 *
 * <p>登录成功发一个随机 token，之后的请求带着它来，服务端查表就知道是谁。
 * 存在内存里，进程重启就全部失效（对课程设计来说完全够用）。</p>
 *
 * <p>想升级成 JWT / Redis 的话，只要保持 {@link #createSession}、{@link #validate}、
 * {@link #remove} 这三个方法签名不变，其它代码都不用动。</p>
 */
@Component
public class TokenStore {

    /** 客户端把 token 放在这个 header 里 */
    public static final String HEADER_NAME = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    /** 一次登录会话：不可变对象，构造后只读 */
    @Data
    @AllArgsConstructor
    public static class Session {

        private final Long userId;
        private final String userName;
        private final Instant expireAt;
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** token 有效期（分钟），配置在 application.yaml 的 app.auth.token-expire-minutes */
    private final long expireMinutes;

    public TokenStore(@Value("${app.auth.token-expire-minutes}") long expireMinutes) {
        this.expireMinutes = expireMinutes;
    }

    /** 登录成功后调用：创建会话并返回 token */
    public String createSession(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expireAt = Instant.now().plus(Duration.ofMinutes(expireMinutes));
        sessions.put(token, new Session(user.getId(), user.getUserName(), expireAt));
        return token;
    }

    /**
     * 校验 token。
     *
     * <p>无效或过期时直接抛 {@link BusinessException}，由全局异常处理器统一返回 401 的提示，
     * 这样拦截器里就不用写 response.setStatus() 那些啰嗦代码了。</p>
     */
    public Session validate(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        Session session = sessions.get(token);
        if (session == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        if (session.getExpireAt().isBefore(Instant.now())) {
            sessions.remove(token);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        return session;
    }

    /** 退出登录：把 token 从表里删掉，立刻失效 */
    public void remove(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /**
     * 从请求里取 token，兼容三种常见写法：
     * <ul>
     *   <li>{@code Authorization: Bearer xxx}（推荐）</li>
     *   <li>{@code Authorization: xxx}</li>
     *   <li>{@code token: xxx}</li>
     * </ul>
     */
    public String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HEADER_NAME);
        if (authorization != null && !authorization.isBlank()) {
            String value = authorization.trim();
            if (value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                return value.substring(BEARER_PREFIX.length()).trim();
            }
            return value;
        }
        return request.getHeader("token");
    }
}
