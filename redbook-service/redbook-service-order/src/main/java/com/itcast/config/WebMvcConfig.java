package com.itcast.config;

import com.itcast.interceptor.BaseInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将拦截器添加到容器中
 */
@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("注册自定义拦截器");
        registry.addInterceptor(new BaseInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/user/send/**", "/user/verify")
                // 支付网关异步回调：外部渠道服务器调用，无登录态/内部令牌，安全靠渠道验签
                .excludePathPatterns("/order/pay/notify");
    }
}
