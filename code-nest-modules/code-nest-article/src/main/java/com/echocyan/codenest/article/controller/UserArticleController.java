package com.echocyan.codenest.article.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.article.api.ArticleItem;
import com.echocyan.codenest.article.service.ArticleReader;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户文章控制器。
 */
@Tag(name = "文章")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserArticleController {

    private final ArticleReader articleReader;

    /**
     * 基于游标分页查询当前登录用户的个人草稿箱，按文章 ID 降序排列。
     *
     * @param cursor 游标偏移量（上一页最后一条文章的 ID）；可选
     * @param size   单页记录数（默认 20，区间 1 ~ 50）
     * @return 草稿列表游标分页数据
     */
    @Operation(summary = "我的草稿", description = "按文章 ID 倒序；cursor 为上一页返回的 nextCursor")
    @GetMapping("/me/drafts")
    public Result<CursorResult<ArticleItem>> myDrafts(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(articleReader.listDrafts(AuthContext.currentUserId(), cursor, size));
    }

    /**
     * 基于游标分页查询指定作者名下已发布的有效文章列表，按文章 ID 降序排列。
     *
     * @param id     作者用户 ID
     * @param cursor 游标偏移量（上一页最后一条文章的 ID）；可选
     * @param size   单页记录数（默认 20，区间 1 ~ 50）
     * @return 作者已发布文章游标分页数据
     */
    @SaIgnore
    @Operation(summary = "作者的文章", description = "只含已发布文章，按文章 ID 倒序；cursor 为上一页返回的 nextCursor")
    @GetMapping("/{id}/articles")
    public Result<CursorResult<ArticleItem>> published(
            @PathVariable long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(articleReader.listPublishedByAuthor(id, cursor, size));
    }
}
