package com.echocyan.codenest.user.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.framework.ratelimit.RateLimit;
import com.echocyan.codenest.user.dto.LoginRequest;
import com.echocyan.codenest.user.dto.RegisterRequest;
import com.echocyan.codenest.user.entity.User;
import com.echocyan.codenest.user.service.UserService;
import com.echocyan.codenest.user.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证控制器。
 * <p>
 * 提供用户注册、登录凭证校验及当前设备注销退出等端点。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 为指定用户建立登录态并封装登录结果。
     *
     * @param user 用户实体
     * @return 登录结果视图对象
     */
    private static LoginVO loginAs(User user) {
        return new LoginVO(user.getId(), AuthContext.login(user.getId()));
    }

    /**
     * 用户注册。
     * <p>
     * 注册成功后自动建立登录态并签发 Token。限制每小时注册频次。
     *
     * @param request 注册请求体
     * @return 登录结果
     */
    @SaIgnore
    @Operation(summary = "注册", description = "注册成功后自动登录")
    @RateLimit("register-per-hour")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(loginAs(userService.register(request.username(), request.password())));
    }

    /**
     * 用户登录。
     * <p>
     * 校验用户名与密码，成功后签发 Bearer Token。限制每分钟登录重试频次。
     *
     * @param request 登录请求体
     * @return 登录结果
     */
    @SaIgnore
    @Operation(summary = "登录", description = "之后的请求以 Authorization: Bearer <token> 携带凭证")
    @RateLimit("login-per-minute")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(loginAs(userService.authenticate(request.username(), request.password())));
    }

    /**
     * 退出登录。
     * <p>
     * 注销并失效当前设备的 Token 会话。
     *
     * @return 成功响应
     */
    @Operation(summary = "退出登录", description = "只让当前设备的 token 失效")
    @PostMapping("/logout")
    public Result<Void> logout() {
        AuthContext.logout();
        return Result.ok();
    }
}
