package com.hmdp.controller;

import com.hmdp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 用户控制器（手机号+验证码登录）
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 发送验证码
     * POST /api/user/code?phone=xxx
     */
    @PostMapping("/code")
    public String sendCode(@RequestParam String phone) {
        // 返回验证码，方便测试环境自动化（生产环境应去掉返回值）
        return userService.sendCode(phone);
    }

    /**
     * 登录
     * POST /api/user/login
     * Body: { "phone": "xxx", "code": "xxx" }
     */
    @PostMapping("/login")
    public String login(@RequestBody Map<String, String> params, HttpServletResponse response) {
        userService.login(params, response);
        return "登录成功";
    }
}
