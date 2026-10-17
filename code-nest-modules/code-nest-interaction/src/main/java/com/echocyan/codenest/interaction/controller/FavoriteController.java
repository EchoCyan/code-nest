package com.echocyan.codenest.interaction.controller;

import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.framework.ratelimit.RateLimit;
import com.echocyan.codenest.interaction.service.FavoriteService;
import com.echocyan.codenest.interaction.vo.FavoriteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文章收藏控制器。
 *
 * <p>提供文章收藏、取消收藏及当前用户收藏列表游标分页查询接口。
 */
@Tag(name = "收藏")
@RestController
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 收藏指定文章。
     *
     * @param id 目标文章 ID
     * @return 成功响应
     */
    @Operation(summary = "收藏", description = "已收藏过时不产生变化；只能收藏已发布的文章")
    @RateLimit("like-favorite-per-minute")
    @PutMapping("/articles/{id}/favorite")
    public Result<Void> favorite(@PathVariable long id) {
        favoriteService.favorite(AuthContext.currentUserId(), id);
        return Result.ok();
    }

    /**
     * 取消对指定文章的收藏。
     *
     * @param id 目标文章 ID
     * @return 成功响应
     */
    @Operation(summary = "取消收藏", description = "没收藏过时不产生变化")
    @RateLimit("like-favorite-per-minute")
    @DeleteMapping("/articles/{id}/favorite")
    public Result<Void> unfavorite(@PathVariable long id) {
        favoriteService.unfavorite(AuthContext.currentUserId(), id);
        return Result.ok();
    }

    /**
     * 分页查询当前登录用户的收藏文章列表。
     *
     * @param cursor 游标（上一页末尾记录的收藏 ID，首页传 null）
     * @param size   单页查询大小（范围 1~50，默认 20）
     * @return 收藏游标分页结果；已删除文章自动过滤
     */
    @Operation(summary = "我的收藏", description = "按收藏时间倒序；已删除的文章不出现，因此一页可能不足 size 条，以 hasMore 判断是否翻完")
    @GetMapping("/users/me/favorites")
    public Result<CursorResult<FavoriteVO>> listMine(@RequestParam(required = false) Long cursor,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(favoriteService.listMine(AuthContext.currentUserId(), cursor, size));
    }
}
