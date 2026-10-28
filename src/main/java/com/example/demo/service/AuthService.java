package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.mapper.UserMapper;
import com.example.demo.pojo.dto.AdminResetPasswordRequest;
import com.example.demo.pojo.dto.ChangePasswordRequest;
import com.example.demo.pojo.dto.LoginRequest;
import com.example.demo.pojo.dto.UpdateNicknameRequest;
import com.example.demo.pojo.entity.User;
import com.example.demo.pojo.vo.LoginResult;
import com.example.demo.security.TokenStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户认证相关的业务逻辑：登录、当前用户、修改密码。
 * Controller 只负责收参数、返结果，真正的判断都在这里。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 管理员角色值，和 user.role 字段保持一致 */
    private static final String ROLE_ADMIN = "ADMIN";

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
        return new LoginResult(token, user.getId(), user.getUserName(), user.getNickName(), user.getRole());
    }

    /** 根据拦截器放进 request 的 userId 查用户信息；token 由 Controller 从当前请求里补上 */
    public LoginResult getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户不存在，请重新登录");
        }
        return new LoginResult(null, user.getId(), user.getUserName(), user.getNickName(), user.getRole());
    }

    /**
     * 修改自己的昵称。
     *
     * <p>只更新 nick_name，不会使 token 失效，修改后不需要重新登录。</p>
     */
    public void updateNickName(Long userId, UpdateNicknameRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户不存在，请重新登录");
        }

        String nickName = request.getNickName().trim();
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getNickName, nickName));
        log.info("用户 {} 修改了自己的昵称", userId);
    }

    /**
     * 修改自己的密码。
     *
     * <p>必须验证原密码；修改成功后该用户所有 token 立即失效，需要重新登录。</p>
     */
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户不存在，请重新登录");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "原密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "新密码不能与原密码相同");
        }

        updatePassword(userId, request.getNewPassword());
        // 密码变了，旧 token 不能继续用；当前这根也一并踢掉，客户端需要重新登录
        tokenStore.removeByUserId(userId);
        log.info("用户 {} 修改了自己的密码", userId);
    }

    /**
     * 管理员重置他人密码，包括其他管理员。
     *
     * <p>管理员权限从数据库实时查询，不信任 token 里的旧角色；成功后被重置用户的所有 token 失效。</p>
     */
    public void resetPassword(Long operatorId, Long targetUserId, AdminResetPasswordRequest request) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !ROLE_ADMIN.equals(operator.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "没有访问权限");
        }
        /*if (operatorId.equals(targetUserId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "修改自己的密码请使用 /user/password");
        }*/

        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (passwordEncoder.matches(request.getNewPassword(), target.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "新密码不能与原密码相同");
        }

        updatePassword(targetUserId, request.getNewPassword());
        tokenStore.removeByUserId(targetUserId);
        log.info("管理员 {} 重置了用户 {} 的密码", operatorId, targetUserId);
    }

    /** 只更新 password 列，避免把整行其它字段一起覆盖 */
    private void updatePassword(Long userId, String newPassword) {
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPassword, passwordEncoder.encode(newPassword)));
        if (updated == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }
    }
}
