package com.echocyan.codenest.notification.listener;

import com.echocyan.codenest.article.api.event.CommentCreatedEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import com.echocyan.codenest.framework.mq.IdempotentConsumer;
import com.echocyan.codenest.interaction.api.event.LikeCreatedEvent;
import com.echocyan.codenest.notification.service.NotificationService;
import com.echocyan.codenest.social.api.event.FollowCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 站内通知消息队列监听器。
 * <p>
 * 监听 {@value #QUEUE} 队列，消费点赞、评论（含二级回复）及关注等领域事件，转交 {@link NotificationService} 处理；
 * 各事件处理方法通过 {@link IdempotentConsumer} 保障消费幂等性。
 */
@Component
@RabbitListener(queues = NotificationListener.QUEUE)
@RequiredArgsConstructor
public class NotificationListener {

    /**
     * 站内通知消费队列名称。
     */
    static final String QUEUE = "notification.create";

    private final NotificationService notificationService;

    /**
     * 声明通知队列及其与领域事件交换机的绑定关系。
     *
     * @return 队列与绑定的声明集合
     */
    @Bean
    static Declarables notificationQueue() {
        return EventQueues.declare(QUEUE, NotificationListener.class);
    }

    /**
     * 消费点赞创建事件。
     *
     * @param event 点赞创建事件
     */
    @RabbitHandler
    @IdempotentConsumer
    public void onLike(LikeCreatedEvent event) {
        notificationService.notifyLike(event);
    }

    /**
     * 消费评论或回复创建事件。
     *
     * @param event 评论或回复创建事件
     */
    @RabbitHandler
    @IdempotentConsumer
    public void onComment(CommentCreatedEvent event) {
        notificationService.notifyComment(event);
    }

    /**
     * 消费关注创建事件。
     *
     * @param event 关注创建事件
     */
    @RabbitHandler
    @IdempotentConsumer
    public void onFollow(FollowCreatedEvent event) {
        notificationService.notifyFollow(event);
    }
}
