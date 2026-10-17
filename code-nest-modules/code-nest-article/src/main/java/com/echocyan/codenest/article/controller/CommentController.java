package com.echocyan.codenest.article.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.article.dto.CommentRequest;
import com.echocyan.codenest.article.dto.ReplyRequest;
import com.echocyan.codenest.article.service.CommentService;
import com.echocyan.codenest.article.vo.CommentIdVO;
import com.echocyan.codenest.article.vo.CommentVO;
import com.echocyan.codenest.article.vo.ReplyVO;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.framework.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论控制器。
 */
@Tag(name = "评论")
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 对已发布文章发表一级根评论。
     *
     * @param id      文章 ID
     * @param request 评论参数
     * @return 包含新评论 ID 的响应
     */
    @Operation(summary = "发表评论", description = "只能评论已发布的文章")
    @RateLimit("comment-per-minute")
    @RateLimit("comment-per-day")
    @PostMapping("/articles/{id}/comments")
    public Result<CommentIdVO> comment(@PathVariable long id, @Valid @RequestBody CommentRequest request) {
        return Result.ok(
                new CommentIdVO(commentService.comment(id, AuthContext.currentUserId(), request.content()).getId()));
    }

    /**
     * 基于游标分页查询文章下的一级评论列表，按时间倒序排列。
     *
     * @param id     文章 ID
     * @param cursor 游标偏移量（上一页最后一条评论的 ID）；可选
     * @param size   单页记录数（默认 20，区间 1 ~ 50）
     * @return 一级评论游标分页数据
     */
    @SaIgnore
    @Operation(summary = "评论列表", description = "按时间倒序，每条带回复数，不内嵌回复")
    @GetMapping("/articles/{id}/comments")
    public Result<CursorResult<CommentVO>> listComments(@PathVariable long id,
                                                        @RequestParam(required = false) Long cursor,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(commentService.listComments(id, cursor, size));
    }

    /**
     * 发表二级楼中楼回复。
     *
     * @param id      被回复的根评论 ID 或已有回复 ID
     * @param request 回复参数
     * @return 包含新回复 ID 的响应
     */
    @Operation(summary = "发表回复", description = "id 可以是评论或回复；对回复再回复时，新回复仍挂在同一条评论下。"
            + "replyToUserId 须是该评论或同一评论下某条回复的作者")
    @RateLimit("comment-per-minute")
    @RateLimit("comment-per-day")
    @PostMapping("/comments/{id}/replies")
    public Result<CommentIdVO> reply(@PathVariable long id, @Valid @RequestBody ReplyRequest request) {
        return Result.ok(new CommentIdVO(
                commentService.reply(id, AuthContext.currentUserId(), request.content(), request.replyToUserId())
                        .getId()));
    }

    /**
     * 基于游标分页查询指定根评论下的二级回复列表，按时间正序排列。
     *
     * @param id     根评论 ID
     * @param cursor 游标偏移量（上一页最后一条回复的 ID）；可选
     * @param size   单页记录数（默认 20，区间 1 ~ 50）
     * @return 二级回复游标分页数据
     */
    @SaIgnore
    @Operation(summary = "回复列表", description = "平铺，按时间正序，每条带被回复人的简要信息")
    @GetMapping("/comments/{id}/replies")
    public Result<CursorResult<ReplyVO>> listReplies(@PathVariable long id,
                                                     @RequestParam(required = false) Long cursor,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(commentService.listReplies(id, cursor, size));
    }

    /**
     * 软删除评论或回复。
     *
     * @param id 评论或回复 ID
     * @return 操作成功响应
     */
    @Operation(summary = "删除评论或回复", description = "只能删除自己的；评论下的回复保留")
    @DeleteMapping("/comments/{id}")
    public Result<Void> delete(@PathVariable long id) {
        commentService.delete(id, AuthContext.currentUserId());
        return Result.ok();
    }
}
