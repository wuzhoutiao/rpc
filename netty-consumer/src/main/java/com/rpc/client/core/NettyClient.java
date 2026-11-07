package com.rpc.client.core;

import com.rpc.api.metrics.RpcMetrics;
import com.rpc.api.protocol.Request;
import com.rpc.api.protocol.RpcResponse;
import com.rpc.api.protocol.RpcMethodSignature;
import com.rpc.api.protocol.HeartbeatMessage;
import com.rpc.client.constants.Constants;
import com.rpc.client.handler.RpcClientChannelInitializer;
import com.rpc.client.zk.ServerWatcher;
import com.rpc.client.zk.ZooKeeperFactory;
import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;


public class NettyClient {
    private static final Bootstrap bootstrap = new Bootstrap();

    // 整个 RPC 客户端共用同一个服务发现 Watcher
    private static final ServerWatcher serverWatcher = new ServerWatcher();

    // Netty 客户端线程组
    private static final EventLoopGroup worker = new NioEventLoopGroup();

    // 客户端是否正在关闭
    private static volatile boolean shuttingDown = false;
    private static volatile boolean initialized = false;
    private static final AtomicLong REQUEST_ID = new AtomicLong();
    private static RpcMetrics.Endpoint metricsEndpoint;

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
            startMetrics();
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

    public static RpcResponse send(Request request) {

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

        // 2. 从包含参数类型的方法签名中提取接口名称
        String serviceName = RpcMethodSignature.serviceName(command);
        if (serviceName == null) {
            throw new IllegalArgumentException(
                    "无效的 RPC 命令：" + command
            );
        }

        if (!RpcRateLimiter.tryAcquire(serviceName)) {
            RpcMetrics.increment("rpc_client_rate_limited_total", Map.of("service", serviceName));
            return errorResponse(request, Constants.RATE_LIMITED, "客户端限流：请求过多");
        }

        Set<String> attemptedNodes = new HashSet<>();
        RpcResponse lastResponse = null;
        for (int attempt = 1; attempt <= RpcClientConfig.MAX_ATTEMPTS; attempt++) {
            ChannelManager.NodeEndpoint endpoint = ChannelManager.selectNode(serviceName, attemptedNodes);
            if (endpoint == null) {
                break;
            }
            attemptedNodes.add(endpoint.getAddress());
            request.setId(REQUEST_ID.incrementAndGet());
            long attemptRequestId = request.getId();
            ResultFuture resultFuture = new ResultFuture(request, RpcClientConfig.REQUEST_TIMEOUT_MILLIS);
            long startNanos = System.nanoTime();
            ChannelFuture future = endpoint.getChannel().writeAndFlush(
                    JSONObject.toJSONString(request) + HeartbeatMessage.LINE_DELIMITER
            );
            future.addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    RpcResponse response = new RpcResponse();
                    response.setId(attemptRequestId);
                    response.setCode("RPC_SEND_FAILED");
                    response.setMessage("RPC 请求发送失败");
                    ResultFuture.receive(response);
                }
            });

            RpcResponse response = resultFuture.get();
            long latencyMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            boolean success = Constants.SUCCESS.equals(response.getCode());
            boolean retryableFailure = isRetriable(response);
            boolean circuitOpened = ChannelManager.recordResult(endpoint, !retryableFailure, latencyMillis);
            Map<String, String> metricLabels = Map.of(
                    "service", serviceName,
                    "node", endpoint.getAddress(),
                    "outcome", success ? "success" : "failure");
            RpcMetrics.increment("rpc_client_requests_total", metricLabels);
            RpcMetrics.observeMillis("rpc_client_request_duration_milliseconds", metricLabels, latencyMillis);
            if (circuitOpened) {
                RpcClientStats.recordCircuitOpen();
                RpcMetrics.increment("rpc_client_circuit_open_total", Map.of("node", endpoint.getAddress()));
                System.err.println("节点熔断：" + endpoint.getAddress()
                        + "，code=" + response.getCode() + "，latencyMs=" + latencyMillis);
            }
            if (success || !retryableFailure) {
                return response;
            }
            lastResponse = response;
            RpcClientStats.recordRetry();
            RpcMetrics.increment("rpc_client_retries_total", Map.of("service", serviceName));
            System.err.println("RPC 调用失败，切换节点重试：node=" + endpoint.getAddress()
                    + "，attempt=" + attempt + "，code=" + response.getCode());
        }

        if (lastResponse != null) {
            return lastResponse;
        }
        if (attemptedNodes.isEmpty() && ChannelManager.hasActiveNode(serviceName)) {
            RpcClientStats.recordCircuitRejection();
            return errorResponse(request, Constants.CIRCUIT_OPEN, "节点熔断中，拒绝请求");
        }
        return errorResponse(request, Constants.NO_HEALTHY_NODE, "没有可用的健康节点");
    }

    private static boolean isRetriable(RpcResponse response) {
        return Constants.TIMEOUT.equals(response.getCode())
                || Constants.FAILURE.equals(response.getCode())
                || "RPC_SEND_FAILED".equals(response.getCode());
    }

    private static void startMetrics() {
        if (!Boolean.parseBoolean(System.getProperty("rpc.metrics.enabled", "true"))) {
            return;
        }
        int metricsPort = Integer.getInteger("rpc.client.metrics.port", 9092);
        try {
            metricsEndpoint = RpcMetrics.startEndpoint(metricsPort);
            System.out.println("客户端 Prometheus 指标：http://127.0.0.1:" + metricsPort + "/metrics");
        } catch (Exception e) {
            System.err.println("客户端指标端点启动失败：" + e.getMessage());
        }
    }

    private static RpcResponse errorResponse(Request request, String code, String message) {
        RpcResponse response = new RpcResponse();
        response.setId(request.getId() == null ? 0L : request.getId());
        response.setCode(code);
        response.setMessage(message);
        return response;
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

        // 5. 关闭指标端点
        if (metricsEndpoint != null) {
            metricsEndpoint.close();
            metricsEndpoint = null;
        }

        // 6. 关闭 Netty 线程组
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

    /** 压测就绪等待和服务发现异常恢复时主动刷新一次注册信息。 */
    public static void refreshServices() throws Exception {
        if (!shuttingDown) {
            serverWatcher.refreshServices();
        }
    }

}
