package com.example.demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 文档配置。
 *
 * <p>启动后访问：</p>
 * <ul>
 *   <li>Swagger UI：{@code http://localhost:8080/swagger-ui.html}</li>
 *   <li>OpenAPI JSON：{@code http://localhost:8080/v3/api-docs}</li>
 * </ul>
 *
 * <p>需要登录的接口在 Swagger UI 右上角点 <b>Authorize</b>，填入登录接口返回的 token 即可调试。</p>
 */
@Configuration
public class OpenApiConfig {

    /** 与 Controller 上 @SecurityRequirement(name = "...") 保持一致 */
    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI loginOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("移动应用开发 · 登录模块 API")
                        .description("登录、获取当前用户、退出登录。\n\n"
                                + "统一响应体 code：1 = 成功，0 = 失败；msg 只在失败时有内容。\n"
                                + "鉴权：先调用 /user/login 获取 token，再点右上角 Authorize 填入 token。")
                        .version("0.0.1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("Token")
                                .in(SecurityScheme.In.HEADER)));
    }
}
