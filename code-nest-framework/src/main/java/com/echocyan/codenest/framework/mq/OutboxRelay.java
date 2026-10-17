package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.echocyan.codenest.common.util.DateTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 事务发件箱（Outbox）补发调度器与数据归档器。
 *
 * <p>核心机制：
 * <ul>
 *   <li><b>并发抓取：</b>通过 {@code SKIP LOCKED} 批量锁定待补发记录，支持多实例并发无锁争用；</li>
 *   <li><b>指数退避：</b>重试间隔从 10 秒倍增至上限 30 分钟，最多重试 10 次，超限后置为 {@link OutboxStatus#FAILED}；</li>
 *   <li><b>超龄熔断：</b>创建超过 12 小时的积压消息不再补发，直接置为失败以防下游重复消费；</li>
 *   <li><b>定时归档：</b>定时分批物理删除保留超过 7 天的已发送记录（{@link OutboxStatus#SENT}）。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OutboxRelay {

    /**
     * 单条记录允许自动补发的最大重试次数（10 次）。
     */
    static final int MAX_RETRIES = 10;

    /**
     * 首次补发的等待延迟时间（10 秒）。
     */
    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(10);

    /**
     * 补发指数退避的单次最长等待上限（30 分钟）。
     */
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(30);

    /**
     * 处于 SENT 状态的发件箱历史记录在数据库中的最长保留周期（7 天）。
     */
    private static final Duration SENT_RETENTION = Duration.ofDays(7);

    /**
     * 允许补发的单条记录最长生命周期（12 小时）。
     *
     * <p>超过该时长的未完成消息直接熔断置为 FAILED；
     * 下游消费端若自建防重缓存，其过期时间必须严格长于此阈值（参见 {@link IdempotentConsumer}）。
     */
    static final Duration MAX_RELAY_AGE = Duration.ofHours(12);

    /**
     * 单次补发调度锁定的最大批次记录数（100 条）。
     */
    private static final int BATCH_SIZE = 100;

    /**
     * 单批物理删除已归档 SENT 记录的最大条目数（1000 条）。
     */
    private static final int PURGE_BATCH_SIZE = 1000;

    /**
     * 发件箱数据访问 Mapper。
     */
    private final MqOutboxMapper outboxMapper;

    /**
     * 底层 AMQP 消息发送执行器。
     */
    private final EventSender sender;

    /**
     * 事务执行模板。
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * 计算已失败指定次数的记录下次补发的时间戳。
     *
     * <p>算法公式为：{@code delay = min(FIRST_RETRY_DELAY * 2^retryCount, MAX_RETRY_DELAY)}。
     *
     * @param now        当前基准时间戳
     * @param retryCount 已经经历的重试失败次数
     * @return 计算得出的下次补发时间点 {@link LocalDateTime}
     */
    static LocalDateTime nextRetryAt(LocalDateTime now, int retryCount) {
        Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << Math.min(retryCount, 16));
        return now.plus(delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay);
    }

    /**
     * 阻塞等待当前批次所有投递 Future 的 Publisher Confirm 响应或超时。
     *
     * @param confirms 当前批次各记录的异步确认 Future 列表
     */
    private static void awaitAll(List<CompletableFuture<Boolean>> confirms) {
        try {
            CompletableFuture.allOf(confirms.toArray(CompletableFuture[]::new))
                    .get(DomainEventPublisher.CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            // 超时或个别异常时吞掉，后续逐条通过 isAcked 严格判定
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 校验单条消息的投递确认 Future 是否已成功且收到 Broker ACK。
     *
     * @param confirm 待检查的异步确认 Future
     * @return {@code true} 表示投递成功且已获确认；{@code false} 表示失败、NACK 或超时未完成
     */
    private static boolean isAcked(CompletableFuture<Boolean> confirm) {
        return confirm.state() == Future.State.SUCCESS && confirm.resultNow();
    }

    /**
     * 定时扫描到期未发送或发送未确认的 Outbox 记录，执行补发逻辑。
     *
     * <p>固定延迟 5 秒轮询，通过 {@code SKIP LOCKED} 实现多实例并发安全拉取；
     * 超龄消息置为 FAILED，其余有效消息批量推入 MQ 并统一等待确认。
     */
    @Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void relayDue() {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = DateTimes.now();
            List<MqOutbox> due = ChainWrappers.lambdaQueryChain(outboxMapper)
                    .eq(MqOutbox::getStatus, OutboxStatus.PENDING)
                    .le(MqOutbox::getNextRetryAt, now)
                    .orderByAsc(MqOutbox::getNextRetryAt)
                    .last("LIMIT " + BATCH_SIZE + " FOR UPDATE SKIP LOCKED")
                    .list();
            if (due.isEmpty()) {
                return;
            }
            LocalDateTime expiredBefore = now.minus(MAX_RELAY_AGE);
            List<MqOutbox> sendable = new ArrayList<>(due.size());
            for (MqOutbox outbox : due) {
                if (outbox.getCreatedAt().isBefore(expiredBefore)) {
                    outbox.setStatus(OutboxStatus.FAILED);
                    outboxMapper.updateById(outbox);
                    log.error("Outbox event is older than {} and will not be relayed, needs manual handling: "
                            + "id={}, routingKey={}", MAX_RELAY_AGE, outbox.getId(), outbox.getRoutingKey());
                } else {
                    sendable.add(outbox);
                }
            }
            // 先全部发出，再统一等待 confirm
            List<CompletableFuture<Boolean>> confirms = sendable.stream()
                    .map(outbox -> sender.send(String.valueOf(outbox.getId()), outbox.getRoutingKey(),
                            outbox.getEventType(), outbox.getPayload()))
                    .toList();
            awaitAll(confirms);
            for (int i = 0; i < sendable.size(); i++) {
                MqOutbox outbox = sendable.get(i);
                if (isAcked(confirms.get(i))) {
                    outbox.setStatus(OutboxStatus.SENT);
                } else {
                    recordFailure(outbox, now);
                }
                outboxMapper.updateById(outbox);
            }
        });
    }

    /**
     * 定时分批物理删除超过数据保留期的历史已发送（SENT）发件箱记录。
     *
     * <p>每小时整点执行，通过循环批量小事务删除，有效防范长事务锁表与大事务日志撑爆。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void purgeSent() {
        LocalDateTime before = DateTimes.now().minus(SENT_RETENTION);
        int deleted;
        do {
            deleted = outboxMapper.delete(Wrappers.lambdaQuery(MqOutbox.class)
                    .eq(MqOutbox::getStatus, OutboxStatus.SENT)
                    .lt(MqOutbox::getCreatedAt, before)
                    .last("LIMIT " + PURGE_BATCH_SIZE));
        } while (deleted == PURGE_BATCH_SIZE);
    }

    /**
     * 记录单条发件箱记录的投递失败状态并计算下一次补发时间。
     *
     * @param outbox 目标发件箱记录
     * @param now    当前系统时间戳
     */
    private void recordFailure(MqOutbox outbox, LocalDateTime now) {
        int retryCount = outbox.getRetryCount() + 1;
        outbox.setRetryCount(retryCount);
        if (retryCount >= MAX_RETRIES) {
            outbox.setStatus(OutboxStatus.FAILED);
            log.error("Outbox event failed {} times and needs manual handling: id={}, routingKey={}",
                    retryCount, outbox.getId(), outbox.getRoutingKey());
        } else {
            outbox.setNextRetryAt(nextRetryAt(now, retryCount));
            log.warn("Outbox event relay failed {} times: id={}, routingKey={}",
                    retryCount, outbox.getId(), outbox.getRoutingKey());
        }
    }
}
