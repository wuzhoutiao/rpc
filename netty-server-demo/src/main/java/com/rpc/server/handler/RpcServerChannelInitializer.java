package com.rpc.server.handler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.EventExecutorGroup;

import java.util.concurrent.TimeUnit;

public class RpcServerChannelInitializer extends ChannelInitializer<NioSocketChannel> {

    private final EventExecutorGroup businessGroup;

    public RpcServerChannelInitializer(EventExecutorGroup businessGroup) {
        this.businessGroup = businessGroup;
    }

    @Override
    protected void initChannel(NioSocketChannel ch) throws Exception {

        ch.pipeline().addLast(new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()[0])); // 设置\r\n为切割消息的分隔符
        ch.pipeline().addLast(new StringDecoder());//字符串解码器
        ch.pipeline().addLast(new StringEncoder());//字符串编码器，把字符串转换为字节
        // 15 秒没有写数据时发送 ping；45 秒没有收到客户端数据时关闭失活连接
        ch.pipeline().addLast(new IdleStateHandler(45, 15, 0, TimeUnit.SECONDS));
        ch.pipeline().addLast(businessGroup, new RpcServerHandler());//业务逻辑处理处
    }
}
