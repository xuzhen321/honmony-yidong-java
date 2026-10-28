package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.pojo.entity.User;
import com.example.demo.pojo.vo.UserListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 管理员分页查询。
     *
     * <p>这是自定义 SQL，MyBatis-Plus 不会自动追加 {@code deleted = 0}，
     * 所以 deleted 条件由调用方决定；传 null 时会把已冻结用户也查出来。</p>
     */
    @Select("""
            <script>
            SELECT id, user_name, nick_name, role, deleted
            FROM `user`
            <where>
                <if test="userName != null and userName != ''">
                    AND user_name LIKE CONCAT(#{userName}, '%')
                </if>
                <if test="deleted != null">
                    AND deleted = #{deleted}
                </if>
            </where>
            ORDER BY id DESC
            </script>
            """)
    @ResultType(UserListItemVO.class)
    IPage<UserListItemVO> selectUserPage(IPage<UserListItemVO> page,
                                         @Param("userName") String userName,
                                         @Param("deleted") Integer deleted);

    /**
     * 忽略逻辑删除查询用户，用于区分“用户不存在”和“用户已冻结”。
     * 只查 id / deleted，不把密码带出来。
     */
    @Select("SELECT id, deleted FROM `user` WHERE id = #{id}")
    User selectByIdIgnoreLogicDelete(@Param("id") Long id);

    /**
     * 忽略逻辑删除按用户名查询，用于创建账号时的重名校验。
     * 已冻结的账号也占用 user_name 唯一键，必须能查出来。
     */
    @Select("""
            SELECT id, user_name, nick_name, role, deleted
            FROM `user`
            WHERE user_name = #{userName}
            LIMIT 1
            """)
    User selectByUserNameIgnoreLogicDelete(@Param("userName") String userName);

    /**
     * 恢复账号：把 deleted 从 1 改回 0。
     * 这是自定义 SQL，不会触发 @TableLogic 自动追加的 deleted = 0 条件。
     */
    @Update("UPDATE `user` SET deleted = 0 WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") Long id);
}
