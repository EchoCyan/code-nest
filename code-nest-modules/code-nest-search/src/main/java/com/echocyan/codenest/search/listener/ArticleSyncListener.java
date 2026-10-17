package com.echocyan.codenest.search.listener;

import com.echocyan.codenest.article.api.event.ArticleDeletedEvent;
import com.echocyan.codenest.article.api.event.ArticlePublishedEvent;
import com.echocyan.codenest.article.api.event.ArticleUpdatedEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import com.echocyan.codenest.search.service.ArticleIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 文章数据同步监听器。
 * <p>
 * 消费队列 {@value #QUEUE}，监听文章发布、编辑与删除领域事件，异步触发搜索索引的增量同步。
 * 索引写入与删除基于 Elasticsearch 外部版本号（External Versioning）控制，天然幂等且具备乱序自愈能力，
 * 无需声明 {@link com.echocyan.codenest.framework.mq.IdempotentConsumer}。
 */
@Component
@RabbitListener(queues = ArticleSyncListener.QUEUE)
@RequiredArgsConstructor
public class ArticleSyncListener {

    static final String QUEUE = "search.article-sync";

    private final ArticleIndex articleIndex;

    /**
     * 声明队列拓扑以及与领域事件交换机的路由绑定。
     *
     * @return RabbitMQ 声明集
     */
    @Bean
    static Declarables articleSyncQueue() {
        return EventQueues.declare(QUEUE, ArticleSyncListener.class);
    }

    /**
     * 处理文章发布事件，同步新增文档至索引。
     *
     * @param event 文章发布领域事件
     */
    @RabbitHandler
    public void onPublished(ArticlePublishedEvent event) {
        articleIndex.sync(event.articleId());
    }

    /**
     * 处理文章更新事件，同步更新索引文档。
     *
     * @param event 文章更新领域事件
     */
    @RabbitHandler
    public void onUpdated(ArticleUpdatedEvent event) {
        articleIndex.sync(event.articleId());
    }

    /**
     * 处理文章删除事件，从索引中移除文档。
     *
     * @param event 文章删除领域事件
     */
    @RabbitHandler
    public void onDeleted(ArticleDeletedEvent event) {
        articleIndex.sync(event.articleId());
    }
}
