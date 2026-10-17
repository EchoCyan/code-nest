package com.echocyan.codenest.user.service.impl;

import com.echocyan.codenest.framework.cache.TwoLevelCache;
import com.echocyan.codenest.framework.cache.TwoLevelCaches;
import com.echocyan.codenest.user.api.UserBrief;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户缓存配置类。
 * <p>
 * 声明用户基础简要信息两级缓存 Bean，缓存前缀为 {@code user:brief}，用户修改个人资料后主动失效。
 */
@Configuration(proxyBeanMethods = false)
class UserCacheConfig {

    /**
     * 注册用户简要信息两级缓存。
     *
     * @param caches 两级缓存工厂
     * @return 用户简要信息缓存实例
     */
    @Bean
    TwoLevelCache<UserBrief> userBriefCache(TwoLevelCaches caches) {
        return caches.create("user:brief", UserBrief.class);
    }
}
