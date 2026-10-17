package com.echocyan.codenest.framework.auth;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 用户认证与登录上下文门面工具。
 *
 * <p>基于 Sa-Token 封装，供 Controller 层获取或操作当前会话用户身份（Service 层禁止直接调用，需由入参显式传入）。</p>
 */
public final class AuthContext {

    private AuthContext() {
    }

    /**
     * 为指定用户在当前设备建立登录会话并签发认证凭证。
     *
     * @param userId 待登录用户的唯一标识 ID（不可包含冒号等特殊字符）
     * @return 新签发的 Token 凭证字符串；客户端后续需在请求头中以 {@code Authorization: Bearer <token>} 格式携带
     */
    public static String login(long userId) {
        StpUtil.login(userId);
        return StpUtil.getTokenValue();
    }

    /**
     * 注销当前设备的登录会话。
     *
     * <p>仅使当前请求关联设备的 Token 失效，同一账号在其他终端上持有的 Token 与会话保持有效。
     */
    public static void logout() {
        StpUtil.logout();
    }

    /**
     * 获取当前登录用户的唯一标识 ID。
     *
     * <p>专用于受保护的认证接口（即未经 {@link cn.dev33.satoken.annotation.SaIgnore} 豁免的端点）。
     * 调用方在进入 Controller 方法时已默认通过拦截器的登录检查。
     *
     * @return 当前登录用户的 ID
     * @throws cn.dev33.satoken.exception.NotLoginException 若当前请求未携带有效 Token 或凭证已过期失效
     */
    public static long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 获取当前登录用户的唯一标识 ID；若未登录则返回 {@code null}。
     *
     * <p>专供标注了 {@link cn.dev33.satoken.annotation.SaIgnore} 的公开接口（例如文章详情页、热榜等）使用，
     * 用于识别可选的访客身份（如判断是否已对当前文章点赞或关注作者）。
     * 当请求头未携带 Token 或 Token 无效、过期时，均按匿名用户安全降级处理，不抛出异常。
     *
     * @return 当前登录用户的 ID；若当前请求为匿名访问或凭证无效则返回 {@code null}
     */
    public static Long currentUserIdOrNull() {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
    }
}
