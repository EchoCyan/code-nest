package com.echocyan.codenest.search.controller;

import com.echocyan.codenest.search.service.ArticleIndex;
import com.echocyan.codenest.search.service.ArticleIndex.RebuildResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 搜索索引全量重建管理端点。
 * <p>
 * 仅在 Actuator 管理端口上暴露（{@code POST /actuator/search-rebuild}），用于运维手动触发零停机索引重建。
 */
@Component
@Endpoint(id = "search-rebuild")
@RequiredArgsConstructor
class SearchRebuildEndpoint {

    private final ArticleIndex articleIndex;

    /**
     * 同步触发搜索索引重建。
     *
     * @return 响应结果：200 成功，返回新索引版本及导入/追补文章数；409 冲突，表示已有重建任务正在运行
     */
    @WriteOperation
    public WebEndpointResponse<RebuildResult> rebuild() {
        return articleIndex.rebuild()
                .map(WebEndpointResponse::new)
                .orElseGet(() -> new WebEndpointResponse<>(HttpStatus.CONFLICT.value()));
    }
}
