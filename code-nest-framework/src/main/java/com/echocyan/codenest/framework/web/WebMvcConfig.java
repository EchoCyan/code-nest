package com.echocyan.codenest.framework.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 全局 Web 通用行为配置类。
 *
 * <p>本类负责统一配置全站 HTTP 端点的路径映射规则与上下文前缀：
 * <ul>
 *   <li><b>全局路由前缀收敛：</b>为全站业务接口统一添加 {@value #API_PREFIX}（即 {@code /api/v1}）前缀；</li>
 *   <li><b>第三方框架端点隔离：</b>通过 {@link HandlerTypePredicate} 精确限定前缀仅作用于
 *       {@code com.echocyan.codenest} 根包下且标注了 {@link RestController} 的业务控制器类，
 *       确保 SpringDoc OpenAPI 文档端点（{@code /v3/api-docs}、{@code /swagger-ui/**}）
 *       以及 Spring Boot Actuator 监控端点（{@code /actuator/**}）保持原生标准路径，不受业务前缀干扰。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 业务 REST 接口的全局统一切入前缀。
     */
    public static final String API_PREFIX = "/api/v1";

    /**
     * 项目业务代码根包名，用于路由匹配过滤条件。
     */
    private static final String BASE_PACKAGE = "com.echocyan.codenest";

    /**
     * 配置 URL 路径匹配规则，为业务 REST 控制器挂载统一版本前缀。
     *
     * @param configurer 路径匹配配置器
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 仅对当前项目基础包下的业务 RestController 追加 API_PREFIX 前缀，第三方框架端点保持原生路径
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forBasePackage(BASE_PACKAGE)
                .and(HandlerTypePredicate.forAnnotation(RestController.class)));
    }
}
