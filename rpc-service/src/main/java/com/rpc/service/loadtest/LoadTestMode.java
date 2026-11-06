package com.rpc.service.loadtest;

public enum LoadTestMode {
    SMOKE(2, 10, 0),
    BENCHMARK(32, 30, 5),
    STRESS(64, 180, 10),
    CUSTOM(32, 30, 5);

    private final int concurrency;
    private final int durationSeconds;
    private final int warmupSeconds;

    LoadTestMode(int concurrency, int durationSeconds, int warmupSeconds) {
        this.concurrency = concurrency;
        this.durationSeconds = durationSeconds;
        this.warmupSeconds = warmupSeconds;
    }

    public int getConcurrency() { return concurrency; }
    public int getDurationSeconds() { return durationSeconds; }
    public int getWarmupSeconds() { return warmupSeconds; }

    public static LoadTestMode parse(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
