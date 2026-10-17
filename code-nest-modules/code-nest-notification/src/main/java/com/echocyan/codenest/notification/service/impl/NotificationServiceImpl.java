package com.echocyan.codenest.notification.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.CommentBrief;
import com.echocyan.codenest.article.api.event.CommentCreatedEvent;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.interaction.api.event.LikeCreatedEvent;
import com.echocyan.codenest.notification.NotificationErrorCode;
import com.echocyan.codenest.notification.entity.Notification;
import com.echocyan.codenest.notification.entity.NotificationType;
import com.echocyan.codenest.notification.mapper.NotificationMapper;
import com.echocyan.codenest.notification.service.NotificationService;
import com.echocyan.codenest.notification.vo.NotificationVO;
import com.echocyan.codenest.social.api.event.FollowCreatedEvent;
import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 站内通知业务服务实现类。
 * <p>
 * 负责消费并处理各类通知事件、执行先查后写的幂等与防重入库、并在列表查询时批量聚合跨模块实体数据。
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    /**
     * 关联内容（文章、评论或回复）已被删除时的占位提示文本。
     */
    private static final String DELETED_CONTENT = "该内容已删除";

    private final UserApi userApi;
    private final ArticleApi articleApi;

    /**
     * 将通知实体与批量查出的触发者、文章及评论数据拼装为 {@link NotificationVO}。
     * <p>
     * 当所属文章已删除时，文章标题及其下属评论摘要均标记为已删除。
     *
     * @param notification 通知实体
     * @param actors       动作触发人信息映射表（userId -> UserBrief）
     * @param articles     关联文章信息映射表（articleId -> ArticleBrief）
     * @param comments     关联评论信息映射表（commentId -> CommentBrief）
     * @return 拼装后的通知视图对象
     */
    private static NotificationVO toVO(Notification notification,
                                       Map<Long, UserBrief> actors,
                                       Map<Long, ArticleBrief> articles,
                                       Map<Long, CommentBrief> comments) {
        ArticleBrief article = null;
        String articleTitle = null;
        if (notification.getArticleId() != null) {
            article = articles.get(notification.getArticleId());
            articleTitle = article == null ? DELETED_CONTENT : article.title();
        }
        String commentSummary = null;
        if (notification.getCommentId() != null) {
            CommentBrief comment = comments.get(notification.getCommentId());
            commentSummary = article == null || comment == null ? DELETED_CONTENT : comment.summary();
        }
        return new NotificationVO(notification.getId(), notification.getType(), actors.get(notification.getActorId()),
                notification.getArticleId(), articleTitle, notification.getCommentId(), commentSummary,
                notification.getIsRead(), notification.getCreatedAt());
    }

    /**
     * 提取本页通知列表中指定列的非空 ID 集合并去重。
     *
     * @param page   通知实体分页列表
     * @param column 提取 ID 的函数引用
     * @return 去重后的非空 ID 列表
     */
    private static List<Long> ids(List<Notification> page, Function<Notification, Long> column) {
        return page.stream().map(column).filter(Objects::nonNull).distinct().toList();
    }

    @Override
    public void notifyLike(LikeCreatedEvent event) {
        Notification notification = new Notification();
        notification.setRecipientId(event.authorId());
        notification.setActorId(event.userId());
        notification.setType(NotificationType.LIKE);
        notification.setArticleId(event.articleId());
        notification.setDedupKey("L:" + event.userId() + ":" + event.articleId());
        send(notification);
    }

    @Override
    public void notifyComment(CommentCreatedEvent event) {
        boolean reply = event.rootId() != 0;
        Notification notification = new Notification();
        notification.setRecipientId(reply ? event.replyToUserId() : event.articleAuthorId());
        notification.setActorId(event.userId());
        notification.setType(reply ? NotificationType.REPLY : NotificationType.COMMENT);
        notification.setArticleId(event.articleId());
        notification.setCommentId(event.commentId());
        send(notification);
    }

    @Override
    public void notifyFollow(FollowCreatedEvent event) {
        Notification notification = new Notification();
        notification.setRecipientId(event.authorId());
        notification.setActorId(event.followerId());
        notification.setType(NotificationType.FOLLOW);
        notification.setDedupKey("F:" + event.followerId() + ":" + event.authorId());
        send(notification);
    }

    /**
     * 执行通知入库写入。
     * <p>
     * 包含如下逻辑：
     * <ul>
     *   <li><b>过滤自产自销：</b>触发人与接收人相同时直接跳过。</li>
     *   <li><b>先查后写防争锁：</b>针对存在去重键（{@link Notification#getDedupKey()}）的高频反复操作（如频繁点赞/取关），
     *       先以非锁定普通查询 {@code exists()} 校验。若已存在则直接返回，避免频繁唯一索引冲突导致 InnoDB 加持有至事务结束的共享锁（S 锁）引发并发等待与死锁风险。</li>
     *   <li><b>唯一索引兜底：</b>并发首次写入由底层唯一索引保证绝对去重，捕获 {@link DuplicateKeyException} 并静默忽略。</li>
     * </ul>
     *
     * @param notification 待持久化的通知实体
     */
    private void send(Notification notification) {
        if (notification.getActorId().equals(notification.getRecipientId())) {
            return;
        }
        if (notification.getDedupKey() != null && lambdaQuery().eq(Notification::getDedupKey,
                notification.getDedupKey()).exists()) {
            return;
        }
        notification.setIsRead(false);
        try {
            save(notification);
        } catch (DuplicateKeyException e) {
            // 同一 dedupKey 已存在时唯一键冲突跳过，底层事务继续执行
        }
    }

    @Override
    public CursorResult<NotificationVO> listMine(long recipientId, Long cursor, int size) {
        CursorResult<Notification> page = CursorResult.ofOverfetched(
                lambdaQuery().eq(Notification::getRecipientId, recipientId)
                        .lt(cursor != null, Notification::getId, cursor)
                        .orderByDesc(Notification::getId)
                        .last("LIMIT " + (size + 1))
                        .list(), size, Notification::getId);
        if (page.list().isEmpty()) {
            return CursorResult.empty();
        }
        Map<Long, UserBrief> actors = userApi.getBriefs(ids(page.list(), Notification::getActorId));
        Map<Long, ArticleBrief> articles = articleApi.getBriefs(ids(page.list(), Notification::getArticleId));
        Map<Long, CommentBrief> comments = articleApi.getCommentBriefs(ids(page.list(), Notification::getCommentId));
        return page.map(notification -> toVO(notification, actors, articles, comments));
    }

    @Override
    public int countUnread(long recipientId) {
        return baseMapper.countUnread(recipientId, MAX_UNREAD_COUNT);
    }

    @Override
    public void markRead(long recipientId, long notificationId) {
        // 由于配置了 AuditableEntity 自动填充，updated_at 每次均会刷新，故对已读记录重复标记仍能命中并更新 1 行
        boolean updated = lambdaUpdate().set(Notification::getIsRead, true)
                .eq(Notification::getId, notificationId)
                .eq(Notification::getRecipientId, recipientId)
                .update(new Notification());
        if (!updated) {
            throw new BizException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    @Override
    public void markAllRead(long recipientId) {
        lambdaUpdate().set(Notification::getIsRead, true)
                .eq(Notification::getRecipientId, recipientId)
                .eq(Notification::getIsRead, false)
                .update(new Notification());
    }
}
