package com.echocyan.codenest.framework.cache;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 缓存工厂与多级失效事件监听器。
 *
 * <p>负责创建 {@link TwoLevelCache} 与 {@link BloomFilter}，
 * 并订阅 Redis 频道 {@value #INVALIDATE_CHANNEL} 广播淘汰各节点本地缓存。</p>
 */
@Component
public class TwoLevelCaches implements MessageListener {

    /**
     * 跨实例本地缓存失效广播的 Redis Pub/Sub 频道名。消息体为被淘汰的完整 Redis Key。
     */
    static final String INVALIDATE_CHANNEL = "cache:invalidate";

    /**
     * Redis 字符串操作模板。
     */
    private final StringRedisTemplate redis;

    /**
     * JSON 序列化工具。
     */
    private final JsonMapper jsonMapper;

    /**
     * 注册了本地 Caffeine 缓存的组件映射表。
     * Key 为 Redis 键前缀（如 {@code cache:article:detail:}），Value 为持有该前缀本地缓存的组件列表。
     */
    private final Map<String, List<TwoLevelCache<?>>> localCaches = new ConcurrentHashMap<>();

    /**
     * 构造缓存工厂组件。
     *
     * @param redis      Redis 字符串模板
     * @param jsonMapper JSON 映射器
     */
    public TwoLevelCaches(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 创建纯集中式（单级 Redis）读缓存实例。
     *
     * <p>适用于数据变更相对频繁或无需消耗应用 JVM 堆内存的读缓存场景（例如用户/文章简要信息摘要缓存）。
     *
     * @param <V>  缓存实体类型
     * @param name 缓存业务名称（全局唯一，格式建议为 {@code <模块>:<用途>}，将作为 Redis 键前缀）
     * @param type 缓存实体 Class 对象
     * @return 单级 Redis 缓存操作对象
     */
    public <V> TwoLevelCache<V> create(String name, Class<V> type) {
        return new TwoLevelCache<>(name, type, redis, jsonMapper, false, null);
    }

    /**
     * 创建两级读缓存实例（Caffeine 本地缓存 + Redis 集中缓存 + 布隆过滤器防穿透）。
     *
     * <p>专用于极高读取吞吐、热点效应明显的关键资源（例如文章详情）。
     * 查询时先读本地内存；未命中时通过布隆过滤器拦截非法 ID；再查询 Redis 并回填。
     * 新建的实例将自动注册至失效事件广播路由表中。
     *
     * @param <V>         缓存实体类型
     * @param name        缓存业务名称（全局唯一，格式建议为 {@code <模块>:<用途>}）
     * @param type        缓存实体 Class 对象
     * @param bloomFilter 布隆过滤器实例，用于在回查 Redis/DB 前快速拦截不存在的 ID；可为 {@code null}
     * @return 两级缓存操作对象
     */
    public <V> TwoLevelCache<V> createTwoLevel(String name, Class<V> type, BloomFilter bloomFilter) {
        TwoLevelCache<V> cache = new TwoLevelCache<>(name, type, redis, jsonMapper, true, bloomFilter);
        localCaches.computeIfAbsent(TwoLevelCache.keyPrefixOf(name), prefix -> new CopyOnWriteArrayList<>()).add(cache);
        return cache;
    }

    /**
     * 创建 Redis 8 原生布隆过滤器实例。
     *
     * @param name 过滤器业务名称（全局唯一，Redis 键为 {@code bf:<name>}）
     * @return 布隆过滤器实例
     */
    public BloomFilter bloomFilter(String name) {
        return new BloomFilter(name, redis);
    }

    /**
     * 处理 Redis Pub/Sub 频道广播的本地缓存失效消息。
     *
     * <p>解析接收到的完整 Redis Key，截取业务前缀查找对应的两级缓存组件，
     * 提取末尾的业务 ID 并触发本地 Caffeine 缓存淘汰。
     *
     * @param message 广播消息（消息体为被删除的 Redis Key 字符串）
     * @param pattern 订阅模式（本场景未使用模式匹配，为 null）
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody(), StandardCharsets.UTF_8);
        int separator = key.lastIndexOf(':');
        List<TwoLevelCache<?>> caches = localCaches.get(key.substring(0, separator + 1));
        if (caches != null) {
            long id = Long.parseLong(key.substring(separator + 1));
            caches.forEach(cache -> cache.invalidateLocal(id));
        }
    }
}
