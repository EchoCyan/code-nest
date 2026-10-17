package com.echocyan.codenest.notification.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.api.event.CommentCreatedEvent;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.interaction.api.event.LikeCreatedEvent;
import com.echocyan.codenest.notification.NotificationErrorCode;
import com.echocyan.codenest.notification.entity.Notification;
import com.echocyan.codenest.notification.vo.NotificationVO;
import com.echocyan.codenest.social.api.event.FollowCreatedEvent;

/**
 * 站内通知业务服务接口。
 * <p>
 * 提供领域事件触发的通知写入、游标分页查询、未读计数统计及已读状态变更等能力。
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 未读通知统计上限（超出时前端通常显示为 "99+"）。
     */
    int MAX_UNREAD_COUNT = 100;

    /**
     * 处理点赞事件并生成点赞通知。
     * <p>
     * 通知文章作者；排除给自己的文章点赞；通过去重键保证同一用户对同一文章仅通知一次。
     *
     * @param event 点赞创建事件
     */
    void notifyLike(LikeCreatedEvent event);

    /**
     * 处理评论与回复事件并生成对应通知。
     * <p>
     * 顶层评论通知文章作者，二级回复通知被回复者；排除自评与自回复；评论与回复均不去重。
     *
     * @param event 评论或回复创建事件
     */
    void notifyComment(CommentCreatedEvent event);

    /**
     * 处理关注事件并生成关注通知。
     * <p>
     * 通知被关注人；排除自己关注自己；通过去重键保证同一用户对同一作者仅通知一次。
     *
     * @param event 关注创建事件
     */
    void notifyFollow(FollowCreatedEvent event);

    /**
     * 游标分页查询当前用户的通知列表。
     * <p>
     * 按通知 ID 倒序排列，在读取时跨模块聚合触发者信息与内容摘要。
     *
     * @param recipientId 接收人用户 ID
     * @param cursor      上一页最后一条记录的通知 ID（首次查询传入 {@code null}）
     * @param size        每页期望返回数量
     * @return 包含当前页数据及下一页游标的分页结果
     */
    CursorResult<NotificationVO> listMine(long recipientId, Long cursor, int size);

    /**
     * 查询指定接收人的未读通知数量。
     *
     * @param recipientId 接收人用户 ID
     * @return 未读通知数（最多统计至 {@value #MAX_UNREAD_COUNT}）
     */
    int countUnread(long recipientId);

    /**
     * 将指定通知标记为已读。
     *
     * @param recipientId    接收人用户 ID（必须为通知所属用户）
     * @param notificationId 通知 ID
     * @throws BizException 若通知不存在或不属于当前用户，抛出 {@link NotificationErrorCode#NOTIFICATION_NOT_FOUND}
     */
    void markRead(long recipientId, long notificationId);

    /**
     * 将指定接收人的全部未读通知批量标记为已读。
     *
     * @param recipientId 接收人用户 ID
     */
    void markAllRead(long recipientId);
}
