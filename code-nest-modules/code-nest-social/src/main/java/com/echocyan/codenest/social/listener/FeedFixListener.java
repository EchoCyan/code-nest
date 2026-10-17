package com.echocyan.codenest.social.listener;

import com.echocyan.codenest.article.api.event.ArticleDeletedEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import com.echocyan.codenest.social.api.event.FollowCreatedEvent;
import com.echocyan.codenest.social.api.event.FollowDeletedEvent;
import com.echocyan.codenest.social.service.FeedStore;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Feed 动态流变更修正消息监听器。
 * <p>
 * 监听 {@value #QUEUE} 队列，消费关注、取关以及文章删除等事件，异步修正对应读者的收件箱与作者的发件箱。
 * 各项修正操作基于 Redis ZSet 原语具备天然幂等性，因此无需使用 {@link com.echocyan.codenest.framework.mq.IdempotentConsumer}。
 */
@Component
@RabbitListener(queues = FeedFixListener.QUEUE)
@RequiredArgsConstructor
public class FeedFixListener {

    /**
     * Feed 动态流修正消费队列名称。
     */
    static final String QUEUE = "social.feed-fix";

    private final FeedStore feedStore;

    /**
     * 声明 Feed 修正队列及其与领域事件交换机的绑定关系。
     *
     * @return 队列与绑定的声明集合
     */
    @Bean
    static Declarables feedFixQueue() {
        return EventQueues.declare(QUEUE, FeedFixListener.class);
    }

    /**
     * 消费关注事件：更新作者粉丝数并将普通作者历史发件箱并入读者收件箱。
     *
     * @param event 关注创建事件
     */
    @RabbitHandler
    public void onFollow(FollowCreatedEvent event) {
        feedStore.follow(event.followerId(), event.authorId());
    }

    /**
     * 消费取关事件：更新作者粉丝数并从读者收件箱中移除该作者的历史文章。
     *
     * @param event 取关事件
     */
    @RabbitHandler
    public void onUnfollow(FollowDeletedEvent event) {
        feedStore.unfollow(event.followerId(), event.authorId());
    }

    /**
     * 消费文章删除事件：从作者发件箱中移除已删除文章。
     *
     * @param event 文章删除事件
     */
    @RabbitHandler
    public void onArticleDeleted(ArticleDeletedEvent event) {
        feedStore.removeArticle(event.articleId(), event.authorId());
    }
}
