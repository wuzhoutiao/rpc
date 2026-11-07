package com.rpc.client.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** 单节点的慢调用、并发数和熔断状态。 */
final class NodeHealth {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final AtomicInteger inFlight = new AtomicInteger();
    private final LongSupplier currentTimeMillis;
    private volatile int consecutiveFailures;
    private volatile long openUntilMillis;
    private volatile long slowSinceMillis = -1L;
    private volatile double latencyEwmaMillis = RpcClientConfig.SLOW_CALL_MILLIS / 2.0;
    private volatile State state = State.CLOSED;

    NodeHealth() {
        this(System::currentTimeMillis);
    }

    NodeHealth(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = currentTimeMillis;
    }

    synchronized boolean tryAcquire() {
        long now = currentTimeMillis.getAsLong();
        if (state == State.OPEN) {
            if (openUntilMillis > now) {
                return false;
            }
            // OPEN 窗口结束后只允许一个探测请求，其他并发请求继续快速拒绝。
            state = State.HALF_OPEN;
        } else if (state == State.HALF_OPEN) {
            return false;
        }
        inFlight.incrementAndGet();
        return true;
    }

    synchronized boolean complete(boolean success, long latencyMillis) {
        inFlight.updateAndGet(value -> Math.max(0, value - 1));
        latencyEwmaMillis = latencyEwmaMillis * 0.8 + latencyMillis * 0.2;
        // 单次调度抖动不能直接判坏节点；只有当前调用和节点延迟趋势都超过阈值，
        // 才把成功但缓慢的调用计入连续失败。明确的 RPC 失败仍立即计数。
        boolean slow = latencyMillis >= RpcClientConfig.SLOW_CALL_MILLIS
                && latencyEwmaMillis >= RpcClientConfig.SLOW_CALL_MILLIS;
        if (success && slow && state == State.CLOSED) {
            long now = currentTimeMillis.getAsLong();
            if (slowSinceMillis < 0) {
                slowSinceMillis = now;
                return false;
            }
            // 同一时刻完成的一批请求通常是调度或 GC 抖动。只有慢趋势持续至少
            // 一个慢调用阈值窗口，才将其计入连续失败。
            if (now - slowSinceMillis < RpcClientConfig.SLOW_CALL_MILLIS) {
                return false;
            }
        } else if (success) {
            slowSinceMillis = -1L;
        }
        boolean healthy = success && !slow;

        if (state == State.HALF_OPEN) {
            if (healthy) {
                closeCircuit();
                return false;
            }
            openCircuit();
            return true;
        }

        // 熔断打开前已经发出的并发请求可能稍后才完成。它们不能重复触发 OPEN，
        // 也不能用一个过期的成功结果提前关闭当前熔断窗口。
        if (state == State.OPEN) {
            return false;
        }

        if (healthy) {
            closeCircuit();
            return false;
        }

        consecutiveFailures++;
        if (consecutiveFailures >= RpcClientConfig.CIRCUIT_FAILURE_THRESHOLD) {
            openCircuit();
            return true;
        }
        return false;
    }

    private void openCircuit() {
        state = State.OPEN;
        openUntilMillis = currentTimeMillis.getAsLong() + RpcClientConfig.CIRCUIT_OPEN_MILLIS;
    }

    private void closeCircuit() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        openUntilMillis = 0;
        slowSinceMillis = -1L;
    }

    double score() {
        if (state == State.OPEN && openUntilMillis <= currentTimeMillis.getAsLong()) {
            // 到期节点优先获得一次半开探测，避免在存在其他健康节点时永远无法恢复。
            return -1.0;
        }
        double statePenalty = state == State.CLOSED
                ? 0.0 : RpcClientConfig.SLOW_CALL_MILLIS * 1000.0;
        return statePenalty + latencyEwmaMillis + inFlight.get() * 50.0
                + consecutiveFailures * RpcClientConfig.SLOW_CALL_MILLIS;
    }
}
