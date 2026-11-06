package com.rpc.client.core;

/** 通过 JVM 参数提供可调的客户端治理配置。 */
public final class RpcClientConfig {

    public static final long REQUEST_TIMEOUT_MILLIS = longProperty("rpc.request.timeout.ms", 3000L);
    public static final int MAX_ATTEMPTS = intProperty("rpc.failover.max-attempts", 3);
    public static final int CIRCUIT_FAILURE_THRESHOLD = intProperty("rpc.circuit.failure-threshold", 3);
    public static final long CIRCUIT_OPEN_MILLIS = longProperty("rpc.circuit.open.ms", 10000L);
    public static final long SLOW_CALL_MILLIS = longProperty("rpc.slow-call.ms", 500L);
    public static final int RATE_LIMIT_PER_SECOND = intProperty("rpc.rate-limit.per-second", 1000);
    public static final int RATE_LIMIT_BURST = intProperty("rpc.rate-limit.burst", 1000);

    private RpcClientConfig() {
    }

    private static int intProperty(String name, int defaultValue) {
        return Math.max(1, Integer.getInteger(name, defaultValue));
    }

    private static long longProperty(String name, long defaultValue) {
        return Math.max(1L, Long.getLong(name, defaultValue));
    }
}
