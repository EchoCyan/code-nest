package com.echocyan.codenest.search.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.ratelimit.RateLimit;
import com.echocyan.codenest.search.dto.SearchSort;
import com.echocyan.codenest.search.service.SearchService;
import com.echocyan.codenest.search.vo.SearchArticleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章搜索控制器。
 * <p>
 * 提供已发布文章的全文检索接口，支持关键词分词匹配、分类与标签筛选、多维排序与分页。
 */
@Tag(name = "搜索")
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 全文检索已发布文章。
     *
     * @param q          搜索关键词（不能为空或纯空格）
     * @param categoryId 分类 ID 筛选，为 {@code null} 时不限分类
     * @param tagId      标签 ID 筛选，为 {@code null} 时不限标签
     * @param sort       排序方式，默认为 {@link SearchSort#RELEVANCE}
     * @param page       页码（从 1 开始）
     * @param size       每页大小（范围 1-50，默认 20）
     * @return 分页检索结果，包含文章基础信息及关键词高亮
     */
    @SaIgnore
    @Operation(summary = "搜索文章", description = "只搜已发布文章；可按分类、标签筛选；page × size 超过 1000（即 from + size > 1000）时返回 400")
    @RateLimit("search-per-minute")
    @GetMapping("/search/articles")
    public Result<PageResult<SearchArticleVO>> search(
            @RequestParam @NotBlank String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "RELEVANCE") SearchSort sort,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) long size) {
        return Result.ok(searchService.search(q, categoryId, tagId, sort, page, size));
    }
}
