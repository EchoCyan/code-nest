package com.echocyan.codenest.framework.openapi;

import cn.dev33.satoken.annotation.SaIgnore;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 接口文档配置。
 *
 * <p>配置 API 元数据、Bearer Token 认证机制，并根据 {@link SaIgnore} 动态标记受保护接口。</p>
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /**
     * 全局 Bearer HTTP 认证方案的组件标识名称。
     */
    private static final String BEARER = "bearer";

    /**
     * 构建并注入 OpenAPI 全局元数据及通用安全模式组件。
     *
     * @return 初始化的 {@link OpenAPI} 根对象
     */
    @Bean
    public OpenAPI codeNestOpenApi() {
        return new OpenAPI().info(
                        new Info().title("码巢 code-nest API").description("开发者技术社区后端接口").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description(
                                        "登录或注册返回的 token。请求头格式为 " + "`Authorization: Bearer " + "<token>`，"
                                                + "缺少 `Bearer ` 前缀会被视为未登录。")));
    }

    /**
     * 构建接口操作定制器，动态标注需要认证登录的 API 端点。
     *
     * <p>严格与 {@link cn.dev33.satoken.interceptor.SaInterceptor} 保持一致的判定逻辑：
     * 仅当处理方法本身以及所在的 Controller 类均未标注 {@link SaIgnore} 时，
     * 才在文档的对应接口操作上挂载 {@value #BEARER} 安全依赖项。
     *
     * @return 用于处理单个操作元数据的 {@link OperationCustomizer} 实例
     */
    @Bean
    public OperationCustomizer loginRequirementCustomizer() {
        return (operation, handlerMethod) -> {
            boolean ignored = handlerMethod.hasMethodAnnotation(SaIgnore.class) || handlerMethod.getBeanType()
                    .isAnnotationPresent(SaIgnore.class);
            return ignored ? operation : operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
        };
    }
}
