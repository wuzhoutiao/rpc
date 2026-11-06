package com.rpc.client.handler;

import com.rpc.api.protocol.HeartbeatMessage;
import com.rpc.api.protocol.Response;
import com.alibaba.fastjson.JSONObject;
import com.rpc.client.core.ResultFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class RpcClientHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String message = msg.toString();

        if (HeartbeatMessage.PING.equals(message)) {
            System.out.println("收到服务端心跳 ping，回复 pong");
            ctx.writeAndFlush(
                    HeartbeatMessage.PONG + HeartbeatMessage.LINE_DELIMITER
            );
            return;
        }

        Response response = JSONObject.parseObject(message, Response.class);
        System.out.println("请求 id =" + response.getId() + "的请求，已收到服务端回复");
        System.out.println("回复为:" + JSONObject.toJSONString(response));
        ResultFuture.receive(response);
    }
}
