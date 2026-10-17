package com.echocyan.codenest.user.vo;

import java.time.LocalDateTime;

/**
 * 用户个人主页资料展示视图对象。
 *
 * @param id        用户 ID
 * @param username  用户名
 * @param nickname  用户昵称
 * @param avatarUrl 头像链接
 * @param bio       个人简介
 * @param createdAt 注册时间
 * @param counts    多维度统计计数指标
 */
public record UserProfileVO(Long id, String username, String nickname, String avatarUrl, String bio,
                            LocalDateTime createdAt, UserCountsVO counts) {
}

