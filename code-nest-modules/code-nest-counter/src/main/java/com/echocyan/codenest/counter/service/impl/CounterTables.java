package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.counter.mapper.CounterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 计数表持久化映射组件。
 *
 * <p>封装文章、用户、评论统计表的元数据命名规范与读写操作：
 * <ul>
 *   <li>表名：{@code {target}_stat}，如 {@code article_stat}。</li>
 *   <li>主键列：{@code {target}_id}，如 {@code article_id}。</li>
 *   <li>计数字段/列名：指标去除实体前缀后加 {@code _count}，如 {@code ARTICLE_LIKE} 对应 {@code like_count}。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class CounterTables {

    private final CounterMapper counterMapper;

    /**
     * 获取指定目标类型包含的所有计数指标。
     *
     * @param target 目标实体类型
     * @return 属于该类型的全部指标列表
     */
    static List<CounterMetric> metrics(CounterTarget target) {
        return Arrays.stream(CounterMetric.values()).filter(metric -> metric.target() == target).toList();
    }

    /**
     * 获取指标在 Redis Hash 中的字段名（去除目标类型前缀并小写）。
     *
     * @param metric 计数指标
     * @return Redis 字段名，如 {@code USER_LIKE_RECEIVED} 对应 {@code like_received}
     */
    static String field(CounterMetric metric) {
        return lower(metric.name().substring(metric.target().name().length() + 1));
    }

    /**
     * 将名称转为小写。
     *
     * @param name 原始字符串
     * @return 小写字符串
     */
    static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String table(CounterTarget target) {
        return lower(target.name()) + "_stat";
    }

    private static String idColumn(CounterTarget target) {
        return lower(target.name()) + "_id";
    }

    private static String column(CounterMetric metric) {
        return field(metric) + "_count";
    }

    /**
     * 重置数据库中指定对象的单项计数。
     *
     * @param metric   计数指标
     * @param targetId 目标对象 ID
     * @param value    重置计数值
     */
    void reset(CounterMetric metric, long targetId, long value) {
        CounterTarget target = metric.target();
        counterMapper.reset(table(target), idColumn(target), column(metric), targetId, value, DateTimes.now());
    }

    /**
     * 批量查询对象的持久化计数。
     *
     * @param target    目标实体类型
     * @param targetIds 对象 ID 集合
     * @return 对象 ID 到计数的映射，仅包含数据库中存在计数行的对象
     */
    Map<Long, Counts> select(CounterTarget target, Collection<Long> targetIds) {
        Map<Long, Counts> result = new HashMap<>();
        if (targetIds.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : counterMapper.selectByIds(table(target), idColumn(target), targetIds)) {
            Map<CounterMetric, Long> values = new EnumMap<>(CounterMetric.class);
            metrics(target).forEach(metric -> values.put(metric, ((Number) row.get(column(metric))).longValue()));
            result.put(((Number) row.get(idColumn(target))).longValue(), new Counts(values));
        }
        return result;
    }

    /**
     * 查询在指定主键区间 {@code (afterId, upperId]} 内该指标计数值大于 0 的对象 ID 列表。
     *
     * @param metric  计数指标
     * @param afterId 起始 ID（不含）
     * @param upperId 截止 ID（含）
     * @return 计数大于 0 的对象 ID 列表
     */
    List<Long> selectNonZeroIds(CounterMetric metric, long afterId, long upperId) {
        CounterTarget target = metric.target();
        return counterMapper.selectNonZeroIds(table(target), idColumn(target), column(metric), afterId, upperId);
    }

    /**
     * 批量覆写对象的各项计数为绝对值；不存在对应行时插入。
     *
     * @param target 目标实体类型
     * @param counts 对象 ID 到其各项计数的映射
     */
    void upsert(CounterTarget target, Map<Long, Counts> counts) {
        if (counts.isEmpty()) {
            return;
        }
        List<CounterMetric> metrics = metrics(target);
        List<List<Long>> rows = new ArrayList<>();
        counts.forEach((id, values) -> {
            List<Long> row = new ArrayList<>();
            row.add(id);
            metrics.forEach(metric -> row.add(values.get(metric)));
            rows.add(row);
        });
        counterMapper.upsertAll(table(target), idColumn(target), metrics.stream().map(CounterTables::column).toList(),
                rows, DateTimes.now());
    }
}
