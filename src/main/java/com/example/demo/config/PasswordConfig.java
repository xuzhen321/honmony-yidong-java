package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密器配置。
 *
 * <p>单独声明成 Bean，由 AuthService 通过构造器注入使用，
 * 避免在业务代码里直接 {@code new BCryptPasswordEncoder()}。</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
