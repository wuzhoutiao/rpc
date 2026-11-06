package com.rpc.client.core;

import com.rpc.api.protocol.Request;
import com.rpc.api.protocol.Response;
import com.rpc.client.handler.RpcClientChannelInitializer;
import com.rpc.client.zk.ServerWatcher;
import com.rpc.client.zk.ZooKeeperFactory;
import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.Set;


public class NettyClient {
    private static final Bootstrap bootstrap = new Bootstrap();

    // 整个 RPC 客户端共用同一个服务发现 Watcher
    private static final ServerWatcher serverWatcher = new ServerWatcher();

    // Netty 客户端线程组
    private static final EventLoopGroup worker = new NioEventLoopGroup();

    // 客户端是否正在关闭
    private static volatile boolean shuttingDown = false;
    private static volatile boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        try {
            bootstrap.group(worker)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000) //单次 TCP 连接尝试的超时时间
                    .handler(new RpcClientChannelInitializer());

            // 初始化 ZooKeeper 客户端
            ZooKeeperFactory.getClient();
            // 首次读取所有 RPC 服务，并注册 Watcher
            serverWatcher.refreshServices();
            initialized = true;
            System.out.println("RPC 客户端初始化完成");

        }catch (Exception e){
            System.err.println(("RPC 客户端初始化异常"));
            throw new IllegalStateException("RPC 客户端初始化失败", e);
        }
    }

    public static Bootstrap getBootstrap() {
        return bootstrap;
    }

    public static Response send(Request request) {

        if (!initialized) {
            throw new IllegalStateException("RPC 客户端尚未初始化");
        }

        // 1. 获取 RPC 命令
        String command = request.getCommand();

        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "RPC 请求 command 不能为空"
            );
        }

        // 2. 从 command 中提取接口名称
        // 例如：remote.UserRemote.getUser
        int lastDotIndex = command.lastIndexOf(".");

        if (lastDotIndex <= 0 || lastDotIndex == command.length() - 1) {
            throw new IllegalArgumentException(
                    "无效的 RPC 命令：" + command
            );
        }

        String serviceName = command.substring(0, lastDotIndex);

        // 3. 根据接口名称选择服务器
        Channel channel = ChannelManager.getChannel(serviceName);

        // 4. 注册等待对象
        ResultFuture resultFuture = new ResultFuture(request);

        // 5. 异步发送请求
        ChannelFuture future = channel.writeAndFlush(
                JSONObject.toJSONString(request) + "\r\n"
        );

        // 6. 监听发送结果
        future.addListener((ChannelFutureListener) f -> {

            if (!f.isSuccess()) {

                System.err.println(
                        "RPC 请求发送失败，requestId = " + request.getId()
                );

                if (f.cause() != null) {
                    f.cause().printStackTrace();
                }

                Response response = new Response();

                response.setId(request.getId());
                response.setCode("RPC_SEND_FAILED");
                response.setMessage("RPC 请求发送失败");

                ResultFuture.receive(response);
            }
        });

        // 7. 等待响应
        return resultFuture.get();
    }

    public static boolean isShuttingDown() {
        return shuttingDown;
    }

    public static synchronized void shutdown() {

        // 防止重复关闭
        if (!initialized || shuttingDown) {
            return;
        }

        // 1. 标记客户端正在关闭
        shuttingDown = true;
        initialized = false;

        System.out.println("RPC 客户端开始关闭");

        // 2. 结束所有正在等待的 RPC 请求
        ResultFuture.shutdownAll();

        // 3. 停止自动重连，并关闭所有 Channel
        ChannelManager.shutdown();

        // 4. 关闭 ZooKeeper 客户端
        ZooKeeperFactory.close();
        System.out.println("ZooKeeper 客户端已关闭");

        // 5. 关闭 Netty 线程组
        worker.shutdownGracefully();

        System.out.println("Netty 线程组已发起关闭");
        System.out.println("RPC 客户端关闭流程已启动");
    }

    /**
     * 获取指定 RPC 接口对应的服务器地址
     */
    public static Set<String> getServers(String serviceName) {

        return serverWatcher.getServers(serviceName);
    }

}
