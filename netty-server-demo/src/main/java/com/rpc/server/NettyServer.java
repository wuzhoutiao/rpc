package com.rpc.server;

import com.rpc.server.constants.Constants;
import com.rpc.server.factory.ZooKeeperFactory;
import com.rpc.server.handler.RpcServerChannelInitializer;
import com.rpc.server.medium.RpcRequestDispatcher;
import com.rpc.api.protocol.RpcMethodSignature;
import com.rpc.api.metrics.RpcMetrics;
import com.rpc.server.testing.FaultInjector;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

@Component
public class NettyServer implements ApplicationListener<ContextRefreshedEvent> {

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private DefaultEventExecutorGroup businessGroup;
    private Channel serverChannel;
    private RpcMetrics.Endpoint metricsEndpoint;

    @Override
    public synchronized void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() != null
                || (serverChannel != null && serverChannel.isActive())) {
            return;
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        businessGroup = new DefaultEventExecutorGroup(10);

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                .option(ChannelOption.SO_BACKLOG, 128) //设置TCP队列大小：已连接+未连接
                .childOption(ChannelOption.SO_KEEPALIVE, false) //使用应用层心跳机制
                .channel(NioServerSocketChannel.class)
                .childHandler(new RpcServerChannelInitializer(businessGroup));

            serverChannel = serverBootstrap.bind(Constants.SERVER_PORT).sync().channel();
            System.out.println("RPC 服务端已监听端口 " + Constants.SERVER_PORT
                    + "，故障注入配置：" + FaultInjector.description());

            if (Boolean.parseBoolean(System.getProperty("rpc.metrics.enabled", "true"))) {
                int metricsPort = Integer.getInteger("rpc.server.metrics.port", 9091);
                try {
                    metricsEndpoint = RpcMetrics.startEndpoint(metricsPort);
                    System.out.println("服务端 Prometheus 指标：http://127.0.0.1:" + metricsPort + "/metrics");
                } catch (Exception metricsError) {
                    System.err.println("服务端指标端点启动失败：" + metricsError.getMessage());
                }
            }

            System.out.println("准备注册 RPC 服务");

            CuratorFramework zkClient = ZooKeeperFactory.getClient();

            if (zkClient != null) {

                // 1. 获取当前服务器暴露的所有接口
                Set<String> serviceNames = new HashSet<>();

                for (String command : RpcRequestDispatcher.getCommands()) {

                    String serviceName = RpcMethodSignature.serviceName(command);
                    if (serviceName != null) {
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

                    String registeredPath = zkClient.create()
                            .creatingParentsIfNeeded()
                            .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                            .forPath(path);

                    System.out.println("RPC 服务注册成功：" + registeredPath);
                }

                if (serviceNames.isEmpty()) {
                    System.out.println("当前服务器没有发现需要注册的 RPC 接口");
                }
            }

        } catch (Exception e) {
            shutdown();
            throw new IllegalStateException("RPC 服务端启动失败", e);
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (metricsEndpoint != null) {
            metricsEndpoint.close();
            metricsEndpoint = null;
        }
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        ZooKeeperFactory.close();
        if (businessGroup != null) {
            businessGroup.shutdownGracefully();
            businessGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
    }
}
