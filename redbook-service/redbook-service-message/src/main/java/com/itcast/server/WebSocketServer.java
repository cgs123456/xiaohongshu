package com.itcast.server;

import com.itcast.handler.WebSocketChannelHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * netty服务器
 */
@Component
@Slf4j
public class WebSocketServer {

    private final WebSocketChannelHandler handler;

    @Value("${netty.port:8888}")
    private int port;

    EventLoopGroup boosGroup = new NioEventLoopGroup(1);
    EventLoopGroup workGroup = new NioEventLoopGroup(10);

    public WebSocketServer(WebSocketChannelHandler handler) {
        this.handler = handler;
    }

    @PostConstruct
    public void run(){
        log.info("启动Netty服务器，端口：{}", port);
        new Thread(() -> {
            try{
                ServerBootstrap bootstrap = new ServerBootstrap();
                ChannelFuture future = bootstrap.group(boosGroup, workGroup)
                        .option(ChannelOption.SO_BACKLOG, 1024)
                        .channel(NioServerSocketChannel.class)
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .childHandler(handler)
                        .bind(port)
                        .sync();
                log.info("Netty WebSocket服务器启动成功，端口：{}", port);
                future.channel().closeFuture().sync();
            } catch (Exception e) {
                // bind 失败直接退出进程，避免服务"假活"但仍注册在 Nacos 上接收流量
                log.error("Netty服务器启动失败，进程退出：", e);
                System.exit(1);
            } finally {
                boosGroup.shutdownGracefully();
                workGroup.shutdownGracefully();
            }
        }, "netty-websocket-server").start();
    }

    @PreDestroy
    public void shutdown() {
        boosGroup.shutdownGracefully();
        workGroup.shutdownGracefully();
    }
}
