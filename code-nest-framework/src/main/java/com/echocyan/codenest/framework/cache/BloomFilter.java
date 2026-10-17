package com.echocyan.codenest.framework.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 基于 Redis 原生布隆过滤器的存在性预检组件（防缓存穿透）。
 *
 * <p>核心机制：
 * <ul>
 *   <li><b>Fail-Open：</b>未就绪（无 {@code ready} 标记）或键不存在时默认放行，防重建期误杀；</li>
 *   <li><b>容量与误判：</b>默认容量 100 万，误判率 0.1%，超出后由 Redis 自动扩容；</li>
 *   <li><b>使用约定：</b>新增数据调用 {@link #add(long)}，启动期通过 {@link #rebuildIfAbsent(Function)} 预热。</li>
 * </ul>
 * </p>
 */
@Slf4j
public class BloomFilter {

    /**
     * 预期误判率（0.1%），即允许千分之一的假阳性概率。
     */
    private static final String ERROR_RATE = "0.001";

    /**
     * 初始预估容量（100 万条）。超出该容量后，Redis 8 会自动追加子过滤器进行无缝扩容。
     */
    private static final String CAPACITY = "1000000";

    /**
     * 元素添加 Lua 脚本。
     *
     * <p>KEYS[1] 为过滤器键，KEYS[2] 为就绪标记键；ARGV[1] 为误判率，ARGV[2] 为初始容量，ARGV[3...] 为待加入的 ID 列表。
     * 若过滤器不存在，会先清除旧的就绪标记，再以指定参数初始化（{@code BF.RESERVE}），确保运行期重建状态一致。
     */
    private static final RedisScript<Long> ADD = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[2])
                redis.call('BF.RESERVE', KEYS[1], ARGV[1], ARGV[2])
            end
            redis.call('BF.MADD', KEYS[1], unpack(ARGV, 3))
            return 1
            """, Long.class);

    /**
     * 元素存在性检查 Lua 脚本。
     *
     * <p>KEYS[1] 为过滤器键，KEYS[2] 为就绪标记键；ARGV[1] 为目标 ID。
     * 若过滤器键或就绪标记键存在缺失（存在数 < 2），则安全返回 1（视作可能存在），不执行拦截；
     * 否则执行 {@code BF.EXISTS} 进行精准判决。
     */
    private static final RedisScript<Long> MIGHT_CONTAIN = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1], KEYS[2]) < 2 then
                return 1
            end
            return redis.call('BF.EXISTS', KEYS[1], ARGV[1])
            """, Long.class);

    /**
     * 布隆过滤器在 Redis 中的主键名（{@code bf:<name>}）。
     */
    private final String key;

    /**
     * 全量数据导入就绪标记键名（{@code bf:<name>:ready}）。
     */
    private final String readyKey;

    /**
     * Redis 字符串操作模板。
     */
    private final StringRedisTemplate redis;

    /**
     * 构造布隆过滤器实例。
     *
     * @param name  过滤器业务名称（全局唯一，建议格式为 {@code <模块>:<实体>}）
     * @param redis Redis 字符串客户端模板
     */
    BloomFilter(String name, StringRedisTemplate redis) {
        this.key = "bf:" + name;
        this.readyKey = key + ":ready";
        this.redis = redis;
    }

    /**
     * 向布隆过滤器中记录一个新生成的业务 ID。
     *
     * <p>通常在实体落库的本地数据库事务内直接调用。若事务随后回滚，仅会在过滤器中残留该 ID 的假阳性记录，
     * 绝不影响业务数据的正确性与一致性。
     *
     * @param id 业务主键 ID
     */
    public void add(long id) {
        addAll(List.of(id));
    }

    /**
     * 当布隆过滤器或就绪标记不存在时，按游标分批全量导入数据并写入就绪标记；若已就绪则跳过。
     *
     * <p>多节点并发启动时各自执行导入具备幂等性；方法执行完毕后原子写入就绪标记，
     * 标志该布隆过滤器正式生效进入拦截状态。
     *
     * @param idsAfter 游标批次数据提取函数。入参为上一批最后一条记录的 ID（首次遍历传入 {@code null}）；
     *                 要求按 ID 升序提取下一批记录，返回空列表时表示遍历结束
     */
    public void rebuildIfAbsent(Function<Long, List<Long>> idsAfter) {
        if (redis.countExistingKeys(List.of(key, readyKey)) == 2) {
            return;
        }
        long imported = 0;
        List<Long> ids = idsAfter.apply(null);
        while (!ids.isEmpty()) {
            addAll(ids);
            imported += ids.size();
            ids = idsAfter.apply(ids.getLast());
        }
        redis.opsForValue().set(readyKey, "1");
        log.info("Rebuilt bloom filter {} with {} ids", key, imported);
    }

    /**
     * 判断指定 ID 是否可能存在于集合中。
     *
     * @param id 待校验的业务主键 ID
     * @return {@code true} 表示可能存在（或处于未就绪状态安全放行）；{@code false} 表示该 ID 一定不存在，可直接拦截
     */
    boolean mightContain(long id) {
        return redis.execute(MIGHT_CONTAIN, List.of(key, readyKey), String.valueOf(id)) == 1;
    }

    /**
     * 批量向布隆过滤器追加 ID 列表。
     *
     * @param ids 待加入的 ID 集合
     */
    private void addAll(List<Long> ids) {
        List<String> args = new ArrayList<>(List.of(ERROR_RATE, CAPACITY));
        ids.forEach(id -> args.add(String.valueOf(id)));
        redis.execute(ADD, List.of(key, readyKey), args.toArray());
    }
}
