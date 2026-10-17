package com.echocyan.codenest.social.service;

import com.echocyan.codenest.counter.api.IdCount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Feed 动态流大 V 识别组件。
 * <p>
 * 粉丝数不低于阈值（{@link #threshold}）的作者被视作大 V。
 * 粉丝数维护在 Redis ZSet {@code {prefix}:followers}（member 为作者 ID，score 为其实时粉丝数）。
 * 读取 Feed 时仅需一次 {@code ZRANGEBYSCORE} 取出全部大 V，与用户关注列表求交集，无需逐个查询关注作者的粉丝数。
 * 键 {@code {prefix}:followers:ready} 标识全量初始化状态，缺失时在系统启动后按关注表批量重建。
 */
@Slf4j
class BigAuthors {

    /**
     * 全量重建时每批统计的作者数。
     */
    private static final int REBUILD_BATCH = 1000;

    /**
     * Lua 脚本：原子更新作者粉丝数（若为 0 则从 ZSet 移除），并返回更新前的旧粉丝数（不存在时返回 0）。
     */
    private static final RedisScript<Long> REPLACE = RedisScript.of("""
            local old = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if tonumber(ARGV[2]) == 0 then
                redis.call('ZREM', KEYS[1], ARGV[1])
            else
                redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
            end
            return tonumber(old or 0)
            """, Long.class);

    private final StringRedisTemplate redis;
    private final FollowerGraph followerGraph;
    private final String key;
    private final String readyKey;
    private final long threshold;

    /**
     * 构造大 V 识别器。
     *
     * @param redis         Redis 操作模板
     * @param followerGraph 关注关系图谱数据访问接口
     * @param keyPrefix     Feed 缓存键通用前缀
     * @param threshold     判定为大 V 的粉丝数阈值
     */
    BigAuthors(StringRedisTemplate redis, FollowerGraph followerGraph, String keyPrefix, long threshold) {
        this.redis = redis;
        this.followerGraph = followerGraph;
        this.key = keyPrefix + ":followers";
        this.readyKey = key + ":ready";
        this.threshold = threshold;
    }

    /**
     * 从给定的作者列表中筛选出当前属于大 V 的作者 ID 集合。
     *
     * @param authorIds 待判定的作者 ID 集合
     * @return 命中大 V 阈值的作者 ID 集合
     */
    Set<Long> among(Collection<Long> authorIds) {
        Set<String> big = redis.opsForZSet().rangeByScore(key, threshold, Double.POSITIVE_INFINITY);
        return authorIds.stream()
                .filter(id -> big.contains(String.valueOf(id)))
                .collect(Collectors.toSet());
    }

    /**
     * 判断指定作者是否为大 V。
     *
     * @param authorId 作者用户 ID
     * @return 若粉丝数达到大 V 阈值返回 {@code true}，否则返回 {@code false}
     */
    boolean isBig(long authorId) {
        Double followers = redis.opsForZSet().score(key, String.valueOf(authorId));
        return followers != null && followers >= threshold;
    }

    /**
     * 按数据库重新统计作者粉丝数并更新至 Redis。
     *
     * @param authorId 作者用户 ID
     * @return 若本次更新使作者从大 V 降级为普通作者返回 {@code true}，否则返回 {@code false}
     */
    boolean refresh(long authorId) {
        long followers = followerGraph.countFollowers(authorId);
        Long old = redis.execute(REPLACE, List.of(key), String.valueOf(authorId), String.valueOf(followers));
        return old >= threshold && followers < threshold;
    }

    /**
     * 若全量就绪标记不存在，从数据库分批统计并重建全部作者粉丝数缓存。
     */
    void rebuildIfAbsent() {
        if (Boolean.TRUE.equals(redis.hasKey(readyKey))) {
            return;
        }
        long imported = 0;
        List<IdCount> counts = followerGraph.countFollowersAfter(0, REBUILD_BATCH);
        while (!counts.isEmpty()) {
            Set<TypedTuple<String>> tuples = counts.stream()
                    .map(count -> TypedTuple.of(String.valueOf(count.id()), (double) count.count()))
                    .collect(Collectors.toSet());
            redis.opsForZSet().add(key, tuples);
            imported += counts.size();
            counts = followerGraph.countFollowersAfter(counts.getLast().id(), REBUILD_BATCH);
        }
        redis.opsForValue().set(readyKey, "1");
        log.info("Rebuilt follower counts of {} authors for the feed", imported);
    }
}
