package com.itcast.filter;


import com.auth0.jwt.exceptions.JWTVerificationException;
import com.itcast.enums.ResponseEnum;
import com.itcast.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局过滤器
 */
@Slf4j
@Component
public class MyGlobalFilter implements GlobalFilter, Ordered {

    /** 精确匹配的白名单路径（避免 startsWith 误放行 /user/sendXXX 之类端点） */
    private static final java.util.Set<String> EXACT_WHITELIST =
            java.util.Set.of("/user/send", "/user/verify", "/note/getNoteList",
                    "/order/pay/notify", "/product/getProductList");

    /** 前缀匹配的白名单：游客可浏览商城（只读），加购/下单仍走认证 */
    private static final java.util.Set<String> PREFIX_WHITELIST =
            java.util.Set.of("/product/getProduct/", "/product/getSkus/");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1.获取请求和响应对象
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // 2.判断是否是免认证接口
        String path = request.getURI().getPath();
        log.info("请求接口为：{}", path);
        // WebSocket 握手放行：浏览器 WS API 无法携带自定义 header，
        // 认证由 message 服务 WebSocketHandler 在首条消息中校验 JWT 完成
        if (isWhitelisted(path) || path.startsWith("/ws/")) {
            log.info("请求接口放行");
            return chain.filter(exchange);
        }

        // 3.获取token
        String token = request.getHeaders().getFirst("token");

        // 4.判断token是否为空
        if (StringUtils.isBlank(token)) {
            response.setStatusCode(HttpStatus.valueOf(ResponseEnum.UNAUTHORIZED.getCode()));
            return response.setComplete();
        }

        // 5.判断token是否过期
        try {
            String userId = JwtUtil.parseToken(token);

            // 6.先移除客户端伪造的同名头，再写入真实userId，并用返回值重建exchange
            ServerHttpRequest newRequest = request.mutate()
                    .headers(h -> h.remove("userId"))
                    .header("userId", userId)
                    .build();
            return chain.filter(exchange.mutate().request(newRequest).build());
        } catch (JWTVerificationException e2) {
            // 覆盖 TokenExpired / SignatureVerification / JWTDecode / InvalidClaim 等所有校验失败
            log.warn("token 校验失败，path={}，reason={}", path, e2.getMessage());
            response.setStatusCode(HttpStatus.valueOf(ResponseEnum.UNAUTHORIZED.getCode()));
            return response.setComplete();
        }
    }

    /** 前缀白名单只匹配带路径变量的只读端点（如 /product/getProduct/1），尾斜杠界定避免误放行 */
    private boolean isWhitelisted(String path) {
        if (EXACT_WHITELIST.contains(path)) {
            return true;
        }
        for (String prefix : PREFIX_WHITELIST) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        // 早于限流等路由过滤器执行，保证 userId 头先注入（供 userPathKeyResolver 使用）
        return -100;
    }
}
