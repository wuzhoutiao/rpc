package client.core;

import client.constants.Constants;
import client.handler.MyClientChannelInitializer;
import client.protocol.ClientRequest;
import client.protocol.Response;
import client.zk.ServerWatcher;
import client.zk.ZooKeeperFactory;
import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;

import java.util.List;


public class NettyClient {
    public static final Bootstrap b = new Bootstrap();

    private static ChannelFuture f = null;

    static {
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            b.group(worker)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new MyClientChannelInitializer());

            CuratorFramework client = ZooKeeperFactory.getClient(); //获取 Curator 封装好的 ZooKeeper 客户端对象
            List<String> serverPath = client.getChildren().forPath(Constants.SERVER_PATH); //获取ZooKeeper 的 Constants.SERVER_PATH 节点下面的所有子节点
            CuratorWatcher watcher = new ServerWatcher();
            client.getChildren().usingWatcher(watcher).forPath(Constants.SERVER_PATH); //监听Constants.SERVER_PATH 节点下面所有的子节点变化

            for(String path : serverPath){
                String[] str = path.split("#");
                ChannelManager.realServerPath.add(str[0]+"#"+str[1]);
                ChannelFuture channelFuture = NettyClient.b.connect(str[0], Integer.valueOf(str[1])).sync();
                ChannelManager.addChannelFuture(channelFuture);
            }

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static Response send(ClientRequest request){
        ChannelFuture channelFuture = ChannelManager.get(ChannelManager.position);
        channelFuture.channel().writeAndFlush(JSONObject.toJSONString(request)+"\r\n");
        ResultFuture resultFuture = new ResultFuture(request);
        return resultFuture.get();
    }
}
