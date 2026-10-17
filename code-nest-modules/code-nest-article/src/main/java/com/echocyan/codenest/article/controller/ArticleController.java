package com.echocyan.codenest.article.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.article.api.ArticleItem;
import com.echocyan.codenest.article.dto.ArticleRequest;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.service.ArticleReader;
import com.echocyan.codenest.article.service.ArticleService;
import com.echocyan.codenest.article.vo.ArticleDetailVO;
import com.echocyan.codenest.article.vo.ArticleVersionVO;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.framework.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文章控制器。
 */
@Tag(name = "文章")
@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final ArticleReader articleReader;

    private static ArticleVersionVO versionOf(Article article) {
        return new ArticleVersionVO(article.getId(), article.getVersion());
    }

    /**
     * 新建文章草稿。
     *
     * @param request 草稿创建参数
     * @return 包含新文章 ID 与初始版本号的响应
     */
    @Operation(summary = "新建草稿")
    @PostMapping
    public Result<ArticleVersionVO> create(@Valid @RequestBody ArticleRequest request) {
        return Result.ok(versionOf(articleService.create(AuthContext.currentUserId(), request)));
    }

    /**
     * 分页查询最新发布的有效文章，支持分类与标签维度筛选。
     *
     * @param categoryId 分类筛选 ID；可选
     * @param tagId      标签筛选 ID；可选
     * @param page       页码（默认 1）
     * @param size       每页记录数（默认 20，区间 1 ~ 50）
     * @return 分页列表数据
     */
    @SaIgnore
    @Operation(summary = "最新文章", description = "只含已发布文章，按发布时间倒序；可按分类、标签筛选")
    @GetMapping
    public Result<PageResult<ArticleItem>> latest(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) long size) {
        return Result.ok(articleReader.pageLatest(categoryId, tagId, page, size));
    }

    /**
     * 全量更新文章内容与元数据。
     *
     * @param id      文章 ID
     * @param version 客户端读到的乐观锁版本号
     * @param request 文章更新参数
     * @return 包含最新版本号的响应
     */
    @Operation(summary = "编辑", description = "整体替换内容；version 为读到的版本号，已在别处修改时返回 409")
    @PutMapping("/{id}")
    public Result<ArticleVersionVO> update(@PathVariable long id, @RequestParam int version,
                                           @Valid @RequestBody ArticleRequest request) {
        return Result.ok(versionOf(articleService.update(id, AuthContext.currentUserId(), version, request)));
    }

    /**
     * 正式发布草稿文章。
     *
     * @param id 文章 ID
     * @return 包含最新版本号的响应
     */
    @Operation(summary = "发布", description = "已发布的文章重复发布不产生变化")
    @RateLimit("publish-per-hour")
    @PostMapping("/{id}/publish")
    public Result<ArticleVersionVO> publish(@PathVariable long id) {
        return Result.ok(versionOf(articleService.publish(id, AuthContext.currentUserId())));
    }

    /**
     * 软删除指定文章。
     *
     * @param id 文章 ID
     * @return 操作成功响应
     */
    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        articleService.delete(id, AuthContext.currentUserId());
        return Result.ok();
    }

    /**
     * 查询文章详情。
     *
     * @param id 文章 ID
     * @return 文章详情完整视图数据
     */
    @SaIgnore
    @Operation(summary = "文章详情", description = "草稿只有作者本人能看到，其他人得到 404")
    @GetMapping("/{id}")
    public Result<ArticleDetailVO> get(@PathVariable long id) {
        return Result.ok(articleReader.getDetail(id, AuthContext.currentUserIdOrNull()));
    }
}
