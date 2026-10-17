package com.echocyan.codenest.social.service;

import com.echocyan.codenest.article.api.ArticleState;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Feed 动态流收件箱与发件箱存储管理组件。
 * <p>
 * 基于 Redis ZSet 实现，member 与 score 均为文章 ID：
 * <ul>
 *   <li><b>发件箱（Outbox）：</b>键为 {@code {prefix}:outbox:{authorId}}，维护作者最近发布的 {@value #OUTBOX_CAP} 篇文章，无 TTL。</li>
 *   <li><b>收件箱（Inbox）：</b>键为 {@code {prefix}:inbox:{userId}}，维护推送给读者的普通作者文章，上限 {@value #INBOX_CAP} 篇，
 *       TTL 为 7 天并在读取时自动续期；键不存在表示该读者超过 7 天未活跃，写扩散推送时跳过，读 Feed 时懒加载重建。</li>
 * </ul>
 * 键 {@code {prefix}:outbox:ready} 标识发件箱全量初始化状态。<br>
 * <b>雪花 ID 浮点精度保护：</b>Redis ZSet score 为双精度浮点数，超过 2^53 的雪花 ID 转换可能发生舍入。
 * 相同 score 的 member 按字典序排序（定长数值的字典序等价于数值序）；按游标翻页时，对与游标 score 相同的一组文章在 Java 内存中做严格数值比较过滤。
 */
class FeedBoxes {

    /**
     * 发件箱保留的最近文章容量上限。
     */
    static final int OUTBOX_CAP = 100;

    /**
     * 收件箱保留的文章容量上限。
     */
    static final int INBOX_CAP = 500;

    /**
     * 活跃用户收件箱存活时间。
     */
    private static final Duration INBOX_TTL = Duration.ofDays(7);

    /**
     * Lua 片段：按 score 升序裁剪 ZSet，仅保留最新的 ARGV[1] 条记录。
     */
    private static final String TRIM = """
            redis.call('ZREMRANGEBYRANK', KEYS[1], 0, -tonumber(ARGV[1]) - 1)
            """;

    /**
     * Lua 片段：将源发件箱文章合并到目标 ZSet 并修剪容量。
     */
    private static final String MERGE = """
            local function merge(src)
                local items = redis.call('ZRANGE', src, 0, -1, 'WITHSCORES')
                if #items == 0 then
                    return
                end
                local args = {}
                for i = 1, #items, 2 do
                    args[#args + 1] = items[i + 1]
                    args[#args + 1] = items[i]
                end
                redis.call('ZADD', KEYS[1], unpack(args))
            """ + TRIM + """
            end
            """;

    /**
     * Lua 脚本：添加文章到 ZSet 并裁剪至上限。
     */
    private static final RedisScript<Long> ADD = RedisScript.of("""
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[2])
            """ + TRIM + """
            return 1
            """, Long.class);

    private static final byte[] ADD_BYTES = ADD.getScriptAsString().getBytes(StandardCharsets.UTF_8);

    /**
     * Lua 脚本：当收件箱存在时添加文章并修剪（未活跃用户跳过，避免创建无 TTL 的收件箱）。
     */
    private static final byte[] ADD_IF_PRESENT = ("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[2])
            """ + TRIM + """
            return 1
            """).getBytes(StandardCharsets.UTF_8);

    /**
     * Lua 脚本：当收件箱存在时将发件箱文章并入。
     */
    private static final RedisScript<Long> MERGE_IF_PRESENT = RedisScript.of(MERGE + """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            merge(KEYS[2])
            return 1
            """, Long.class);

    private static final byte[] MERGE_IF_PRESENT_BYTES =
            MERGE_IF_PRESENT.getScriptAsString().getBytes(StandardCharsets.UTF_8);

    /**
     * Lua 脚本：将多个发件箱文章合并到收件箱并设置过期时间。
     */
    private static final RedisScript<Long> REBUILD = RedisScript.of(MERGE + """
            for i = 2, #KEYS do
                merge(KEYS[i])
            end
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    /**
     * Lua 脚本：从收件箱中移除发件箱里的全部文章。
     */
    private static final RedisScript<Long> REMOVE_ALL_OF = RedisScript.of("""
            local ids = redis.call('ZRANGE', KEYS[2], 0, -1)
            if #ids == 0 then
                return 0
            end
            return redis.call('ZREM', KEYS[1], unpack(ids))
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final String outboxReadyKey;

    FeedBoxes(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
        this.outboxReadyKey = keyPrefix + ":outbox:ready";
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String outboxKey(long authorId) {
        return keyPrefix + ":outbox:" + authorId;
    }

    private String inboxKey(long userId) {
        return keyPrefix + ":inbox:" + userId;
    }

    /**
     * 将单篇文章写入作者发件箱并裁剪至容量上限。
     *
     * @param authorId  作者用户 ID
     * @param articleId 文章 ID
     */
    void addToOutbox(long authorId, long articleId) {
        redis.execute(ADD, List.of(outboxKey(authorId)), String.valueOf(OUTBOX_CAP), String.valueOf(articleId));
    }

    /**
     * 使用 Pipeline 将一批文章批量写入各自作者的发件箱。
     *
     * @param articles 包含作者 ID 与文章 ID 的状态列表
     */
    void addToOutboxes(Collection<ArticleState> articles) {
        byte[] cap = bytes(String.valueOf(OUTBOX_CAP));
        redis.executePipelined((RedisCallback<Object>) connection -> {
            articles.forEach(article -> connection.scriptingCommands()
                    .eval(ADD_BYTES, ReturnType.INTEGER, 1, bytes(outboxKey(article.authorId())), cap,
                            bytes(String.valueOf(article.id()))));
            return null;
        });
    }

    /**
     * 检查发件箱全量初始化就绪标记是否存在。
     *
     * @return 若已初始化完成返回 {@code true}，否则返回 {@code false}
     */
    boolean outboxesReady() {
        return Boolean.TRUE.equals(redis.hasKey(outboxReadyKey));
    }

    /**
     * 写入发件箱全量初始化完成标记。
     */
    void markOutboxesReady() {
        redis.opsForValue().set(outboxReadyKey, "1");
    }

    /**
     * 从指定作者的发件箱中移除单篇文章。
     *
     * @param authorId  作者用户 ID
     * @param articleId 文章 ID
     */
    void removeFromOutbox(long authorId, long articleId) {
        redis.opsForZSet().remove(outboxKey(authorId), String.valueOf(articleId));
    }

    /**
     * 使用 Pipeline 将文章批量推送到活跃粉丝的收件箱中（收件箱不存在的跳过）。
     *
     * @param userIds   粉丝用户 ID 集合
     * @param articleId 文章 ID
     */
    void pushToInboxes(Collection<Long> userIds, long articleId) {
        byte[] id = bytes(String.valueOf(articleId));
        byte[] cap = bytes(String.valueOf(INBOX_CAP));
        redis.executePipelined((RedisCallback<Object>) connection -> {
            userIds.forEach(userId -> connection.scriptingCommands()
                    .eval(ADD_IF_PRESENT, ReturnType.INTEGER, 1, bytes(inboxKey(userId)), cap, id));
            return null;
        });
    }

    /**
     * 当读者收件箱存在时，将指定作者发件箱中的文章合并入其收件箱。
     *
     * @param userId   读者用户 ID
     * @param authorId 作者用户 ID
     */
    void mergeOutboxIntoInbox(long userId, long authorId) {
        redis.execute(MERGE_IF_PRESENT, List.of(inboxKey(userId), outboxKey(authorId)), String.valueOf(INBOX_CAP));
    }

    /**
     * 使用 Pipeline 将作者发件箱文章批量合并入多个活跃读者的收件箱中。
     *
     * @param userIds  读者用户 ID 集合
     * @param authorId 作者用户 ID
     */
    void mergeOutboxIntoInboxes(Collection<Long> userIds, long authorId) {
        byte[] outbox = bytes(outboxKey(authorId));
        byte[] cap = bytes(String.valueOf(INBOX_CAP));
        redis.executePipelined((RedisCallback<Object>) connection -> {
            userIds.forEach(userId -> connection.scriptingCommands()
                    .eval(MERGE_IF_PRESENT_BYTES, ReturnType.INTEGER, 2, bytes(inboxKey(userId)), outbox, cap));
            return null;
        });
    }

    /**
     * 从读者收件箱中移除指定作者发件箱中的全部文章。
     *
     * @param userId   读者用户 ID
     * @param authorId 作者用户 ID
     */
    void removeOutboxFromInbox(long userId, long authorId) {
        redis.execute(REMOVE_ALL_OF, List.of(inboxKey(userId), outboxKey(authorId)));
    }

    /**
     * 读者收件箱存在时刷新过期时间；不存在时从关注的普通作者发件箱懒加载合并重建。
     *
     * @param userId    读者用户 ID
     * @param authorIds 关注的普通作者 ID 集合
     */
    void renewOrRebuildInbox(long userId, Collection<Long> authorIds) {
        String inbox = inboxKey(userId);
        if (Boolean.TRUE.equals(redis.expire(inbox, INBOX_TTL)) || authorIds.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>(List.of(inbox));
        authorIds.forEach(authorId -> keys.add(outboxKey(authorId)));
        redis.execute(REBUILD, keys, String.valueOf(INBOX_CAP), String.valueOf(INBOX_TTL.toSeconds()));
    }

    /**
     * 使用 Pipeline 并行读取读者收件箱与关注大 V 发件箱中早于游标的文章 ID 列表。
     * <p>
     * 针对雪花 ID 转换为 double score 造成的浮点精度舍入，对相同 score 的文章区间取出后在 Java 内存做精确严格数值过滤。
     *
     * @param userId    读者用户 ID
     * @param authorIds 关注的大 V 作者 ID 集合
     * @param cursor    游标文章 ID（为 {@code null} 时从最新文章开始）
     * @param limit     各箱抓取的单箱上限数量
     * @return 聚合后的文章 ID 列表（未排序、未去重）
     */
    List<Long> readBefore(long userId, Collection<Long> authorIds, Long cursor, int limit) {
        List<byte[]> keys = Stream.concat(Stream.of(inboxKey(userId)), authorIds.stream().map(this::outboxKey))
                .map(FeedBoxes::bytes)
                .toList();
        List<Object> replies = redis.executePipelined((RedisCallback<Object>) connection -> {
            RedisZSetCommands zSet = connection.zSetCommands();
            for (byte[] key : keys) {
                if (cursor == null) {
                    zSet.zRevRange(key, 0, limit - 1);
                } else {
                    // 与游标 score 相同的一组可能有比游标大的 ID，取出后再精确过滤；其余的 score 更小，ID 一定小于游标
                    double score = cursor;
                    zSet.zRangeByScore(key, score, score);
                    zSet.zRevRangeByScore(key, Range.leftUnbounded(Range.Bound.exclusive(score)),
                            Limit.limit().count(limit));
                }
            }
            return null;
        });
        List<Long> ids = new ArrayList<>();
        for (Object reply : replies) {
            for (Object member : (Set<?>) reply) {
                long id = Long.parseLong((String) member);
                if (cursor == null || id < cursor) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }
}
