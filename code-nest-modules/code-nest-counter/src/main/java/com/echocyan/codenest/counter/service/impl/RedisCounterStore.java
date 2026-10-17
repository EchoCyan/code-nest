package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 计数存储组件。
 *
 * <p>以 Redis 为主计数介质，MySQL 作为持久化镜像。键设计规范如下：
 * <ul>
 *   <li>{@code counter:{target}:{id}}：对象全量计数 Hash，字段名对应 {@link CounterTables#field}，不设 TTL。</li>
 *   <li>{@code counter:dirty:{target}}：待落库对象 ID 集合（Set）。</li>
 *   <li>{@code counter:dedup:{messageId}}：消费去重键，24 小时过期。</li>
 * </ul>
 *
 * <p>核心机制保障：
 * <ul>
 *   <li>原子更新：Lua 脚本执行累加与幂等去重；Hash 或字段缺失时先从 MySQL 回填（{@code HSETNX}）再累加，避免冲零。</li>
 *   <li>定时落库：定时以 {@code SPOP} 拉取待落库集合，以绝对值批量写入 MySQL。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RedisCounterStore {

    /**
     * 单次落库 {@code SPOP} 取出的对象数量上限与单条批量 SQL 行数上限。
     */
    static final int FLUSH_BATCH = 1000;

    /**
     * 消费去重键过期时长（24 小时），大于 Outbox 最大重发窗口（12 小时）。
     */
    private static final Duration DEDUP_TTL = Duration.ofDays(1);

    /**
     * Lua 脚本返回值：表示 Hash 或指定字段不存在，需执行回填。
     */
    private static final Long MISS = -1L;

    /**
     * 计数累加与去重 Lua 脚本。
     *
     * <p>KEYS[1]: 计数 Hash，KEYS[2]: 待落库集合，KEYS[3]: 去重 Key（可选）。<br>
     * ARGV[1]: 字段名，ARGV[2]: 增量，ARGV[3]: 对象 ID，ARGV[4]: 去重过期秒数。<br>
     * 返回：-1 为 MISS，0 为重复消息，1 为更新成功。
     */
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 0 then
                return -1
            end
            if KEYS[3] and redis.call('EXISTS', KEYS[3]) == 1 then
                return 0
            end
            if redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2]) < 0 then
                redis.call('HSET', KEYS[1], ARGV[1], 0)
            end
            redis.call('SADD', KEYS[2], ARGV[3])
            if KEYS[3] then
                redis.call('SET', KEYS[3], 1, 'EX', ARGV[4])
            end
            return 1
            """, Long.class);

    /**
     * 缺失字段懒回填 Lua 脚本（使用 {@code HSETNX} 仅写入 Hash 中不存在的字段，保留已有计数）。
     */
    private static final byte[] BACKFILL = """
            for i = 1, #ARGV, 2 do
                redis.call('HSETNX', KEYS[1], ARGV[i], ARGV[i + 1])
            end
            return 1
            """.getBytes(StandardCharsets.UTF_8);

    /**
     * 计数纠偏重置 Lua 脚本（仅在 Hash 存在时覆写并标记待落库，不存在时留给下次读取懒加载）。
     */
    private static final RedisScript<Long> RESET_IF_PRESENT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            redis.call('SADD', KEYS[2], ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final CounterTables counterTables;

    private static String hashKey(CounterTarget target, long id) {
        return "counter:" + CounterTables.lower(target.name()) + ":" + id;
    }

    private static String dirtyKey(CounterTarget target) {
        return "counter:dirty:" + CounterTables.lower(target.name());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 原子增减对象计数值（计算结果下限为 0），并将对象加入待落库集合。
     *
     * @param metric    计数指标
     * @param targetId  目标对象 ID
     * @param delta     增量（支持负数）
     * @param messageId 幂等去重标识；为 null 时不去重
     * @throws IllegalStateException 若回填后该字段在 Redis 中仍不存在
     */
    void increment(CounterMetric metric, long targetId, long delta, String messageId) {
        CounterTarget target = metric.target();
        List<String> keys = new ArrayList<>(List.of(hashKey(target, targetId), dirtyKey(target)));
        if (messageId != null) {
            keys.add("counter:dedup:" + messageId);
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            Long result = redis.execute(INCREMENT, keys, CounterTables.field(metric), String.valueOf(delta),
                    String.valueOf(targetId), String.valueOf(DEDUP_TTL.toSeconds()));
            if (!MISS.equals(result)) {
                return;
            }
            backfill(target, List.of(targetId));
        }
        throw new IllegalStateException("计数回填后仍不存在: " + hashKey(target, targetId) + " " + CounterTables.field(metric));
    }

    /**
     * 批量读取对象计数；若缓存未命中或缺少字段，自动从 MySQL 批量回填后再读。
     *
     * @param target    目标实体类型
     * @param targetIds 对象 ID 集合
     * @return 对象 ID 到各项计数的映射
     */
    Map<Long, Counts> get(CounterTarget target, Collection<Long> targetIds) {
        List<Long> ids = List.copyOf(new LinkedHashSet<>(targetIds));
        Map<Long, Counts> result = read(target, ids);
        List<Long> missing = ids.stream().filter(id -> !result.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            Map<Long, Counts> loaded = backfill(target, missing);
            Map<Long, Counts> filled = read(target, missing);
            // 回填后又被删除的对象，按 MySQL 的值返回
            missing.forEach(id -> result.put(id, filled.getOrDefault(id, loaded.get(id))));
        }
        return result;
    }

    /**
     * 仅从 Redis 读取对象计数，不触发数据库回填。
     *
     * @param target    目标实体类型
     * @param targetIds 对象 ID 集合
     * @return 仅包含缓存存在且字段齐全的对象计数映射
     */
    Map<Long, Counts> getIfPresent(CounterTarget target, Collection<Long> targetIds) {
        return read(target, List.copyOf(new LinkedHashSet<>(targetIds)));
    }

    /**
     * 若缓存存在，重置指定计数值并标记待落库；若缓存不存在则不写入。
     *
     * @param metric   计数指标
     * @param targetId 目标对象 ID
     * @param value    重置计数值
     */
    void resetIfPresent(CounterMetric metric, long targetId, long value) {
        CounterTarget target = metric.target();
        redis.execute(RESET_IF_PRESENT, List.of(hashKey(target, targetId), dirtyKey(target)),
                CounterTables.field(metric), String.valueOf(value), String.valueOf(targetId));
    }

    /**
     * 定时异步落库任务，每 5 秒执行一次。
     *
     * <p>利用 {@code SPOP} 的原子性防止多实例并发冲突；将变更对象以绝对值批量写入 MySQL。
     */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    void flush() {
        for (CounterTarget target : CounterTarget.values()) {
            List<String> ids;
            do {
                ids = redis.opsForSet().pop(dirtyKey(target), FLUSH_BATCH);
            } while (ids != null && flush(target, ids) && ids.size() == FLUSH_BATCH);
        }
    }

    /**
     * 执行单批次对象计数的持久化写入；失败时将 ID 放回待落库集合。
     *
     * @param target 目标实体类型
     * @param ids    待落库对象 ID 列表
     * @return 写入是否成功
     */
    private boolean flush(CounterTarget target, List<String> ids) {
        if (ids.isEmpty()) {
            return true;
        }
        try {
            // Hash 或字段缺失时（Redis 丢数据、新增了指标）先从 MySQL 回填，再整行写回
            counterTables.upsert(target, get(target, ids.stream().map(Long::valueOf).toList()));
            return true;
        } catch (RuntimeException e) {
            redis.opsForSet().add(dirtyKey(target), ids.toArray(String[]::new));
            log.error("Failed to flush counters, ids are returned to the dirty set: target={}, size={}",
                    target, ids.size(), e);
            return false;
        }
    }

    /**
     * 基于 Pipeline 批量执行 {@code HMGET} 读取对象计数。
     *
     * @param target 目标实体类型
     * @param ids    对象 ID 列表
     * @return 存在且字段完整的对象计数映射
     */
    private Map<Long, Counts> read(CounterTarget target, List<Long> ids) {
        List<CounterMetric> metrics = CounterTables.metrics(target);
        byte[][] fields = metrics.stream().map(metric -> bytes(CounterTables.field(metric))).toArray(byte[][]::new);
        List<Object> replies = redis.executePipelined((RedisCallback<Object>) connection -> {
            ids.forEach(id -> connection.hashCommands().hMGet(bytes(hashKey(target, id)), fields));
            return null;
        });
        Map<Long, Counts> result = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            List<?> values = (List<?>) replies.get(i);
            if (values.contains(null)) {
                continue;
            }
            Map<CounterMetric, Long> counts = new EnumMap<>(CounterMetric.class);
            for (int j = 0; j < metrics.size(); j++) {
                counts.put(metrics.get(j), Long.valueOf((String) values.get(j)));
            }
            result.put(ids.get(i), new Counts(counts));
        }
        return result;
    }

    /**
     * 从 MySQL 加载事实计数，并通过 Pipeline 执行 {@code HSETNX} 懒回填至 Redis。
     *
     * @param target 目标实体类型
     * @param ids    对象 ID 列表
     * @return 从 MySQL 读取的计数值映射（包含全量入参 ID）
     */
    private Map<Long, Counts> backfill(CounterTarget target, List<Long> ids) {
        Map<Long, Counts> loaded = counterTables.select(target, ids);
        ids.forEach(id -> loaded.putIfAbsent(id, new Counts(Map.of())));
        List<CounterMetric> metrics = CounterTables.metrics(target);
        redis.executePipelined((RedisCallback<Object>) connection -> {
            loaded.forEach((id, counts) -> {
                List<byte[]> keysAndArgs = new ArrayList<>();
                keysAndArgs.add(bytes(hashKey(target, id)));
                metrics.forEach(metric -> {
                    keysAndArgs.add(bytes(CounterTables.field(metric)));
                    keysAndArgs.add(bytes(String.valueOf(counts.get(metric))));
                });
                connection.scriptingCommands().eval(BACKFILL, ReturnType.INTEGER, 1,
                    keysAndArgs.toArray(byte[][]::new));
            });
            return null;
        });
        return loaded;
    }
}

