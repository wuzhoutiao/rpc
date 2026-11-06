package com.rpc.server.handler;

import com.rpc.api.protocol.HeartbeatMessage;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * 在 I/O 线程中处理服务端心跳，心跳帧不会进入 RPC 业务线程池。
 */
public class RpcServerHeartbeatHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String message = msg.toString();

        if (HeartbeatMessage.PING.equals(message)) {
            System.out.println("收到客户端心跳 ping，回复 pong");
            ctx.writeAndFlush(
                    HeartbeatMessage.PONG + HeartbeatMessage.LINE_DELIMITER
            ).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            return;
        }
        if (HeartbeatMessage.PONG.equals(message)) {
            System.out.println("收到客户端心跳 pong");
            return;
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            if (state == IdleState.WRITER_IDLE) {
                ctx.writeAndFlush(
                        HeartbeatMessage.PING + HeartbeatMessage.LINE_DELIMITER
                ).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                return;
            }
            if (state == IdleState.READER_IDLE) {
                System.out.println("客户端心跳超时，关闭连接：" + ctx.channel().remoteAddress());
                ctx.close();
                return;
            }
        }
        ctx.fireUserEventTriggered(evt);
    }
}
