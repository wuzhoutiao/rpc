package com.rpc.server.handler;

import com.alibaba.fastjson.JSONObject;
import com.rpc.api.protocol.HeartbeatMessage;
import com.rpc.api.protocol.Request;
import com.rpc.api.protocol.Response;
import com.rpc.server.medium.RpcRequestDispatcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

public class RpcServerHandler extends ChannelInboundHandlerAdapter {
    /* 请求进入 pipeline 后，由业务 EventExecutorGroup 执行反序列化、反射调用和响应写回。 */

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String message = msg.toString();

        // 心跳消息不进入 RPC 反序列化和业务线程池
        if (HeartbeatMessage.PONG.equals(message)) {
            System.out.println("收到客户端心跳 pong");
            return;
        }
        if (HeartbeatMessage.PING.equals(message)) {
            ctx.writeAndFlush(
                    HeartbeatMessage.PONG + HeartbeatMessage.LINE_DELIMITER
            );
            return;
        }

        System.out.println("服务器收到消息：" + message);
        Request request = JSONObject.parseObject(message, Request.class);
        System.out.println("正在处理请求，id=" + request.getId() + ", command=" + request.getCommand());
        Response response = RpcRequestDispatcher.newInstance().process(request);
        ctx.writeAndFlush(JSONObject.toJSONString(response) + "\r\n");
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            if (state == IdleState.WRITER_IDLE) {
                ctx.writeAndFlush(
                        HeartbeatMessage.PING + HeartbeatMessage.LINE_DELIMITER
                );
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
