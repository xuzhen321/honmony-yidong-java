package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.mapper.UserMapper;
import com.example.demo.pojo.dto.LoginRequest;
import com.example.demo.pojo.entity.User;
import com.example.demo.pojo.vo.LoginResult;
import com.example.demo.security.TokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录相关的业务逻辑。Controller 只负责收参数、返结果，真正的判断都在这里。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final TokenStore tokenStore;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 登录。
     *
     * <p>注意 "用户不存在" 和 "密码错误" 返回的是同一句提示 —— 这叫防止账号枚举，
     * 不然别人可以拿这个接口试出哪些用户名是真实存在的。</p>
     */
    public LoginResult login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserName, request.getUserName().trim()));

        // 账号不存在和密码错误统一按“认证失败”处理（HTTP 401 + code 0），
        // 且提示语保持一致，避免别人用这个接口枚举出哪些用户名真实存在
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        String token = tokenStore.createSession(user);
        return new LoginResult(token, user.getId(), user.getUserName(), user.getNickName());
    }

    /** 根据拦截器放进 request 的 userId 查用户信息；token 由 Controller 从当前请求里补上 */
    public LoginResult getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户不存在，请重新登录");
        }
        return new LoginResult(null, user.getId(), user.getUserName(), user.getNickName());
    }
}
