package com.echocyan.codenest.framework.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis ZSet 的分布式滑动窗口限流器。
 *
 * <p>通过 Lua 脚本原子执行：清理过期时间戳、统计窗口请求数；
 * 未超限时原子写入并放行，任一超限则返回需等待的冷却毫秒数（All-or-Nothing）。</p>
 */
@Component
@RequiredArgsConstructor
public class SlidingWindowRateLimiter {

    /**
     * 限流 Key 在 Redis 中的统一全局前缀。
     */
    private static final String KEY_PREFIX = "rate-limit:";

    /**
     * 多配额原子校验与记录 Lua 脚本。
     *
     * <p>KEYS 包含所有关联额度的 ZSet 键名；
     * ARGV[1] 为本次请求唯一 UUID，随后依次成对传入各额度的 limit（阈值）和 window（窗口毫秒数）。
     * 脚本返回值：0 表示所有配额均满足并已成功记录；正数表示被拒绝后至少需要等待的冷却毫秒数。
     */
    private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local wait = 0
            for i, key in ipairs(KEYS) do
                local limit = tonumber(ARGV[i * 2])
                local window = tonumber(ARGV[i * 2 + 1])
                redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
                local count = redis.call('ZCARD', key)
                if count >= limit then
                    -- 调低限额后窗口内可能多于 limit 条，要等到只剩 limit - 1 条才有名额
                    local freeing = redis.call('ZRANGE', key, count - limit, count - limit, 'WITHSCORES')
                    wait = math.max(wait, tonumber(freeing[2]) + window - now)
                end
            end
            if wait > 0 then
                return wait
            end
            for i, key in ipairs(KEYS) do
                redis.call('ZADD', key, now, ARGV[1])
                redis.call('PEXPIRE', key, ARGV[i * 2 + 1])
            end
            return 0
            """, Long.class);

    /**
     * Redis 字符串操作模板。
     */
    private final StringRedisTemplate redis;

    /**
     * 原子评估并尝试扣减一组限流配额。
     *
     * <p>当且仅当传入的所有配额均有余量时，才会将本次请求记录写入所有配额对应的 ZSet 中；
     * 若任一配额超限，则放弃所有配额写入，并计算出需等待的冷却时长。
     *
     * @param quotas 待申请扣减的配额列表（不可为空）
     * @return {@code 0} 表示成功放行并已扣减；大于 0 的数值表示因超限被拦截，该数值为至少需要等待的冷却毫秒数
     * @throws org.springframework.dao.DataAccessException 若底层 Redis 连接断开或操作超时
     */
    public long tryAcquire(List<Quota> quotas) {
        List<String> keys = new ArrayList<>(quotas.size());
        List<String> args = new ArrayList<>(quotas.size() * 2 + 1);
        args.add(UUID.randomUUID().toString());
        for (Quota quota : quotas) {
            keys.add(KEY_PREFIX + quota.key());
            args.add(String.valueOf(quota.limit()));
            args.add(String.valueOf(quota.window().toMillis()));
        }
        return redis.execute(ACQUIRE, keys, args.toArray());
    }

    /**
     * 单个滑动窗口限流配额定义 Record。
     *
     * @param key    配额在 Redis 中的后缀键标识（如 {@code article:publish:user:10001}）
     * @param limit  滑动窗口内允许的最大请求上限
     * @param window 滑动窗口时间跨度
     */
    public record Quota(String key, int limit, Duration window) {
    }
}
