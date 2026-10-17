package com.echocyan.codenest.social.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 用户关注成功领域事件。
 * <p>
 * 当关注关系成功写入数据库后触发发布（取关后重新关注会再次触发）。
 *
 * @param followerId 发起关注的用户 ID
 * @param authorId   被关注的作者用户 ID
 */
@DomainEvent("follow.created")
public record FollowCreatedEvent(long followerId, long authorId) {
}
