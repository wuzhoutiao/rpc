package com.rpc.client.constants;

public class Constants {
    public static final String SERVER_PATH = "/netty";
    public static final int SERVER_PORT = 8081;
    public static final String SERVER_IP = "127.0.0.1";

    public static final String SUCCESS = "00000";
    public static final String TIMEOUT = "10001";
    public static final String METHOD_NOT_FOUND = "10002";
    public static final String FAILURE = "00001";
    public static final String RATE_LIMITED = "10003";
    public static final String NO_HEALTHY_NODE = "10004";
    public static final String CIRCUIT_OPEN = "10005";
}
