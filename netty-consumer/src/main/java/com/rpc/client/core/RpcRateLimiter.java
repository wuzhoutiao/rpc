package com.rpc.client.core;

import java.util.concurrent.ConcurrentHashMap;

/** 按服务维度的本地令牌桶，过载时快速失败。 */
final class RpcRateLimiter {

    private static final ConcurrentHashMap<String, TokenBucket> BUCKETS = new ConcurrentHashMap<>();

    private RpcRateLimiter() {
    }

    static boolean tryAcquire(String serviceName) {
        return BUCKETS.computeIfAbsent(serviceName, ignored -> new TokenBucket()).tryAcquire();
    }

    private static final class TokenBucket {
        private double tokens = RpcClientConfig.RATE_LIMIT_BURST;
        private long lastRefillNanos = System.nanoTime();

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            double added = (now - lastRefillNanos) / 1_000_000_000.0
                    * RpcClientConfig.RATE_LIMIT_PER_SECOND;
            tokens = Math.min(RpcClientConfig.RATE_LIMIT_BURST, tokens + added);
            lastRefillNanos = now;
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }
    }
}
