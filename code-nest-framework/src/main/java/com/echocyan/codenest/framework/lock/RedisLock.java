package com.echocyan.codenest.framework.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 基于 Redis 的轻量级分布式互斥锁（SET NX + Lua 校验释放）。
 *
 * <p>适用于后台定时任务等单点互斥场景。采用随机 UUID 令牌校验属主防误删，
 * 不设看门狗自动续期，业务需合理设定 TTL。</p>
 */
@Component
@RequiredArgsConstructor
public class RedisLock {

    /**
     * 校验属主并原子释放锁的 Lua 脚本。
     *
     * <p>KEYS[1] 为分布式锁 Key；ARGV[1] 为当前线程加锁时写入的专属 Token。
     * 仅当 Key 中的值与 ARGV[1] 完全匹配时才执行 {@code DEL} 删除，防止误删他人的锁；返回 1 表示释放成功，0 表示已被抢占或过期。
     */
    private static final RedisScript<Long> UNLOCK = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    /**
     * Redis 字符串操作模板。
     */
    private final StringRedisTemplate redis;

    /**
     * 尝试获取分布式互斥锁，抢锁成功则同步执行带返回值的任务并在执行完毕后自动释放锁。
     *
     * @param <T>  任务返回结果的泛型类型
     * @param key  分布式锁在 Redis 中的完整键名
     * @param ttl  锁的租期时效（超出该时间后锁将自动释放，需预估任务最大可能耗时）
     * @param task 抢锁成功后待执行的业务闭包（返回值不可为 {@code null}）
     * @return 包装任务执行结果的 {@link Optional}；若锁已被其他实例占用则立即返回 {@link Optional#empty()} 且不执行任务
     * @throws RuntimeException 若任务执行过程中抛出异常，将在 finally 释放锁之后原样向上抛出
     */
    public <T> Optional<T> tryRun(String key, Duration ttl, Supplier<T> task) {
        String token = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, ttl))) {
            return Optional.empty();
        }
        try {
            return Optional.of(task.get());
        } finally {
            redis.execute(UNLOCK, List.of(key), token);
        }
    }

    /**
     * 尝试获取分布式互斥锁，抢锁成功则同步执行无返回值的任务并在执行完毕后自动释放锁。
     *
     * @param key  分布式锁在 Redis 中的完整键名
     * @param ttl  锁的租期时效
     * @param task 抢锁成功后待执行的无返回值业务逻辑
     * @return {@code true} 表示成功获取到锁并完整执行了任务；{@code false} 表示锁已被占用，任务未执行
     * @throws RuntimeException 若任务执行过程中抛出异常，将在 finally 释放锁之后原样向上抛出
     */
    public boolean tryRun(String key, Duration ttl, Runnable task) {
        return tryRun(key, ttl, () -> {
            task.run();
            return true;
        }).isPresent();
    }
}
