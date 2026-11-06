package com.rpc.api.protocol;

/**
 * RPC 长连接使用的心跳消息。
 */
public final class HeartbeatMessage {

    public static final String PING = "ping";
    public static final String PONG = "pong";
    public static final String LINE_DELIMITER = "\r\n";

    /** 空闲多久后主动发送一次心跳。 */
    public static final int WRITER_IDLE_SECONDS = 15;

    /** 多久没有收到任何数据后判定对端失活。 */
    public static final int READER_IDLE_SECONDS = 45;

    private HeartbeatMessage() {
    }
}
