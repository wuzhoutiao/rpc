package com.rpc.server.testing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 仅由 JVM 参数显式启用的 Provider 故障注入器；默认配置不产生任何影响。
 */
public final class FaultInjector {

    private static final long DELAY_MILLIS = Math.max(0L, Long.getLong("rpc.fault.delay.ms", 0L));
    private static final double FAILURE_RATE = failureRate();
    private static final Path CONTROL_FILE = controlFile();
    private static volatile String cachedState;
    private static volatile long nextControlReadNanos;

    private FaultInjector() {
    }

    public static void beforeQuery() {
        String state = faultState();
        if (DELAY_MILLIS > 0 && delayEnabled(state)) {
            try {
                TimeUnit.MILLISECONDS.sleep(DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("故障注入延迟被中断", e);
            }
        }
        if (failureEnabled(state) && ThreadLocalRandom.current().nextDouble() < FAILURE_RATE) {
            throw new IllegalStateException("Provider 故障注入异常");
        }
    }

    public static String description() {
        return "delayMs=" + DELAY_MILLIS + ", failureRate=" + FAILURE_RATE
                + ", controlFile=" + CONTROL_FILE;
    }

    private static double failureRate() {
        String value = System.getProperty("rpc.fault.failure-rate", "0");
        try {
            return Math.max(0.0, Math.min(1.0, Double.parseDouble(value)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("rpc.fault.failure-rate 必须是 0 到 1 之间的小数", e);
        }
    }

    private static Path controlFile() {
        String value = System.getProperty("rpc.fault.control.file");
        return value == null || value.trim().isEmpty() ? null : Paths.get(value);
    }

    private static boolean delayEnabled(String state) {
        return CONTROL_FILE == null || "slow".equalsIgnoreCase(state);
    }

    private static boolean failureEnabled(String state) {
        if (FAILURE_RATE <= 0) return false;
        return CONTROL_FILE == null || !"normal".equalsIgnoreCase(state);
    }

    private static String faultState() {
        if (CONTROL_FILE == null) return "enabled";
        long now = System.nanoTime();
        String state = cachedState;
        if (state != null && now < nextControlReadNanos) return state;
        return refreshFaultState(now);
    }

    private static synchronized String refreshFaultState(long now) {
        if (cachedState != null && now < nextControlReadNanos) return cachedState;
        try {
            cachedState = new String(
                    Files.readAllBytes(CONTROL_FILE), StandardCharsets.UTF_8).trim();
            nextControlReadNanos = now + TimeUnit.MILLISECONDS.toNanos(50);
            return cachedState;
        } catch (Exception e) {
            throw new IllegalStateException("无法读取故障注入控制文件：" + CONTROL_FILE, e);
        }
    }
}
