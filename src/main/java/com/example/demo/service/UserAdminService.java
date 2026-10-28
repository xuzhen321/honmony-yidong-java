package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.BusinessException;
import com.example.demo.mapper.UserMapper;
import com.example.demo.pojo.dto.CreateUserRequest;
import com.example.demo.pojo.entity.User;
import com.example.demo.pojo.vo.PageResult;
import com.example.demo.pojo.vo.UserListItemVO;
import com.example.demo.security.TokenStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 管理员用户管理：分页查询、冻结、恢复。
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    /** 管理员角色值，和 user.role 字段保持一致 */
    private static final String ROLE_ADMIN = "ADMIN";

    private final UserMapper userMapper;
    private final TokenStore tokenStore;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 管理员创建账号。只有 ADMIN 可以调用。
     *
     * <p>用户名唯一：已冻结账号也占用 user_name 唯一键，所以重名时提示管理员先恢复或换名。
     * 创建后不自动登录，也不在这里创建声纹档案；用户首次登录看到
     * {@code LoginResult.needVoiceprintEnroll=true} 后去录声纹。</p>
     */
    public void createUser(Long operatorId, CreateUserRequest request) {
        this.requireAdmin(operatorId);

        String userName = request.getUserName().trim();
        User existing = userMapper.selectByUserNameIgnoreLogicDelete(userName);
        if (existing != null) {
            if (Integer.valueOf(1).equals(existing.getDeleted())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "用户名已存在且已被冻结，请先恢复账号或换一个用户名");
            }
            throw new BusinessException(HttpStatus.CONFLICT, "用户名已存在");
        }

        String role = request.getRole() == null || request.getRole().isBlank()
                ? "USER" : request.getRole().trim().toUpperCase();
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "role 只能是 USER 或 ADMIN");
        }
        String nickName = request.getNickName() == null || request.getNickName().isBlank()
                ? "新用户" : request.getNickName().trim();

        User user = new User();
        user.setUserName(userName);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickName(nickName);
        user.setRole(role);
        user.setDeleted(0);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发创建同名账号时由 uk_user_user_name 兜底
            throw new BusinessException(HttpStatus.CONFLICT, "用户名已存在");
        }

        log.info("管理员 {} 创建账号 {}（{}，角色 {}）",
                operatorId, user.getId(), userName, role);
    }

    /**
     * 分页条件查询。
     *
     * @param userName 用户名，模糊匹配；为空时不过滤
     * @param deleted  0 正常，1 已冻结；传 null 返回全部
     */
    public PageResult<UserListItemVO> pageUsers(Long operatorId, String userName,
                                                Integer deleted, long pageNum, long pageSize) {
        this.requireAdmin(operatorId);

        if (deleted != null && deleted != 0 && deleted != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "deleted 只能是 0 或 1");
        }
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }

        String keyword = (userName == null || userName.isBlank()) ? null : userName.trim();
        Page<UserListItemVO> page = new Page<>(pageNum, pageSize);
        IPage<UserListItemVO> result = userMapper.selectUserPage(page, keyword, deleted);
        return new PageResult<>(result.getCurrent(), result.getSize(),
                result.getTotal(), result.getPages(), result.getRecords());
    }

    /** 冻结用户：逻辑删除。已经是冻结状态时幂等返回成功 */
    public void freezeUser(Long operatorId, Long targetUserId) {
        this.requireAdmin(operatorId);
        if (operatorId.equals(targetUserId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不能冻结自己");
        }

        User active = userMapper.selectById(targetUserId);
        if (active == null) {
            User any = userMapper.selectByIdIgnoreLogicDelete(targetUserId);
            if (any == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
            }
            // 已经是冻结状态，幂等处理
            tokenStore.removeByUserId(targetUserId);
            return;
        }

        userMapper.deleteById(targetUserId);
        tokenStore.removeByUserId(targetUserId);
        log.info("管理员 {} 冻结了用户 {}", operatorId, targetUserId);
    }

    /** 恢复账号：把 deleted 改回 0。已经是正常状态时幂等返回成功 */
    public void restoreUser(Long operatorId, Long targetUserId) {
        this.requireAdmin(operatorId);

        User target = userMapper.selectByIdIgnoreLogicDelete(targetUserId);
        if (target == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (Integer.valueOf(0).equals(target.getDeleted())) {
            return;
        }

        userMapper.restoreById(targetUserId);
        tokenStore.removeByUserId(targetUserId);
        log.info("管理员 {} 恢复了用户 {}", operatorId, targetUserId);
    }

    /** 管理员权限从数据库实时查询，不信任 token 里的旧角色 */
    private void requireAdmin(Long operatorId) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !ROLE_ADMIN.equals(operator.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "没有访问权限");
        }
    }
}
