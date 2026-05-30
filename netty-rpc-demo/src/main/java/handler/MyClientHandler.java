package handler;

import com.alibaba.fastjson.JSONObject;
import future.ResultFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import protocol.Response;

public class MyClientHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Response response = JSONObject.parseObject(msg.toString(), Response.class);
        ResultFuture.receive(response); //通知该请求对应的future已经获得结果了，可以被唤醒了
    }
}
