package com.echocyan.codenest.framework.auth;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.echocyan.codenest.framework.web.WebMvcConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由认证拦截配置。
 *
 * <p>默认拦截 {@link WebMvcConfig#API_PREFIX} 下的所有接口并校验登录态（公开接口标注 {@link cn.dev33.satoken.annotation.SaIgnore} 豁免）。
 * 仅对命中的 {@link HandlerMethod} 校验，未匹配路由交由框架返回 404。</p>
 */
@Configuration(proxyBeanMethods = false)
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册全局 Sa-Token 路由认证拦截器。
     *
     * @param registry Spring Web 拦截器注册中心
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 仅在命中 Controller 处理方法时校验登录态；不存在的路由放行给底层处理，返回 404 而非 401
        registry.addInterceptor(new SaInterceptor(handler -> {
            if (handler instanceof HandlerMethod) {
                StpUtil.checkLogin();
            }
        })).addPathPatterns(WebMvcConfig.API_PREFIX + "/**");
    }
}
