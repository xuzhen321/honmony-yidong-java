package com.example.demo.config;

import com.example.demo.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册登录拦截器 + 开放跨域。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 默认拦截所有请求，只有下面白名单里的接口不需要 token
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 登录接口本身不能要 token，否则永远登不进去
                        "/user/login",
                        // Swagger UI 和 OpenAPI JSON
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        // Spring Boot 的错误转发页，避免原始 500 被覆盖成 401
                        "/error",
                        // 浏览器默认请求的站点图标
                        "/favicon.ico"
                );
    }

    /**
     * 跨域：鸿蒙真机/模拟器发的不是浏览器请求，其实不需要 CORS；
     * 但你在 DevEco 预览器或浏览器里调试接口时会用到，所以开着。
     * 上线时请把 allowedOriginPatterns 收紧成具体域名。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
