package com.rpc.api.protocol;

/**
 * RPC 长连接使用的心跳消息。
 */
public final class HeartbeatMessage {

    public static final String PING = "ping";
    public static final String PONG = "pong";
    public static final String LINE_DELIMITER = "\r\n";

    private HeartbeatMessage() {
    }
}
