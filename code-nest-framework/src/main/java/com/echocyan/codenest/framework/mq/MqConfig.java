package com.echocyan.codenest.framework.mq;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * RabbitMQ 消息可靠性基础设施配置。
 *
 * <p>配置交换机、JSON 转换器、死信处理与监听器容器工厂，
 * 并在启动时校验 Publisher Confirm/Returns 等可靠性前置配置。</p>
 */
@Slf4j
@EnableScheduling
@Configuration(proxyBeanMethods = false)
public class MqConfig {

    /**
     * 构造配置类并立即执行 RabbitMQ 可靠性环境强校验。
     *
     * @param rabbitProperties Spring Boot 绑定的 RabbitMQ 配置属性对象
     */
    MqConfig(RabbitProperties rabbitProperties) {
        requireReliabilitySettings(rabbitProperties);
    }

    /**
     * 校验 RabbitMQ 客户端配置是否满足可靠消息投递的强制性技术前置要求。
     *
     * <p>必须同时满足如下四项配置：
     * <ol>
     *   <li>{@code spring.rabbitmq.publisher-confirm-type=correlated}：开启关联发布确认。若缺失，发送端将永远无法收到 Broker ACK，导致 Outbox
     *   全部堆积并最终标记为 FAILED；</li>
     *   <li>{@code spring.rabbitmq.publisher-returns=true}：开启投递失败退回；</li>
     *   <li>{@code spring.rabbitmq.template.mandatory=true}：开启 Mandatory 强制路由标识。与 returns 配合，确保当消息路由键无任何绑定队列时由
     *   Broker 显式退回，防止静默丢弃；</li>
     *   <li>{@code spring.rabbitmq.listener.simple.retry.enabled=true}：开启消费端本地自动重试。若缺失，单次消费异常将直接丢弃或死循环重入队。</li>
     * </ol>
     *
     * @param properties 待检验的 RabbitMQ 配置属性
     * @throws IllegalStateException 若上述四项关键配置中有任意一项未满足规范
     */
    static void requireReliabilitySettings(RabbitProperties properties) {
        List<String> missing = new ArrayList<>();
        if (properties.getPublisherConfirmType() != CachingConnectionFactory.ConfirmType.CORRELATED) {
            missing.add("spring.rabbitmq.publisher-confirm-type=correlated");
        }
        if (!properties.isPublisherReturns()) {
            missing.add("spring.rabbitmq.publisher-returns=true");
        }
        if (!Boolean.TRUE.equals(properties.getTemplate().getMandatory())) {
            missing.add("spring.rabbitmq.template.mandatory=true");
        }
        if (!properties.getListener().getSimple().getRetry().isEnabled()) {
            missing.add("spring.rabbitmq.listener.simple.retry.enabled=true");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Reliable messaging requires " + String.join(", ", missing));
        }
    }

    /**
     * 注册全局持久化 Topic 领域事件交换机 Bean。
     *
     * @return 统一领域事件交换机 {@link TopicExchange}
     */
    @Bean
    public TopicExchange eventExchange() {
        return EventQueues.exchange();
    }

    /**
     * 注册全局持久化 Direct 死信交换机 Bean。
     *
     * @return 统一死信交换机 {@link DirectExchange}
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return EventQueues.deadLetterExchange();
    }

    /**
     * 注册面向领域事件的 JSON 报文消息转换器 Bean。
     *
     * <p>与 Web 表现层共享统一的 Jackson 配置（雪花 ID 字符串化、时区规整），
     * 并绑定 {@link DomainEventClassMapper} 进行类型白名单安全反序列化。
     *
     * @param jsonMapper 全局定制配置的 Jackson 映射器
     * @return 领域事件专用 {@link MessageConverter}
     */
    @Bean
    public MessageConverter eventMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.setClassMapper(new DomainEventClassMapper());
        return converter;
    }

    /**
     * 注册重试耗尽后的死信隔离恢复器 Bean。
     *
     * <p>当消费端本地重试次数耗尽后，记录 ERROR 级别错误日志，
     * 抛出 {@link AmqpRejectAndDontRequeueException} 拒绝消息且不重新放回原队列，
     * 促使 RabbitMQ Broker 依据主队列的死信配置（x-dead-letter-*）自动将消息路由至专属死信队列。
     *
     * @return 死信消息恢复处理器 {@link MessageRecoverer}
     */
    @Bean
    public MessageRecoverer deadLetterRecoverer() {
        return (message, cause) -> {
            MessageProperties properties = message.getMessageProperties();
            log.error("Retries exhausted, moving message to dead letter queue: queue={}, messageId={}, type={}",
                    properties.getConsumerQueue(), properties.getMessageId(), properties.getType(), cause);
            throw new AmqpRejectAndDontRequeueException("Retries exhausted", cause);
        };
    }

    /**
     * 注册定制化的 RabbitMQ 监听器容器工厂 Bean。
     *
     * <p>在由 Spring Boot 自动装配好的标准 Advice 拦截链（包含本地重试拦截器）最外层，
     * 前置插入 {@link IdempotentConsumerAspect#bindConsumingMessage()} 拦截器，
     * 确保进入监听器或触发重试时当前线程均能安全获取底层原始消息上下文。
     *
     * @param configurer        Spring Boot 自动装配配置器
     * @param connectionFactory RabbitMQ 物理连接工厂
     * @return 组装完成的容器工厂实例
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        List<Advice> adviceChain = new ArrayList<>();
        adviceChain.add(IdempotentConsumerAspect.bindConsumingMessage());
        if (factory.getAdviceChain() != null) {
            adviceChain.addAll(List.of(factory.getAdviceChain()));
        }
        factory.setAdviceChain(adviceChain.toArray(Advice[]::new));
        return factory;
    }
}
