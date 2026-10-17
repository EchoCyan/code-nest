package com.echocyan.codenest.framework.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 基于主键 ID 的读缓存组件（Cache-Aside 模式）。
 *
 * <p>支持纯 Redis 一级缓存或 Caffeine + Redis 两级缓存架构，统一由 {@link TwoLevelCaches} 构建：
 * <ul>
 *   <li><b>防雪崩：</b>基础 TTL 为 30 分钟并叠加 0~5 分钟随机抖动；</li>
 *   <li><b>防穿透：</b>空值回填短生命周期（60 秒）标记，两级模式下可选布隆过滤器预检；</li>
 *   <li><b>防击穿：</b>本地缓存基于 Caffeine 加载锁实现并发单飞回源（Single-Flight）；</li>
 *   <li><b>广播失效：</b>两级模式下通过 Redis Pub/Sub 广播淘汰各节点本地缓存；</li>
 *   <li><b>事务感知：</b>写操作在 Spring 事务提交后（{@code afterCommit}）执行缓存淘汰。</li>
 * </ul>
 * </p>
 *
 * @param <V> 缓存对象的数据类型
 */
@Slf4j
public class TwoLevelCache<V> {

    /**
     * Redis 基础缓存过期时间（30 分钟）。
     */
    private static final Duration TTL = Duration.ofMinutes(30);

    /**
     * Redis 缓存随机抖动上限秒数（5 分钟 = 300 秒），实际过期时间为 30 到 35 分钟随机分布。
     */
    private static final long TTL_JITTER_SECONDS = Duration.ofMinutes(5).toSeconds();

    /**
     * 针对空值标记缓存的存活时间（60 秒），防止缓存穿透。
     */
    private static final Duration NULL_TTL = Duration.ofSeconds(60);

    /**
     * Caffeine 一级本地缓存的最大条目数上限（10,000 条），采用 W-TinyLFU 淘汰策略。
     */
    private static final int LOCAL_MAXIMUM_SIZE = 10_000;

    /**
     * Caffeine 一级本地缓存的写入后过期时间（60 秒），作为跨节点广播丢失时的兜底时限。
     */
    private static final Duration LOCAL_TTL = Duration.ofSeconds(60);

    /**
     * 空值在 Redis 中的序列化占位符，即 JSON 文本 {@code "null"}。
     */
    private static final String NULL = "null";

    /**
     * 当前缓存组件在 Redis 中的 Key 前缀（如 {@code cache:article:detail:}）。
     */
    private final String keyPrefix;

    /**
     * 缓存值实体类型的 Class 对象。
     */
    private final Class<V> type;

    /**
     * Redis 字符串操作客户端。
     */
    private final StringRedisTemplate redis;

    /**
     * JSON 序列化与反序列化工具。
     */
    private final JsonMapper jsonMapper;

    /**
     * 本地 Caffeine 缓存容器；仅在启用两级缓存时实例化，单级 Redis 缓存时为 {@code null}。
     */
    private final Cache<Long, V> local;

    /**
     * 布隆过滤器；仅在启用两级缓存且指定了过滤器时有效，用于前置拦截一定不存在的 ID。
     */
    private final BloomFilter bloomFilter;

    /**
     * 构造缓存组件实例，包级私有，仅由 {@link TwoLevelCaches} 工厂调用。
     *
     * @param name        缓存业务命名标识
     * @param type        缓存实体 Class
     * @param redis       Redis 客户端模板
     * @param jsonMapper  JSON 映射器
     * @param twoLevel    是否启用本地 Caffeine 一级缓存
     * @param bloomFilter 布隆过滤器实例，为 {@code null} 时不进行布隆过滤
     */
    TwoLevelCache(String name,
                  Class<V> type,
                  StringRedisTemplate redis,
                  JsonMapper jsonMapper,
                  boolean twoLevel,
                  BloomFilter bloomFilter) {
        this.keyPrefix = keyPrefixOf(name);
        this.type = type;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.local = twoLevel
                ? Caffeine.newBuilder().maximumSize(LOCAL_MAXIMUM_SIZE).expireAfterWrite(LOCAL_TTL).build()
                : null;
        this.bloomFilter = bloomFilter;
    }

