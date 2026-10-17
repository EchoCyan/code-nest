package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * 已确认发件箱记录异步批量状态标记器。
 *
 * <p>收到 RabbitMQ Publisher Confirm ACK 后将消息 ID 暂存至内存无锁队列，
 * 由后台定时任务高频批量更新数据库为 {@link OutboxStatus#SENT}，降低高并发下的写库开销；
 * 容器销毁（{@link PreDestroy}）时同步刷盘残留记录。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OutboxSentMarker {

    /**
     * 单次批量执行状态更新的最大条目数（500 条）。
     */
    static final int BATCH_SIZE = 500;

    /**
     * 已确认消息 ID 的线程安全待刷盘内存缓冲队列。
     */
    private final Queue<Long> pending = new ConcurrentLinkedQueue<>();

    /**
     * 发件箱数据访问 Mapper。
     */
    private final MqOutboxMapper outboxMapper;

    /**
     * 登记一条已获得 Publisher Confirm 确认的消息 ID。
     *
     * <p>直接在 AMQP 客户端网络连接回调线程上执行，仅向内存并发队列追加元素，绝不产生数据库 I/O 阻塞。
     *
     * @param id 已获确认的发件箱记录主键 ID
     */
    void markSent(long id) {
        pending.add(id);
    }

    /**
     * 定时将内存缓冲队列中的已确认消息批量更新至数据库。
     *
     * <p>固定延迟 100 毫秒执行一次，按批次取出队列中积累的全部 ID，分批执行批量更新。
     */
    @Scheduled(fixedDelay = 100, initialDelay = 100, timeUnit = TimeUnit.MILLISECONDS)
    public void flush() {
        List<Long> batch = new ArrayList<>(BATCH_SIZE);
        Long id;
        while ((id = pending.poll()) != null) {
            batch.add(id);
            if (batch.size() == BATCH_SIZE) {
                update(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            update(batch);
        }
    }

    /**
     * 容器销毁前置钩子，在应用停机前强制将队列剩余的所有待确认标记刷盘。
     */
    @PreDestroy
    void flushOnShutdown() {
        flush();
    }

    /**
     * 执行单批次已确认记录的状态批量更新 SQL。
     *
     * @param ids 待更新的发件箱主键 ID 批次列表
     */
    private void update(List<Long> ids) {
        try {
            ChainWrappers.lambdaUpdateChain(outboxMapper)
                    .in(MqOutbox::getId, ids)
                    .eq(MqOutbox::getStatus, OutboxStatus.PENDING)
                    .set(MqOutbox::getStatus, OutboxStatus.SENT)
                    .update(new MqOutbox());
        } catch (RuntimeException e) {
            // 标记失败只会导致后续被补发任务重新投递，不在此重试，避免失败时队列无界堆积导致 OOM
            log.warn("Failed to mark {} outbox events as sent, they will be relayed again", ids.size(), e);
        }
    }
}
