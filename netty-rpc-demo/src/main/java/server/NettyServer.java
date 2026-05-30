package server;

import constants.Constants;
import factory.ZooKeeperFactory;
import handler.MyNettyServerInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

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

            System.out.println("准备注册");

            CuratorFramework client = ZooKeeperFactory.getClient(); //获得一个zookeeper客户端来连接
            if(client != null) {
                System.out.println(client);
                client.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL_SEQUENTIAL) //临时端点
                        .forPath(Constants.SERVER_PATH+"/"+InetAddress.getLocalHost().getHostAddress()+"#"+Constants.SERVER_PORT+"#");
                System.out.println("注册成功");
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
