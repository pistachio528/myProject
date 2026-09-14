package com.hmdp.service;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 发送手机验证码
     *
     * @param phone 手机号
     * @return 验证码（测试环境直接返回，方便自动化测试）
     */
    String sendCode(String phone);

    /**
     * 手机号+验证码登录
     *
     * @param params   包含 phone 和 code 的请求参数
     * @param response HTTP 响应（用于写入 token header）
     */
    void login(Map<String, String> params, HttpServletResponse response);
}
