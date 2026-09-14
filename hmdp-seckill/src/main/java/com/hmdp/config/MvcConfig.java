package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：注册登录拦截器
 */
@Configuration
@RequiredArgsConstructor
public class MvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                // 排除用户登录相关接口，无需鉴权
                .excludePathPatterns("/api/user/**")
                // 排除优惠券列表接口，公开可访问
                .excludePathPatterns("/api/voucher/list/**")
                // 排除优惠券查询接口，供智能客服内部调用
                .excludePathPatterns("/api/voucher/**")
                // 排除订单状态查询接口，供智能客服内部调用（order/list 需要登录，不能排除）
                .excludePathPatterns("/api/seckill/order/status/**");
    }
}
