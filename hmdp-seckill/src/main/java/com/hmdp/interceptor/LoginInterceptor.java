package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * 登录拦截器
 * 从请求 Header 中取 token，校验并将 userId 存入 ThreadLocal
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private static final String TOKEN_KEY_PREFIX = "login:token:";
    private static final long TOKEN_TTL_MINUTES = 30L;

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        String userIdStr = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (!StringUtils.hasText(userIdStr)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 续期 token
        redisTemplate.expire(TOKEN_KEY_PREFIX + token, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        // 存入 ThreadLocal
        UserHolder.setUserId(Long.parseLong(userIdStr));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束后清除 ThreadLocal，防止内存泄漏
        UserHolder.removeUserId();
    }
}
