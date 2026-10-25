package client.core;

import client.constants.Constants;
import client.handler.MyClientChannelInitializer;
import client.protocol.ClientRequest;
import com.rpc.common.protocol.Response;
import client.zk.ServerWatcher;
import client.zk.ZooKeeperFactory;
import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.curator.framework.CuratorFramework;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class NettyClient {
    public static final Bootstrap b = new Bootstrap();

    // Netty 客户端线程组
    private static final EventLoopGroup worker = new NioEventLoopGroup();

    // 客户端是否正在关闭
    private static volatile boolean shuttingDown = false;


    static {
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

    public static Response send(ClientRequest request) {

        // 1. 获取可用连接
        Channel channel = ChannelManager.getChannel();

        // 2. 注册等待对象
        ResultFuture resultFuture = new ResultFuture(request);

        // 3. 异步发送请求
        ChannelFuture future = channel.writeAndFlush(
                JSONObject.toJSONString(request) + "\r\n"
        );

        // 4. 监听发送结果
        future.addListener((ChannelFutureListener) f -> {

            if (!f.isSuccess()) {

                System.err.println(
                        "RPC 请求发送失败，requestId = " + request.getId()
                );

                if (f.cause() != null) {
                    f.cause().printStackTrace();
                }

                // 构造失败响应
                Response response = new Response();

                response.setId(request.getId());
                response.setCode("RPC_SEND_FAILED");
                response.setMessage("RPC 请求发送失败");

                // 通知 ResultFuture
                ResultFuture.receive(response);
            }
        });

        // 5. 等待服务端响应或发送失败通知
        return resultFuture.get();
    }

    public static boolean isShuttingDown() {
        return shuttingDown;
    }

    public static synchronized void shutdown() {

        // 防止重复关闭
        if (shuttingDown) {
            return;
        }

        // 1. 标记客户端正在关闭
        shuttingDown = true;

        System.out.println("RPC 客户端开始关闭");

        // 2. 结束所有正在等待的 RPC 请求
        ResultFuture.shutdownAll();

        // 3. 停止自动重连，并关闭所有 Channel
        ChannelManager.shutdown();

        // 4. 关闭 ZooKeeper 客户端
        CuratorFramework zkClient = ZooKeeperFactory.client;

        if (zkClient != null) {
            zkClient.close();
            System.out.println("ZooKeeper 客户端已关闭");
        }

        // 5. 关闭 Netty 线程组
        worker.shutdownGracefully();

        System.out.println("Netty 线程组已发起关闭");
        System.out.println("RPC 客户端关闭流程已启动");
    }

}
