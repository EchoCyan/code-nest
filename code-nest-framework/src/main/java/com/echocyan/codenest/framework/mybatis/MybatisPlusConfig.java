package com.echocyan.codenest.framework.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 基础设施配置。
 *
 * <p>配置 Mapper 扫描路径，装配乐观锁与 MySQL 分页插件，注册审计字段自动填充处理器。</p>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.echocyan.codenest", annotationClass = Mapper.class)
public class MybatisPlusConfig {

    /**
     * 装配 MyBatis-Plus 拦截器插件链。
     *
     * <p>按顺序集成如下内部拦截器：
     * <ol>
     *   <li>{@link OptimisticLockerInnerInterceptor}：乐观锁拦截器。针对标注了
     *       {@link com.baomidou.mybatisplus.annotation.Version} 的实体字段（如文章版本号），
     *       在执行更新时自动将版本号追加为 SQL WHERE 条件并在更新时递增，防止并发写覆盖冲突；</li>
     *   <li>{@link PaginationInnerInterceptor}：针对 MySQL 方言的物理分页拦截器。
     *       支持基于页码与页长的底层物理分页查询，自动优化 COUNT 统计语句。</li>
     * </ol>
     *
     * @return 配置好的 {@link MybatisPlusInterceptor} 拦截器容器 Bean
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 注册实体时间戳自动填充元数据处理器 Bean。
     *
     * @return {@link AuditMetaObjectHandler} 实例
     */
    @Bean
    public AuditMetaObjectHandler auditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }
}
