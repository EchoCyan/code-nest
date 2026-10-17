package com.echocyan.codenest.user.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.user.dto.UpdateProfileRequest;
import com.echocyan.codenest.user.service.UserService;
import com.echocyan.codenest.user.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料控制器。
 * <p>
 * 提供当前登录用户个人资料查询与修改，以及公开用户主页资料查询等端点。
 */
@Tag(name = "用户")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 查询当前登录用户的个人资料。
     *
     * @return 当前用户资料与统计计数
     */
    @Operation(summary = "我的资料")
    @GetMapping("/me")
    public Result<UserProfileVO> me() {
        return Result.ok(userService.getProfile(AuthContext.currentUserId()));
    }

    /**
     * 修改当前登录用户的个人资料。
     * <p>
     * 整体替换昵称、头像及个人简介，未传递字段置空；用户名不可变更。
     *
     * @param request 资料更新请求体
     * @return 成功响应
     */
    @Operation(summary = "修改我的资料", description = "整体替换昵称、头像、简介；用户名不可修改")
    @PutMapping("/me")
    public Result<Void> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(AuthContext.currentUserId(), request);
        return Result.ok();
    }

    /**
     * 查询指定用户的公开主页资料。
     * <p>
     * 无需登录即可访问，返回基本资料与各项统计计数。
     *
     * @param id 目标用户 ID
     * @return 用户资料与统计计数
     */
    @SaIgnore
    @Operation(summary = "用户主页", description = "资料与粉丝数、关注数、文章数、获赞数")
    @GetMapping("/{id}")
    public Result<UserProfileVO> get(@PathVariable long id) {
        return Result.ok(userService.getProfile(id));
    }
}
