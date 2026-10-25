package client.core;

import client.constants.Constants;
import client.handler.MyClientChannelInitializer;
import client.protocol.ClientRequest;
import com.rpc.common.protocol.Response;
import client.zk.ServerWatcher;
import client.zk.ZooKeeperFactory;
import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.curator.framework.CuratorFramework;
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
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000) //单次 TCP 连接尝试的超时时间
                    .handler(new MyClientChannelInitializer());

            CuratorFramework client = ZooKeeperFactory.getClient(); //获取 Curator 封装好的 ZooKeeper 客户端对象
            List<String> serverPath = client.getChildren().usingWatcher(new ServerWatcher()).forPath(Constants.SERVER_PATH); //获取ZooKeeper 的 Constants.SERVER_PATH 节点下面的所有子节点

            Set<String> servers = new HashSet<>();
            for(String path : serverPath){
                String[] str = path.split("#");
                servers.add(str[0]+":"+str[1]);
            }

            ChannelManager.updateChannels(servers,b);//异步建立连接，不等待连接成功

            System.out.println("RPC 客户端初始化完成");

        }catch (Exception e){
            System.err.println(("RPC 客户端初始化异常"));
            e.printStackTrace();
        }
    }

    public  static void initialize(){
    }

    public static Response send(ClientRequest request){
        Channel channel = ChannelManager.getChannel(); //轮询选择一个可用连接
        ResultFuture resultFuture = new ResultFuture(request);// 先注册响应等待对象，避免响应先于注册到达
        channel.writeAndFlush(JSONObject.toJSONString(request)+"\r\n");//发送 rpc 请求
        return resultFuture.get();
    }


}
