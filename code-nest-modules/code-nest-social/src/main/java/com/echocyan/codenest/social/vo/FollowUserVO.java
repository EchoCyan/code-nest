package com.echocyan.codenest.social.vo;

import com.echocyan.codenest.user.api.UserBrief;

import java.time.LocalDateTime;

/**
 * 粉丝列表或关注列表的用户项视图对象。
 *
 * @param user       目标用户的简要基本资料
 * @param followedAt 建立关注关系的时间
 */
public record FollowUserVO(UserBrief user, LocalDateTime followedAt) {
}
