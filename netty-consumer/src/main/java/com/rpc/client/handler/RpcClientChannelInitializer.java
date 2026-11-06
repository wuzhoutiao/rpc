package com.rpc.client.handler;

import com.rpc.api.protocol.HeartbeatMessage;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public class RpcClientChannelInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()[0]));
        ch.pipeline().addLast(new StringDecoder());//字符串解码器
        ch.pipeline().addLast(new StringEncoder());//字符串编码器
        // 客户端和服务端都主动探活：写空闲时 ping，读超时时断开并由 ChannelManager 重连
        ch.pipeline().addLast(new IdleStateHandler(
                HeartbeatMessage.READER_IDLE_SECONDS,
                HeartbeatMessage.WRITER_IDLE_SECONDS,
                0,
                TimeUnit.SECONDS
        ));
        ch.pipeline().addLast(new RpcClientHandler());//业务逻辑处理
    }
}
