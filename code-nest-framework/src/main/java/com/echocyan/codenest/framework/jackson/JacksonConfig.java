package com.echocyan.codenest.framework.jackson;

import com.echocyan.codenest.common.util.DateTimes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 全局 JSON 序列化与反序列化配置。
 *
 * <p>特性：包装类型 {@link Long} 序列化为字符串防前端精度丢失；
 * {@link LocalDateTime} 采用 ISO-8601 带时区偏移格式输出并支持宽容反序列化。</p>
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    /**
     * 注册项目专用的 Jackson 定制模块。
     *
     * @return 包含 Long 字符串化与 LocalDateTime 时区序列化器的 {@link SimpleModule}
     */
    @Bean
    public SimpleModule codeNestJacksonModule() {
        SimpleModule module = new SimpleModule("code-nest");
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(LocalDateTime.class, new OffsetLocalDateTimeSerializer());
        module.addDeserializer(LocalDateTime.class, new OffsetLocalDateTimeDeserializer());
        return module;
    }

    /**
     * 将本地时间 {@link LocalDateTime} 序列化为带时区偏移的 ISO-8601 字符串。
     */
    static class OffsetLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {

        /**
         * 执行时间序列化。
         *
         * @param value 待序列化的本地时间对象
         * @param gen   JSON 输出生成器
         * @param ctxt  序列化上下文
         */
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(
                    value.atZone(DateTimes.ZONE).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    /**
     * 将 ISO-8601 格式时间字符串反序列化为本地时间 {@link LocalDateTime}。
     *
     * <p>优先按带时区偏移字符串（如 {@code 2026-10-17T17:00:00+08:00}）解析并转换为全站统一时区时刻；
     * 若字符串不带时区偏移，则直接按本地无时区时间格式回退解析。
     */
    static class OffsetLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

        /**
         * 执行时间反序列化。
         *
         * @param p    JSON 解析器
         * @param ctxt 反序列化上下文
         * @return 转换后的 {@link LocalDateTime} 对象；若入参为空白文本则返回 {@code null}
         */
        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return OffsetDateTime.parse(text).atZoneSameInstant(DateTimes.ZONE).toLocalDateTime();
            } catch (DateTimeParseException e) {
                // 不带时区偏移时直接按全站基准时区理解
                return LocalDateTime.parse(text);
            }
        }
    }
}
