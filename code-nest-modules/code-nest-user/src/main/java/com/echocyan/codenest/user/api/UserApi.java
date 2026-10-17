package com.echocyan.codenest.user.api;

import java.util.Collection;
import java.util.Map;

/**
 * 用户模块对外部模块的门面服务接口。
 * <p>
 * 提供跨模块调用的用户基础信息批量读取与存在性校验能力。
 */
public interface UserApi {

    /**
     * 批量查询用户简要信息。
     * <p>
     * 优先走两级缓存读取，用户修改资料后自动失效。
     *
     * @param userIds 用户 ID 集合
     * @return 用户 ID 到简要信息的映射；不存在的用户不包含在返回 Map 中
     */
    Map<Long, UserBrief> getBriefs(Collection<Long> userIds);

    /**
     * 校验指定用户是否存在。
     *
     * @param userId 用户 ID
     * @return {@code true} 表示用户存在，{@code false} 表示不存在
     */
    boolean exists(long userId);
}

