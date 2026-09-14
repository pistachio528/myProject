package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** 验证码 Redis key 前缀 */
    private static final String CODE_KEY_PREFIX = "login:code:";
    /** 登录 token Redis key 前缀 */
    private static final String TOKEN_KEY_PREFIX = "login:token:";
    /** 验证码 TTL（分钟） */
    private static final long CODE_TTL_MINUTES = 2L;
    /** Token TTL（分钟） */
    private static final long TOKEN_TTL_MINUTES = 30L;

    private final StringRedisTemplate redisTemplate;

    @Override
    public String sendCode(String phone) {
        // 生成 6 位随机验证码
        String code = RandomUtil.randomNumbers(6);
        // 存入 Redis，TTL = 2 分钟
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + phone, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        log.info("【模拟短信】手机号 {} 验证码：{}", phone, code);
        // 直接返回验证码，方便测试环境自动化
        return code;
    }

    @Override
    public void login(Map<String, String> params, HttpServletResponse response) {
        String phone = params.get("phone");
        String code = params.get("code");

        // 校验验证码
        String cachedCode = redisTemplate.opsForValue().get(CODE_KEY_PREFIX + phone);
        if (cachedCode == null || !cachedCode.equals(code)) {
            throw new RuntimeException("验证码错误或已过期");
        }

        // 查询或创建用户
        User user = getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickName("用户_" + RandomUtil.randomString(6));
            save(user);
        }

        // 生成 token，存入 Redis，TTL = 30 分钟
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token,
                String.valueOf(user.getId()), TOKEN_TTL_MINUTES, TimeUnit.MINUTES);

        // 将 token 写入响应 header
        response.setHeader("Authorization", token);
        log.info("用户 {} 登录成功，token={}", phone, token);
    }
}
