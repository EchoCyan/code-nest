package com.echocyan.codenest.article.listener;

import com.echocyan.codenest.article.api.event.ArticleDeletedEvent;
import com.echocyan.codenest.article.api.event.ArticlePublishedEvent;
import com.echocyan.codenest.article.api.event.ArticleUpdatedEvent;
import com.echocyan.codenest.article.service.ArticleCache;
import com.echocyan.codenest.framework.mq.EventQueues;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 文章缓存异步失效监听器。
 * <p>
 * 监听文章发布、更新与删除事件，执行缓存异步淘汰。
 */
@Component
@RabbitListener(queues = ArticleCacheEvictListener.QUEUE)
@RequiredArgsConstructor
public class ArticleCacheEvictListener {

    /**
     * 绑定的 RabbitMQ 消费队列名称。
     */
    static final String QUEUE = "article.cache-evict";

    private final ArticleCache articleCache;

    /**
     * 声明当前监听器所需的队列、死信队列及路由绑定关系。
     */
    @Bean
    static Declarables articleCacheEvictQueue() {
        return EventQueues.declare(QUEUE, ArticleCacheEvictListener.class);
    }

    /**
     * 消费文章发布事件，二次清除文章缓存。
     *
     * @param event 文章发布领域事件
     */
    @RabbitHandler
    public void onPublished(ArticlePublishedEvent event) {
        articleCache.evict(event.articleId());
    }

    /**
     * 消费文章更新事件，二次清除文章缓存。
     *
     * @param event 文章内容更新领域事件
     */
    @RabbitHandler
    public void onUpdated(ArticleUpdatedEvent event) {
        articleCache.evict(event.articleId());
    }

    /**
     * 消费文章软删除事件，二次清除文章缓存。
     *
     * @param event 文章软删除领域事件
     */
    @RabbitHandler
    public void onDeleted(ArticleDeletedEvent event) {
        articleCache.evict(event.articleId());
    }
}
