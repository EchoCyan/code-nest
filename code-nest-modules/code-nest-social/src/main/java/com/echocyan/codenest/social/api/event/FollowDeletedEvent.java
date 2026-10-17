package com.echocyan.codenest.social.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 用户取消关注领域事件。
 * <p>
 * 当关注记录在数据库中被物理删除后触发发布。
 *
 * @param followerId 发起取关的用户 ID
 * @param authorId   被取消关注的作者用户 ID
 */
@DomainEvent("follow.deleted")
public record FollowDeletedEvent(long followerId, long authorId) {
}
