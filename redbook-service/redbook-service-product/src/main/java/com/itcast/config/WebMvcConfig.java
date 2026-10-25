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
        registry.addInterceptor(new BaseInterceptor()).addPathPatterns("/**")
                // 游客可浏览商城：商品只读接口匿名放行（网关前缀白名单对应），
                // 加购/下单/评论等写操作仍需登录
                .excludePathPatterns("/product/getProductList", "/product/getProduct/*", "/product/getSkus/*");
    }
}
