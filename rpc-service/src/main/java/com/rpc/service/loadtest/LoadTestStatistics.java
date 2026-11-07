package com.rpc.service.loadtest;

import com.rpc.client.constants.Constants;
import com.rpc.client.core.RpcClientConfig;
import com.rpc.client.core.RpcClientStats;

import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

public final class LoadTestStatistics {
    private static final int BUCKETS_PER_MILLISECOND = 10;

    private final AtomicLongArray latencyBuckets;
    private final LongAdder total = new LongAdder();
    private final LongAdder success = new LongAdder();
    private final LongAdder failure = new LongAdder();
    private final LongAdder timeout = new LongAdder();
    private final LongAdder rateLimited = new LongAdder();
    private final LongAdder circuitRejected = new LongAdder();
    private final LongAdder exception = new LongAdder();
    private final LongAdder latencyMicros = new LongAdder();
    private final LongAdder postFaultTotal = new LongAdder();
    private final LongAdder postFaultSuccess = new LongAdder();
    private final LongAdder postFaultFailure = new LongAdder();

    public LoadTestStatistics() {
        long defaultMaxMillis = RpcClientConfig.REQUEST_TIMEOUT_MILLIS
                * RpcClientConfig.MAX_ATTEMPTS + 1000L;
        int maxMillis = Integer.getInteger("rpc.load.max-latency.ms", (int) defaultMaxMillis);
        latencyBuckets = new AtomicLongArray(Math.max(1001, maxMillis * BUCKETS_PER_MILLISECOND + 2));
    }

    public void recordSuccess(long elapsedMicros, boolean postFault) {
        recordLatency(elapsedMicros);
        total.increment();
        success.increment();
        recordPostFault(postFault, true);
    }

    public void recordFailure(String code, long elapsedMicros, boolean postFault) {
        recordLatency(elapsedMicros);
        total.increment();
        failure.increment();
        recordPostFault(postFault, false);
        if (Constants.TIMEOUT.equals(code)) timeout.increment();
        else if (Constants.RATE_LIMITED.equals(code)) rateLimited.increment();
        else if (Constants.CIRCUIT_OPEN.equals(code)) circuitRejected.increment();
    }

    public void recordException(long elapsedMicros, boolean postFault) {
        recordLatency(elapsedMicros);
        total.increment();
        failure.increment();
        exception.increment();
        recordPostFault(postFault, false);
    }

    private void recordPostFault(boolean postFault, boolean successful) {
        if (!postFault) return;
        postFaultTotal.increment();
        if (successful) postFaultSuccess.increment();
        else postFaultFailure.increment();
    }

    private void recordLatency(long elapsedMicros) {
        long safeMicros = Math.max(0L, elapsedMicros);
        latencyMicros.add(safeMicros);
        long bucket = (safeMicros + 99L) / 100L;
        int index = (int) Math.min(bucket, latencyBuckets.length() - 1L);
        latencyBuckets.incrementAndGet(index);
    }

    public LoadTestResult toResult(LoadTestConfig config, double elapsedSeconds,
                                   RpcClientStats.Snapshot governanceStats) {
        long totalValue = total.sum();
        return new LoadTestResult(
                config, elapsedSeconds, totalValue, success.sum(), failure.sum(),
                totalValue == 0 ? 0.0 : latencyMicros.sum() / 1000.0 / totalValue,
                percentileMillis(totalValue, 0.50), percentileMillis(totalValue, 0.95),
                percentileMillis(totalValue, 0.99), timeout.sum(), rateLimited.sum(),
                circuitRejected.sum(), exception.sum(), postFaultTotal.sum(),
                postFaultSuccess.sum(), postFaultFailure.sum(), governanceStats);
    }

    private double percentileMillis(long count, double percentile) {
        if (count == 0) return 0.0;
        long target = (long) Math.ceil(count * percentile);
        long cumulative = 0;
        for (int i = 0; i < latencyBuckets.length(); i++) {
            cumulative += latencyBuckets.get(i);
            if (cumulative >= target) return i / (double) BUCKETS_PER_MILLISECOND;
        }
        return (latencyBuckets.length() - 1) / (double) BUCKETS_PER_MILLISECOND;
    }
}
