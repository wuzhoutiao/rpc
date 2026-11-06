package com.rpc.client.handler;

import com.rpc.api.protocol.HeartbeatMessage;
import com.rpc.api.protocol.Response;
import com.alibaba.fastjson.JSONObject;
import com.rpc.client.core.ResultFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

public class RpcClientHandler extends ChannelInboundHandlerAdapter {
    private static final boolean VERBOSE = Boolean.getBoolean("rpc.verbose");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String message = msg.toString();

        if (HeartbeatMessage.PING.equals(message)) {
            if (VERBOSE) System.out.println("收到服务端心跳 ping，回复 pong");
            ctx.writeAndFlush(
                    HeartbeatMessage.PONG + HeartbeatMessage.LINE_DELIMITER
            ).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            return;
        }
        if (HeartbeatMessage.PONG.equals(message)) {
            if (VERBOSE) System.out.println("收到服务端心跳 pong");
            return;
        }

        Response response = JSONObject.parseObject(message, Response.class);
        if (VERBOSE) {
            System.out.println("请求 id =" + response.getId() + "的请求，已收到服务端回复");
            System.out.println("回复为:" + JSONObject.toJSONString(response));
        }
        ResultFuture.receive(response);
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
                System.out.println("服务端心跳超时，关闭连接：" + ctx.channel().remoteAddress());
                ctx.close();
                return;
            }
        }
        ctx.fireUserEventTriggered(evt);
    }
}
