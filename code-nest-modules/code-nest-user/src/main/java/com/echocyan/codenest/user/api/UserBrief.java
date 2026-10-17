package com.echocyan.codenest.user.api;

/**
 * 用户基础简要信息。
 * <p>
 * 供文章、评论、社交与通知等跨模块展示用户基本身份信息。
 *
 * @param id        用户 ID
 * @param nickname  用户昵称
 * @param avatarUrl 头像链接
 */
public record UserBrief(Long id, String nickname, String avatarUrl) {
}

