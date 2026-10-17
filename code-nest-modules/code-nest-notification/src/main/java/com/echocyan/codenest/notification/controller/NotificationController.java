package com.echocyan.codenest.notification.controller;

import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.notification.service.NotificationService;
import com.echocyan.codenest.notification.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知控制器。
 * <p>
 * 提供当前用户的站内通知分页列表查询、未读数获取以及单条/全部已读标记等 REST 接口。
 */
@Tag(name = "通知")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 游标分页查询当前用户的通知列表。
     *
     * @param cursor 上一页返回的下一页游标（首次查询为 {@code null}）
     * @param size   分页大小（默认 20，最小 1，最大 50）
     * @return 包含通知列表及下一页游标的统一响应体
     */
    @Operation(summary = "我的通知", description = "按时间倒序；cursor 为上一页返回的 nextCursor；打开列表不会标记已读")
    @GetMapping
    public Result<CursorResult<NotificationVO>> listMine(@RequestParam(required = false) Long cursor,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(notificationService.listMine(AuthContext.currentUserId(), cursor, size));
    }

    /**
     * 查询当前用户的未读通知数。
     *
     * @return 未读通知数统一响应体（最多返回 {@value NotificationService#MAX_UNREAD_COUNT}，前端据此展示 "99+"）
     */
    @Operation(summary = "未读数", description = "最多数到 " + NotificationService.MAX_UNREAD_COUNT + "，前端显示为 99+")
    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        return Result.ok(notificationService.countUnread(AuthContext.currentUserId()));
    }

    /**
     * 标记指定通知为已读。
     *
     * @param id 通知 ID
     * @return 成功响应体
     */
    @Operation(summary = "标记单条已读", description = "只能标记自己的通知，否则按不存在处理")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable long id) {
        notificationService.markRead(AuthContext.currentUserId(), id);
        return Result.ok();
    }

    /**
     * 批量标记当前用户的全部未读通知为已读。
     *
     * @return 成功响应体
     */
    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(AuthContext.currentUserId());
        return Result.ok();
    }
}
