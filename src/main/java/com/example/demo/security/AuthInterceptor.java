package com.example.demo.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：默认拦截所有请求，先验 token 再放行。
 *
 * <p>白名单（登录、Swagger 文档、/error 等）在 {@link com.example.demo.config.WebConfig} 里 exclude 掉。</p>
 */
@RequiredArgsConstructor
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 校验通过后，把当前用户信息塞进 request，Controller 用 @RequestAttribute 取 */
    public static final String ATTR_USER_ID = "currentUserId";
    public static final String ATTR_USER_NAME = "currentUserName";
    public static final String ATTR_USER_ROLE = "currentUserRole";

    private final TokenStore tokenStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 预检请求不带 token，必须放行，否则浏览器/预览器里的请求全部失败
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }


        TokenStore.Session session = tokenStore.validate(tokenStore.resolveToken(request));

        request.setAttribute(ATTR_USER_ID, session.getUserId());
        request.setAttribute(ATTR_USER_NAME, session.getUserName());
        request.setAttribute(ATTR_USER_ROLE, session.getRole());
        return true;
    }
}
