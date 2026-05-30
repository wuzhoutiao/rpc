package client.handler;

import client.core.ResultFuture;
import client.protocol.Response;
import com.alibaba.fastjson.JSONObject;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MyClientHandler extends ChannelInboundHandlerAdapter {
    private static final Executor exec = Executors.newFixedThreadPool(10);//业务线程池

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        final Object m = msg; //在 Java 里，匿名内部类访问外部方法的局部变量时，这个变量必须是 final 或者事实 final
        if(msg.toString().equals("ping")){
            System.out.println("收到服务器读写空闲ping,向服务器发生pong");
            ctx.channel().writeAndFlush("pong\r\n");
        }

        exec.execute(new Runnable() {
            @Override
            public void run() {
                Response response = JSONObject.parseObject(m.toString(), Response.class);
                System.out.println("请求 id ="+ response.getId() + "的请求，已收到服务端回复");
                System.out.println("回复为:"+JSONObject.toJSONString(response));
                ResultFuture.receive(response);
            }
        });
    }
}
