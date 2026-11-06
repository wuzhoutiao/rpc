package com.rpc.client.core;

import java.util.concurrent.atomic.LongAdder;

/** 压测和诊断使用的进程内客户端治理计数器。 */
public final class RpcClientStats {

    private static final LongAdder RETRIES = new LongAdder();
    private static final LongAdder CIRCUIT_OPEN_EVENTS = new LongAdder();
    private static final LongAdder CIRCUIT_REJECTIONS = new LongAdder();

    private RpcClientStats() {
    }

    static void recordRetry() {
        RETRIES.increment();
    }

    static void recordCircuitOpen() {
        CIRCUIT_OPEN_EVENTS.increment();
    }

    static void recordCircuitRejection() {
        CIRCUIT_REJECTIONS.increment();
    }

    public static Snapshot snapshot() {
        return new Snapshot(RETRIES.sum(), CIRCUIT_OPEN_EVENTS.sum(), CIRCUIT_REJECTIONS.sum());
    }

    public static final class Snapshot {
        private final long retries;
        private final long circuitOpenEvents;
        private final long circuitRejections;

        private Snapshot(long retries, long circuitOpenEvents, long circuitRejections) {
            this.retries = retries;
            this.circuitOpenEvents = circuitOpenEvents;
            this.circuitRejections = circuitRejections;
        }

        public Snapshot minus(Snapshot before) {
            return new Snapshot(
                    retries - before.retries,
                    circuitOpenEvents - before.circuitOpenEvents,
                    circuitRejections - before.circuitRejections);
        }

        public long getRetries() { return retries; }
        public long getCircuitOpenEvents() { return circuitOpenEvents; }
        public long getCircuitRejections() { return circuitRejections; }
    }
}
