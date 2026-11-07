package com.rpc.server.handler;

import com.alibaba.fastjson.JSONObject;
import com.rpc.api.protocol.Request;
import com.rpc.api.protocol.RpcResponse;
import com.rpc.api.protocol.RpcMethodSignature;
import com.rpc.api.metrics.RpcMetrics;
import com.rpc.server.medium.RpcRequestDispatcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.Map;

public class RpcServerHandler extends ChannelInboundHandlerAdapter {
    private static final boolean VERBOSE = Boolean.getBoolean("rpc.verbose");
    /* 请求进入 pipeline 后，由业务 EventExecutorGroup 执行反序列化、反射调用和响应写回。 */

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String message = msg.toString();

        if (VERBOSE) System.out.println("服务器收到消息：" + message);
        Request request = JSONObject.parseObject(message, Request.class);
        if (VERBOSE) {
            System.out.println("正在处理请求，id=" + request.getId() + ", command=" + request.getCommand());
        }
        long startNanos = System.nanoTime();
        RpcResponse response = RpcRequestDispatcher.newInstance().process(request);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        String service = RpcMethodSignature.serviceName(request.getCommand());
        String outcome = "00000".equals(response.getCode()) ? "success" : "failure";
        Map<String, String> labels = Map.of(
                "service", service == null ? "unknown" : service,
                "outcome", outcome);
        RpcMetrics.increment("rpc_server_requests_total", labels);
        RpcMetrics.observeMillis("rpc_server_request_duration_milliseconds", labels, elapsedMillis);
        ctx.writeAndFlush(JSONObject.toJSONString(response) + "\r\n");
    }

}
