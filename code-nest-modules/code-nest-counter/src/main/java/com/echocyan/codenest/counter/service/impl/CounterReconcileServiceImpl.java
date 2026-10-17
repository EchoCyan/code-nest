package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterSource;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.counter.service.CounterReconcileService;
import com.echocyan.codenest.framework.lock.RedisLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 计数对账服务实现类。
 *
 * <p>按指标分页比对 {@link CounterSource} 精确事实计数、Redis 缓存与 MySQL 统计表数值；
 * 识别不一致及孤儿记录（事实已删但统计非零），调用 {@link CounterApi#reset} 统一纠偏。
 *
 * <p>并发与一致性说明：
 * <ul>
 *   <li>统计与修正期间发生的并发写入可能产生 ±1 偏差，由下个对账周期纠正。</li>
 *   <li>在途 MQ 事件若在重置之后到达，将在修正值基础上递增。</li>
 * </ul>
 */
@Slf4j
@Service
class CounterReconcileServiceImpl implements CounterReconcileService {

    /**
     * 单页对账统计对象数及批量比对读取上限。
     */
    static final int BATCH = 500;

    /**
     * 对账分布式锁 Redis 键名。
     */
    private static final String LOCK_KEY = "counter:reconcile:lock";

    /**
     * 分布式锁租约时长；超过单次对账最大预期耗时，保障宕机时锁自动释放。
     */
    private static final Duration LOCK_TTL = Duration.ofHours(1);

    private final CounterApi counterApi;
    private final RedisCounterStore redisCounterStore;
    private final CounterTables counterTables;
    private final RedisLock redisLock;
    private final Map<CounterMetric, CounterSource> sources = new EnumMap<>(CounterMetric.class);

    /**
     * 构造对账服务并注册所有计数数据源。
     *
     * @param counterApi        计数门面接口
     * @param redisCounterStore Redis 计数存储
     * @param counterTables     计数表持久化映射
     * @param redisLock         分布式锁组件
     * @param sources           各模块注册的计数数据源列表
     * @throws IllegalStateException 若同一指标存在多个数据源
     */
    CounterReconcileServiceImpl(CounterApi counterApi,
                                RedisCounterStore redisCounterStore,
                                CounterTables counterTables,
                                RedisLock redisLock,
                                List<CounterSource> sources) {
        this.counterApi = counterApi;
        this.redisCounterStore = redisCounterStore;
        this.counterTables = counterTables;
        this.redisLock = redisLock;
        sources.forEach(source -> source.metrics().forEach(metric -> {
            if (this.sources.putIfAbsent(metric, source) != null) {
                throw new IllegalStateException("计数指标有多个对账来源: " + metric);
            }
        }));
    }

    @Override
    public Optional<Map<CounterMetric, Long>> reconcile() {
        Optional<Map<CounterMetric, Long>> result = redisLock.tryRun(LOCK_KEY, LOCK_TTL, () -> {
            Map<CounterMetric, Long> corrected = new EnumMap<>(CounterMetric.class);
            sources.forEach((metric, source) -> corrected.put(metric, reconcile(metric, source)));
            log.info("Counter reconciliation finished, corrected: {}", corrected);
            return corrected;
        });
        if (result.isEmpty()) {
            log.info("Counter reconciliation is already running on another instance, skipped");
        }
        return result;
    }

    /**
     * 定时全量对账任务，每周一凌晨 04:00 执行。
     */
    @Scheduled(cron = "0 0 4 * * MON")
    void scheduledReconcile() {
        reconcile();
    }

    /**
     * 对单个指标执行全量游标分页对账。
     *
     * @param metric 计数指标
     * @param source 对应的精确数据源
     * @return 该指标被修正的对象总数
     */
    private long reconcile(CounterMetric metric, CounterSource source) {
        long corrected = 0;
        long afterId = 0;
        boolean last;
        do {
            List<IdCount> page = source.countAfter(metric, afterId, BATCH);
            last = page.size() < BATCH;
            long upperId = last ? Long.MAX_VALUE : page.getLast().id();
            Map<Long, Long> exact = new HashMap<>();
            // 计数表里有值、但关系表里已经没有对应行的对象，精确计数为 0
            counterTables.selectNonZeroIds(metric, afterId, upperId).forEach(id -> exact.put(id, 0L));
            page.forEach(row -> exact.put(row.id(), row.count()));
            corrected += correct(metric, exact);
            afterId = upperId;
        } while (!last);
        return corrected;
    }

    /**
     * 批量比对当前计数值并纠偏。
     *
     * @param metric 计数指标
     * @param exact  对象 ID 到精确计数值的映射
     * @return 本批次被修正的对象数
     */
    private long correct(CounterMetric metric, Map<Long, Long> exact) {
        long corrected = 0;
        List<Long> ids = List.copyOf(exact.keySet());
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<Long> batch = ids.subList(from, Math.min(from + BATCH, ids.size()));
            Map<Long, Counts> current = redisCounterStore.getIfPresent(metric.target(), batch);
            Map<Long, Counts> stored = counterTables.select(metric.target(), batch);
            for (Long id : batch) {
                long value = exact.get(id);
                long storedValue = stored.containsKey(id) ? stored.get(id).get(metric) : 0;
                boolean redisWrong = current.containsKey(id) && current.get(id).get(metric) != value;
                if (redisWrong || storedValue != value) {
                    counterApi.reset(metric, id, value);
                    corrected++;
                }
            }
        }
        return corrected;
    }
}
