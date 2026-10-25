package server;

import constants.Constants;
import factory.ZooKeeperFactory;
import handler.MyNettyServerInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import medium.Medium;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

@Component
public class NettyServer implements ApplicationListener<ContextRefreshedEvent> {
    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup work = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(boss, work)
                .option(ChannelOption.SO_BACKLOG, 128) //设置TCP队列大小：已连接+未连接
                .option(ChannelOption.SO_KEEPALIVE, false) //不使用默认的心跳机制
                .channel(NioServerSocketChannel.class)
                .childHandler(new MyNettyServerInitializer());

            ChannelFuture f = serverBootstrap.bind(Constants.SERVER_PORT).sync(); //绑定端口，并同步等待绑定完成

            System.out.println("准备注册 RPC 服务");

            CuratorFramework client = ZooKeeperFactory.getClient();

            if (client != null) {

                // 1. 获取当前服务器暴露的所有接口
                Set<String> serviceNames = new HashSet<>();

                for (String command : Medium.mediamap.keySet()) {

                    // 例如 remote.UserRemote.getUser
                    // 提取 remote.UserRemote
                    int lastDotIndex = command.lastIndexOf(".");

                    if (lastDotIndex > 0) {
                        String serviceName = command.substring(0, lastDotIndex);
                        serviceNames.add(serviceName);
                    }
                }

                // 2. 获取当前服务器地址
                String address = InetAddress.getLocalHost().getHostAddress()
                        + "#" + Constants.SERVER_PORT + "#";

                // 3. 按照接口名称注册
                for (String serviceName : serviceNames) {

                    String path = Constants.SERVER_PATH
                            + "/" + serviceName
                            + "/" + address;

                    String registeredPath = client.create()
                            .creatingParentsIfNeeded()
                            .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                            .forPath(path);

                    System.out.println("RPC 服务注册成功：" + registeredPath);
                }

                if (serviceNames.isEmpty()) {
                    System.out.println("当前服务器没有发现需要注册的 RPC 接口");
                }
            }

            f.channel() //f.channel()拿到监听端口的ServerSocketChannel
                    .closeFuture() //关闭代表Channel未来关闭事件的Future
                    .sync(); //当前线程阻塞等待,直到Channel真正关闭
            System.out.println("已关闭");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            boss.shutdownGracefully();
            work.shutdownGracefully();
        }
    }
}
