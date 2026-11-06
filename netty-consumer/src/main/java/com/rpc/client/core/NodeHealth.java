package com.rpc.client.core;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** 单节点的慢调用、并发数和熔断状态。 */
final class NodeHealth {

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicBoolean halfOpenProbe = new AtomicBoolean();
    private volatile int consecutiveFailures;
    private volatile long openUntilMillis;
    private volatile double latencyEwmaMillis = RpcClientConfig.SLOW_CALL_MILLIS / 2.0;

    boolean tryAcquire() {
        long openUntil = openUntilMillis;
        long now = System.currentTimeMillis();
        if (openUntil > now) {
            return false;
        }
        if (openUntil != 0 && !halfOpenProbe.compareAndSet(false, true)) {
            return false;
        }
        inFlight.incrementAndGet();
        return true;
    }

    synchronized boolean complete(boolean success, long latencyMillis) {
        inFlight.updateAndGet(value -> Math.max(0, value - 1));
        latencyEwmaMillis = latencyEwmaMillis * 0.8 + latencyMillis * 0.2;
        boolean healthy = success && latencyMillis < RpcClientConfig.SLOW_CALL_MILLIS;
        if (healthy) {
            consecutiveFailures = 0;
            openUntilMillis = 0;
            halfOpenProbe.set(false);
            return false;
        }

        consecutiveFailures++;
        halfOpenProbe.set(false);
        if (consecutiveFailures >= RpcClientConfig.CIRCUIT_FAILURE_THRESHOLD) {
            openUntilMillis = System.currentTimeMillis() + RpcClientConfig.CIRCUIT_OPEN_MILLIS;
            return true;
        }
        return false;
    }

    double score() {
        return latencyEwmaMillis + inFlight.get() * 50.0
                + consecutiveFailures * RpcClientConfig.SLOW_CALL_MILLIS;
    }
}
