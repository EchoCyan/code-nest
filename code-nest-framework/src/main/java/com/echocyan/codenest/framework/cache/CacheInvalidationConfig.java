package com.echocyan.codenest.framework.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 本地缓存失效广播监听配置。
 *
 * <p>注册 {@link RedisMessageListenerContainer} 并订阅 {@value TwoLevelCaches#INVALIDATE_CHANNEL} 频道，
 * 将淘汰事件分发至 {@link TwoLevelCaches} 剔除本地缓存。</p>
 */
@Configuration(proxyBeanMethods = false)
class CacheInvalidationConfig {

    /**
     * 注册 Redis 消息监听容器 Bean，绑定失效事件监听器与订阅频道。
     *
     * @param connectionFactory Redis 连接工厂
     * @param caches            两级缓存管理器兼消息监听处理器
     * @return 初始化的 Redis 消息监听容器实例
     */
    @Bean
    RedisMessageListenerContainer cacheInvalidationListenerContainer(RedisConnectionFactory connectionFactory,
                                                                     TwoLevelCaches caches) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(caches, new ChannelTopic(TwoLevelCaches.INVALIDATE_CHANNEL));
        return container;
    }
}
