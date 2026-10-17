package com.echocyan.codenest.framework.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.UndeclaredThrowableException;

/**
 * {@link IdempotentConsumer} 消费幂等切面。
 *
 * <p>基于 {@code (queue, message_id)} 数据库唯一约束实现消息幂等消费：
 * 在事务中前置插入消费记录，捕获 {@link DuplicateKeyException} 忽略重复消息，
 * 保证下游消费方法至多执行一次。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
class IdempotentConsumerAspect {

    /**
     * 绑定当前线程正在消费的 AMQP 原始报文对象的 ThreadLocal 变量。
     */
    private static final ThreadLocal<Message> CONSUMING = new ThreadLocal<>();

    /**
     * 消费记录防重表持久化 Mapper。
     */
    private final MqConsumeRecordMapper consumeRecordMapper;

    /**
     * 事务执行模板，用于在独立事务中执行防重校验与业务消费。
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * 构建用于在 Spring AMQP 监听容器中绑定当前消息的 AOP Alliance 方法拦截器。
     *
     * <p>必须配置在容器本地重试 Advice 链的最外层，
     * 确保重试机制触发的每一次方法调用期间均能正确获取当前正在重试的 AMQP 原始报文。
     *
     * @return 消息线程绑定的 {@link MethodInterceptor} 实例
     */
    static MethodInterceptor bindConsumingMessage() {
        return invocation -> {
            if (!(invocation.getArguments()[1] instanceof Message message)) {
                return invocation.proceed();
            }
            CONSUMING.set(message);
            try {
                return invocation.proceed();
            } finally {
                CONSUMING.remove();
            }
        };
    }

    /**
     * 统一执行切入点目标方法并规整异常。
     *
     * @param joinPoint 环绕切点
     * @return 目标方法返回值
     * @throws RuntimeException 或 {@link Error} 原始异常向上抛出
     */
    private static Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    /**
     * 拦截标注了 {@link IdempotentConsumer} 的消息处理方法，执行事务性防重消费。
     *
     * @param joinPoint 切入点对象
     * @return 业务方法执行结果；若判定为重复消息则返回 {@code null}
     * @throws IllegalStateException 若未处于 AMQP 监听线程上下文中，或消息缺失必要的 {@code message_id}
     * @throws Throwable             业务执行异常，向上抛出以触发事务回滚及容器重试
     */
    @Around("@annotation(com.echocyan.codenest.framework.mq.IdempotentConsumer)")
    public Object consumeOnce(ProceedingJoinPoint joinPoint) {
        Message message = CONSUMING.get();
        if (message == null) {
            throw new IllegalStateException(
                    "@IdempotentConsumer must be used on a @RabbitListener method: " + joinPoint.getSignature());
        }
        MessageProperties properties = message.getMessageProperties();
        if (properties.getMessageId() == null) {
            throw new IllegalStateException("Message has no message_id, queue=" + properties.getConsumerQueue());
        }
        MqConsumeRecord record = new MqConsumeRecord();
        record.setMessageId(properties.getMessageId());
        record.setConsumer(properties.getConsumerQueue());
        return transactionTemplate.execute(status -> {
            try {
                consumeRecordMapper.insert(record);
            } catch (DuplicateKeyException e) {
                // 唯一索引冲突仅使当前插入语句报错，事务整体正常提交退出
                log.info("Skip consumed message: messageId={}, consumer={}", record.getMessageId(),
                        record.getConsumer());
                return null;
            }
            return proceed(joinPoint);
        });
    }
}
