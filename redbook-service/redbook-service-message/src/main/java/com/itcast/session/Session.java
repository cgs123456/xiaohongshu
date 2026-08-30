package com.itcast.session;

import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentHashMap;

public class Session {

    private static final ConcurrentHashMap<Integer, Channel> userIdChannelMap = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Channel, Integer> channelUserIdMap = new ConcurrentHashMap<>();

    public static void bind(Integer userId, Channel channel) {
        // 旧channel存在先关闭再放新channel，避免旧连接残留导致通知发送到失效通道
        Channel oldChannel = userIdChannelMap.put(userId, channel);
        if (oldChannel != null && oldChannel != channel) {
            oldChannel.close();
        }
        channelUserIdMap.put(channel, userId);
    }

    public static void unbind(Integer userId, Channel channel) {
        // 条件删除：仅当map中当前映射的仍是该channel时才移除，防止误删新连接的绑定
        userIdChannelMap.remove(userId, channel);
        channelUserIdMap.remove(channel);
    }

    public static Integer getUserId(Channel channel) {
        return channelUserIdMap.get(channel);
    }

    public static Channel getChannel(Integer userId) {
        return userIdChannelMap.get(userId);
    }
}
