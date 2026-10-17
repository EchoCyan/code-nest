package com.echocyan.codenest.user.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.user.api.UserErrorCode;
import com.echocyan.codenest.user.dto.UpdateProfileRequest;
import com.echocyan.codenest.user.entity.User;
import com.echocyan.codenest.user.vo.UserProfileVO;

/**
 * 用户核心业务服务接口。
 * <p>
 * 提供用户注册、身份认证凭证校验、用户资料与统计计数查询、资料修改等服务。
 */
public interface UserService extends IService<User> {

    /**
     * 注册新用户。
     * <p>
     * 初始昵称默认等于用户名。用户名唯一性由底层数据库唯一索引保障，并发冲突时抛出业务异常。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 注册成功后的用户实体
     * @throws BizException 当用户名已被占用时抛出 {@link UserErrorCode#USERNAME_TAKEN}
     */
    User register(String username, String password);

    /**
     * 校验用户登录凭证。
     * <p>
     * 用户不存在或密码不匹配时均统一抛出错误，避免对外暴露用户名是否已存在。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 认证成功后的用户实体
     * @throws BizException 当用户名不存在或密码错误时抛出 {@link UserErrorCode#BAD_CREDENTIALS}
     */
    User authenticate(String username, String password);

    /**
     * 查询用户主页资料与统计计数。
     *
     * @param id 用户 ID
     * @return 用户资料与粉丝、关注、文章、获赞统计计数
     * @throws BizException 当指定用户不存在时抛出 {@link UserErrorCode#USER_NOT_FOUND}
     */
    UserProfileVO getProfile(long id);

    /**
     * 整体替换修改用户的个人资料。
     * <p>
     * 对昵称、头像、简介执行全量覆盖更新，未传递字段置空写入，并同步失效二级缓存。
     *
     * @param userId  当前操作用户 ID
     * @param request 资料更新请求
     */
    void updateProfile(long userId, UpdateProfileRequest request);
}
