package com.rpc.service.loadtest;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class LoadTestConfig {
    private final LoadTestMode mode;
    private final int concurrency;
    private final int durationSeconds;
    private final int warmupSeconds;
    private final Path outputDirectory;
    private final String faultMode;
    private final int faultAtSeconds;
    private final Path readyFile;

    private LoadTestConfig(LoadTestMode mode, int concurrency, int durationSeconds,
                           int warmupSeconds, Path outputDirectory, String faultMode,
                           int faultAtSeconds, Path readyFile) {
        this.mode = mode;
        this.concurrency = concurrency;
        this.durationSeconds = durationSeconds;
        this.warmupSeconds = warmupSeconds;
        this.outputDirectory = outputDirectory;
        this.faultMode = faultMode;
        this.faultAtSeconds = faultAtSeconds;
        this.readyFile = readyFile;
    }

    public static LoadTestConfig parse(String[] args) {
        LoadTestMode mode;
        int argumentOffset;
        if (args.length > 0 && args[0].matches("\\d+")) {
            // 兼容旧用法：RpcLoadTest 32 30
            mode = LoadTestMode.CUSTOM;
            argumentOffset = 0;
        } else {
            mode = args.length > 0
                    ? LoadTestMode.parse(args[0])
                    : LoadTestMode.parse(System.getProperty("rpc.load.mode", "benchmark"));
            argumentOffset = args.length > 0 ? 1 : 0;
        }

        int concurrency = positiveInt(args, argumentOffset, "rpc.load.concurrency", mode.getConcurrency());
        int duration = positiveInt(args, argumentOffset + 1, "rpc.load.duration.seconds", mode.getDurationSeconds());
        int warmup = nonNegativeInt("rpc.load.warmup.seconds", mode.getWarmupSeconds());
        Path output = Paths.get(System.getProperty("rpc.load.output.dir", "load-test-results"))
                .toAbsolutePath().normalize();
        String faultMode = System.getProperty("rpc.load.fault-mode", "none");
        int faultAtSeconds = Integer.getInteger("rpc.load.fault.at.seconds", -1);
        String readyFileValue = System.getProperty("rpc.load.ready.file");
        Path readyFile = readyFileValue == null || readyFileValue.trim().isEmpty()
                ? null : Paths.get(readyFileValue).toAbsolutePath().normalize();
        return new LoadTestConfig(mode, concurrency, duration, warmup, output, faultMode,
                faultAtSeconds, readyFile);
    }

    private static int positiveInt(String[] args, int index, String property, int defaultValue) {
        int value = index < args.length
                ? Integer.parseInt(args[index])
                : Integer.getInteger(property, defaultValue);
        if (value <= 0) throw new IllegalArgumentException(property + " 必须大于 0");
        return value;
    }

    private static int nonNegativeInt(String property, int defaultValue) {
        int value = Integer.getInteger(property, defaultValue);
        if (value < 0) throw new IllegalArgumentException(property + " 不能小于 0");
        return value;
    }

    public LoadTestMode getMode() { return mode; }
    public int getConcurrency() { return concurrency; }
    public int getDurationSeconds() { return durationSeconds; }
    public int getWarmupSeconds() { return warmupSeconds; }
    public Path getOutputDirectory() { return outputDirectory; }
    public String getFaultMode() { return faultMode; }
    public int getFaultAtSeconds() { return faultAtSeconds; }
    public Path getReadyFile() { return readyFile; }
}
