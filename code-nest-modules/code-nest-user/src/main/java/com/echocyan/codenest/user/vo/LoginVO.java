package com.echocyan.codenest.user.vo;

/**
 * 用户登录认证成功响应视图对象。
 * <p>
 * 客户端在后续受保护请求中，需通过请求头 {@code Authorization: Bearer <token>} 携带鉴权凭证。
 *
 * @param userId 用户 ID
 * @param token  登录会话令牌（Token）
 */
public record LoginVO(Long userId, String token) {
}

