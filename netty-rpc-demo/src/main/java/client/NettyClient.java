package client;

import com.alibaba.fastjson.JSONObject;
import constants.Constants;
import future.ResultFuture;
import handler.MyNettyClientInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import protocol.ClientRequest;
import protocol.Response;

public class NettyClient {
    private static ChannelFuture f = null; //此时为空值，说明连接还不存在

    static{ //NettyClient类第一次加载时，自动建立并保存一个全局TCP连接,整个客户端连接只建立一次
        NioEventLoopGroup work = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(work).channel(NioSocketChannel.class).handler(new MyNettyClientInitializer());

            f = bootstrap.connect("127.0.0.1", Constants.SERVER_PORT).sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static Response send(ClientRequest request){
        f.channel().writeAndFlush(JSONObject.toJSONString(request)+"\r\n");
        ResultFuture future = new ResultFuture(request);
        return future.get();
    }
}
