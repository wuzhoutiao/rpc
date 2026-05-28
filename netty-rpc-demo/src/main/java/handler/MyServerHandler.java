package handler;

import com.alibaba.fastjson.JSONObject;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import medium.Medium;
import protocol.Response;
import protocol.ServerRequest;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MyServerHandler extends ChannelInboundHandlerAdapter {
    /*请求进来 -> work线程读取 socket 数据 -> 进入 pipeline -> ServerHandler.channelRead()
    -> 把业务逻辑封装成 Runnable -> exec.execute(task) ->业务线程池执行 method.invoke()
    -> 处理完后再 writeAndFlush(response)*/

    private static final Executor exec = Executors.newFixedThreadPool(10);//业务线程池

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("服务器收到消息：" + msg.toString());
        exec.execute(new Runnable() {
            @Override
            public void run() {
                ServerRequest serverRequest = JSONObject.parseObject(msg.toString(), ServerRequest.class);
                System.out.println("正在处理请求，id="+serverRequest.getId() + ", command=" + serverRequest.getCommand());
                Medium medium =  Medium.newInstance();

                Response response = medium.process(serverRequest);

                ctx.channel().writeAndFlush(JSONObject.toJSONString(response)+"\r\n");
            }
        });
    }
}
