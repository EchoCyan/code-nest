package com.echocyan.codenest.counter.controller;

import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.service.CounterReconcileService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 计数对账 Actuator 运维端点。
 *
 * <p>暴露为 {@code POST /actuator/counter-reconcile}，仅在内部管理端口提供，用于手动触发全量对账。
 */
@Component
@Endpoint(id = "counter-reconcile")
@RequiredArgsConstructor
class CounterReconcileEndpoint {

    private final CounterReconcileService counterReconcileService;

    /**
     * 手动触发同步全量对账。
     *
     * @return 响应包含对账结果；若成功获取锁并完成对账返回 HTTP 200 及各指标修正数，若已有对账在执行返回 HTTP 409
     */
    @WriteOperation
    public WebEndpointResponse<Map<CounterMetric, Long>> reconcile() {
        return counterReconcileService.reconcile()
                .map(WebEndpointResponse::new)
                .orElseGet(() -> new WebEndpointResponse<>(HttpStatus.CONFLICT.value()));
    }
}
