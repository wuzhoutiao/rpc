package com.rpc.server.handler;

import com.rpc.api.protocol.HeartbeatMessage;
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
        ch.pipeline().addLast(new IdleStateHandler(
                HeartbeatMessage.READER_IDLE_SECONDS,
                HeartbeatMessage.WRITER_IDLE_SECONDS,
                0,
                TimeUnit.SECONDS
        ));
        // 心跳留在 I/O 线程处理，避免耗时 RPC 业务阻塞探活和超时关闭
        ch.pipeline().addLast(new RpcServerHeartbeatHandler());
        ch.pipeline().addLast(businessGroup, new RpcServerHandler());//业务逻辑处理处
    }
}
