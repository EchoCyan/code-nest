package com.echocyan.codenest.social.listener;

import com.echocyan.codenest.article.api.event.ArticlePublishedEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import com.echocyan.codenest.social.service.FeedStore;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Feed 动态流发文推送消息监听器。
 * <p>
 * 监听 {@value #QUEUE} 队列，消费文章发布事件，将文章写入作者发件箱并在作者为普通作者时推送到活跃粉丝收件箱。
 * 推送基于 Redis ZSet 幂等覆盖，因此无需添加 {@link com.echocyan.codenest.framework.mq.IdempotentConsumer}。
 */
@Component
@RequiredArgsConstructor
public class FeedPushListener {

    /**
     * 发文推送消费队列名称。
     */
    static final String QUEUE = "social.feed-push";

    private final FeedStore feedStore;

    /**
     * 声明发文推送队列及其与领域事件交换机的绑定关系。
     *
     * @return 队列与绑定的声明集合
     */
    @Bean
    static Declarables feedPushQueue() {
        return EventQueues.declare(QUEUE, FeedPushListener.class);
    }

    /**
     * 消费文章发布事件并执行发件箱入库及粉丝推送。
     *
     * @param event 文章发布事件
     */
    @RabbitListener(queues = QUEUE)
    public void onPublished(ArticlePublishedEvent event) {
        feedStore.addArticle(event.articleId(), event.authorId());
    }
}