    /**
     * 根据业务名称生成 Redis 统一键前缀。
     *
     * @param name 业务标识名称
     * @return 格式化后的 Redis 键前缀（格式为 {@code cache:<name>:}）
     */
    static String keyPrefixOf(String name) {
        return "cache:" + name + ":";
    }

    /**
     * 根据序列化内容动态计算缓存存活时间。
     *
     * @param encoded 待缓存的 JSON 文本
     * @return 若为 {@link #NULL} 标记返回短生命周期 {@link #NULL_TTL}；否则返回叠加随机抖动的标准 TTL
     */
    private static Duration ttlOf(String encoded) {
        if (NULL.equals(encoded)) {
            return NULL_TTL;
        }
        return TTL.plusSeconds(ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS + 1));
    }

    /**
     * 读取指定 ID 的缓存值；若未命中则调用加载函数回填后返回。
     *
     * <p>在两级缓存模式下，请求按照如下顺序流转：
     * <ol>
     *   <li>检查 Caffeine 本地缓存，命中直接返回；</li>
     *   <li>若配置了布隆过滤器，检查 ID 存在性，若布隆过滤器判定绝对不存在则直接返回 {@code null}；</li>
     *   <li>查询 Redis L2 缓存，若命中有效值或空值占位符则返回；</li>
     *   <li>执行 {@code loader} 回查底层数据库，并将结果回填至 Redis。</li>
     * </ol>
     * 本地缓存返回的是 JVM 共享内存对象，调用方不得直接修改其内部状态。
     *
     * @param id     业务主键 ID
     * @param loader 数据库回源加载函数，入参为目标 ID，数据不存在时应返回 {@code null}
     * @return 对应的缓存数据对象；若数据库中不存在则返回 {@code null}
     */
    public V get(long id, Function<Long, V> loader) {
        if (local == null) {
            return getFromRedis(id, loader);
        }
        return local.get(id, key -> bloomFilter.mightContain(key) ? getFromRedis(key, loader) : null);
    }

    /**
     * 从 Redis 读取单条数据，未命中时调用加载函数回查并回填。
     *
     * @param id     业务主键 ID
     * @param loader 数据库加载函数
     * @return 业务数据或 {@code null}
     */
    private V getFromRedis(long id, Function<Long, V> loader) {
        String key = key(id);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return decode(cached);
        }
        V value = loader.apply(id);
        String encoded = encode(value);
        redis.opsForValue().set(key, encoded, ttlOf(encoded));
        return value;
    }

    /**
     * 批量读取指定 ID 集合的缓存数据。
     *
     * <p>批量读取流程优化如下：
     * <ol>
     *   <li>首先在本地缓存（若启用）中批量检索；</li>
     *   <li>未命中本地缓存的 ID 发起一次批量 Redis {@code MGET} 查询；</li>
     *   <li>仍未命中的 ID 统一归集后，执行一次 {@code batchLoader} 批量回源加载数据库；</li>
     *   <li>回源数据通过 Redis Pipeline 管道批量异步回填 Redis，降低网络往返开销。</li>
     * </ol>
     * 注意：为避免高基数场景下的网络往返放大，批量读取流程不经过布隆过滤器。
     *
     * @param ids         业务主键 ID 集合
     * @param batchLoader 批量回源加载函数，仅接收在缓存中确实缺失的 ID 集合，返回 ID 到数据实体的映射表
     * @return ID 到实体的映射 Map（仅包含实际存在的数据，不存在的 ID 不会出现在结果集中）
     */
    public Map<Long, V> getAll(Collection<Long> ids, Function<Collection<Long>, Map<Long, V>> batchLoader) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        if (local == null) {
            return getAllFromRedis(ids, batchLoader);
        }
        return local.getAll(ids, misses -> getAllFromRedis(List.copyOf(misses), batchLoader));
    }

    /**
     * 通过 Redis MGET 批量加载，对缺失的数据执行批量回源并通过 Pipeline 批量写回 Redis。
     *
     * @param ids         待查询的 ID 集合
     * @param batchLoader 批量回源函数
     * @return 查询结果映射 Map
     */
    private Map<Long, V> getAllFromRedis(Collection<Long> ids, Function<Collection<Long>, Map<Long, V>> batchLoader) {
        List<Long> distinct = ids.stream().distinct().toList();
        List<String> cached = redis.opsForValue().multiGet(distinct.stream().map(this::key).toList());
        Map<Long, V> result = new HashMap<>();
        List<Long> misses = new ArrayList<>();
        for (int i = 0; i < distinct.size(); i++) {
            String hit = cached.get(i);
            if (hit == null) {
                misses.add(distinct.get(i));
            } else if (!NULL.equals(hit)) {
                result.put(distinct.get(i), decode(hit));
            }
        }
        if (misses.isEmpty()) {
            return result;
        }
        Map<Long, V> loaded = batchLoader.apply(misses);
        result.putAll(loaded);
        Map<String, String> backfill = new LinkedHashMap<>();
        misses.forEach(id -> backfill.put(key(id), encode(loaded.get(id))));
        redis.executePipelined((RedisCallback<Object>) connection -> {
            backfill.forEach((key, value) -> connection.stringCommands()
                    .set(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8),
                            Expiration.from(ttlOf(value)), SetOption.upsert()));
            return null;
        });
        return result;
    }

    /**
     * 显式淘汰指定 ID 的缓存。
     *
     * <p>若当前处于活跃的 Spring 数据库事务中，淘汰操作将推迟至事务成功提交后（{@code afterCommit}）执行；
     * 若事务发生回滚则自动取消淘汰，避免网络抖动或事务失败时过早击毁有效缓存。
     * 若处于无事务环境，则立即执行淘汰。两级模式下会自动通过 Redis Pub/Sub 广播失效通知。
     *
     * @param id 待淘汰的业务主键 ID
     */
    public void evict(long id) {
        String key = key(id);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        evictNow(id, key);
                    } catch (RuntimeException e) {
                        log.warn("Failed to evict cache after commit: key={}", key, e);
                    }
                }
            });
        } else {
            evictNow(id, key);
        }
    }

    /**
     * 立即执行物理淘汰。
     *
     * <p>遵循严谨的删除顺序：先删除 Redis 集中式缓存，再清理本节点 Caffeine 缓存，
     * 避免当前节点在广播往返间隙重新从 Redis 读取到旧数据；最后通过 Pub/Sub 发布广播。
     *
     * @param id  业务主键 ID
     * @param key 完整的 Redis Key
     */
    private void evictNow(long id, String key) {
        redis.delete(key);
        if (local != null) {
            local.invalidate(id);
            redis.convertAndSend(TwoLevelCaches.INVALIDATE_CHANNEL, key);
        }
    }

    /**
     * 响应远端节点的失效广播，从本地 Caffeine 缓存中淘汰对应 ID 的记录。
     *
     * @param id 待淘汰的业务主键 ID
     */
    void invalidateLocal(long id) {
        if (local != null) {
            local.invalidate(id);
        }
    }

    /**
     * 根据业务主键 ID 构造完整的 Redis 缓存 Key。
     *
     * @param id 业务主键 ID
     * @return 完整的 Redis 键名
     */
    private String key(long id) {
        return keyPrefix + id;
    }

    /**
     * 将业务实体序列化为 JSON 字符串；若为 {@code null} 则输出 {@link #NULL} 标记。
     *
     * @param value 待序列化的实体对象
     * @return JSON 文本
     */
    private String encode(V value) {
        return value == null ? NULL : jsonMapper.writeValueAsString(value);
    }

    /**
     * 将 JSON 文本反序列化为实体对象；若为 {@link #NULL} 标记则返回 {@code null}。
     *
     * @param json 缓存的 JSON 文本
     * @return 业务实体对象或 {@code null}
     */
    private V decode(String json) {
        return NULL.equals(json) ? null : jsonMapper.readValue(json, type);
    }
}
