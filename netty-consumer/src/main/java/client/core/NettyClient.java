package client.core;

import client.constants.Constants;
import client.handler.MyClientChannelInitializer;
import client.protocol.ClientRequest;
import client.protocol.Response;
import client.zk.ServerWatcher;
import client.zk.ZooKeeperFactory;
import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class NettyClient {
    public static final Bootstrap b = new Bootstrap();

    static {
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            b.group(worker)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new MyClientChannelInitializer());

            CuratorFramework client = ZooKeeperFactory.getClient(); //获取 Curator 封装好的 ZooKeeper 客户端对象
            List<String> serverPath = client.getChildren().usingWatcher(new ServerWatcher()).forPath(Constants.SERVER_PATH); //获取ZooKeeper 的 Constants.SERVER_PATH 节点下面的所有子节点

            Set<String> servers = new HashSet<>();
            for(String path : serverPath){
                String[] str = path.split("#");
                servers.add(str[0]+":"+str[1]);
            }

            ChannelManager.updateChannels(servers,b);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static Response send(ClientRequest request){
        Channel channel = ChannelManager.getChannel(); //轮询选择一个可用连接
        ResultFuture resultFuture = new ResultFuture(request);// 先注册响应等待对象，避免响应先于注册到达
        channel.writeAndFlush(JSONObject.toJSONString(request)+"\r\n");//发送rpc请求
        return resultFuture.get();
    }
}
