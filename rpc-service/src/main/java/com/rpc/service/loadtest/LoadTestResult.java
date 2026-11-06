package com.rpc.service.loadtest;

import com.rpc.client.core.RpcClientStats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class LoadTestResult {
    private static final String CSV_HEADER = "timestamp,mode,fault_mode,concurrency,configured_duration_seconds,"
            + "actual_elapsed_seconds,total,success,failure,qps,average_ms,p50_ms,p95_ms,p99_ms,"
            + "timeouts,rate_limited,circuit_rejected,exceptions,retries,circuit_open_events,"
            + "post_fault_total,post_fault_success,post_fault_failure,"
            + "request_timeout_ms,max_attempts,slow_call_ms,java_version,os,processors";

    private final LoadTestConfig config;
    private final double elapsedSeconds;
    private final long total;
    private final long success;
    private final long failure;
    private final double averageMillis;
    private final double p50Millis;
    private final double p95Millis;
    private final double p99Millis;
    private final long timeouts;
    private final long rateLimited;
    private final long circuitRejected;
    private final long exceptions;
    private final long postFaultTotal;
    private final long postFaultSuccess;
    private final long postFaultFailure;
    private final RpcClientStats.Snapshot governance;
    private final String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    LoadTestResult(LoadTestConfig config, double elapsedSeconds, long total, long success, long failure,
                   double averageMillis, double p50Millis, double p95Millis, double p99Millis,
                   long timeouts, long rateLimited, long circuitRejected, long exceptions,
                   long postFaultTotal, long postFaultSuccess, long postFaultFailure,
                   RpcClientStats.Snapshot governance) {
        this.config = config;
        this.elapsedSeconds = elapsedSeconds;
        this.total = total;
        this.success = success;
        this.failure = failure;
        this.averageMillis = averageMillis;
        this.p50Millis = p50Millis;
        this.p95Millis = p95Millis;
        this.p99Millis = p99Millis;
        this.timeouts = timeouts;
        this.rateLimited = rateLimited;
        this.circuitRejected = circuitRejected;
        this.exceptions = exceptions;
        this.postFaultTotal = postFaultTotal;
        this.postFaultSuccess = postFaultSuccess;
        this.postFaultFailure = postFaultFailure;
        this.governance = governance;
    }

    public Path appendCsv() throws IOException {
        Files.createDirectories(config.getOutputDirectory());
        Path csv = config.getOutputDirectory().resolve("rpc-load-results.csv");
        boolean needsHeader = !Files.exists(csv) || Files.size(csv) == 0;
        StringBuilder text = new StringBuilder();
        if (needsHeader) text.append(CSV_HEADER).append(System.lineSeparator());
        text.append(csvRow()).append(System.lineSeparator());
        Files.write(csv, text.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return csv;
    }

    public void printSummary() {
        double qps = elapsedSeconds == 0 ? 0 : total / elapsedSeconds;
        System.out.println("\n========== RPC LOAD TEST RESULT ==========");
        System.out.printf(Locale.ROOT, "mode=%s, fault=%s, concurrency=%d, elapsed=%.2fs%n",
                config.getMode().name().toLowerCase(), config.getFaultMode(),
                config.getConcurrency(), elapsedSeconds);
        System.out.printf(Locale.ROOT, "total=%d, success=%d, failure=%d, qps=%.2f%n",
                total, success, failure, qps);
        System.out.printf(Locale.ROOT, "avg=%.2fms, p50=%.2fms, p95=%.2fms, p99=%.2fms%n",
                averageMillis, p50Millis, p95Millis, p99Millis);
        System.out.printf(Locale.ROOT,
                "timeouts=%d, rateLimited=%d, circuitRejected=%d, exceptions=%d%n",
                timeouts, rateLimited, circuitRejected, exceptions);
        System.out.printf(Locale.ROOT, "retries=%d, circuitOpenEvents=%d%n",
                governance.getRetries(), governance.getCircuitOpenEvents());
        if (config.getFaultAtSeconds() >= 0) {
            System.out.printf(Locale.ROOT, "postFaultTotal=%d, postFaultSuccess=%d, postFaultFailure=%d%n",
                    postFaultTotal, postFaultSuccess, postFaultFailure);
        }
        System.out.println("==========================================");
    }

    private String csvRow() {
        return String.join(",",
                quote(timestamp), quote(config.getMode().name().toLowerCase()), quote(config.getFaultMode()),
                String.valueOf(config.getConcurrency()), String.valueOf(config.getDurationSeconds()),
                format(elapsedSeconds), String.valueOf(total), String.valueOf(success), String.valueOf(failure),
                format(elapsedSeconds == 0 ? 0 : total / elapsedSeconds), format(averageMillis),
                format(p50Millis), format(p95Millis), format(p99Millis), String.valueOf(timeouts),
                String.valueOf(rateLimited), String.valueOf(circuitRejected), String.valueOf(exceptions),
                String.valueOf(governance.getRetries()), String.valueOf(governance.getCircuitOpenEvents()),
                String.valueOf(postFaultTotal), String.valueOf(postFaultSuccess),
                String.valueOf(postFaultFailure),
                System.getProperty("rpc.request.timeout.ms", "3000"),
                System.getProperty("rpc.failover.max-attempts", "3"),
                System.getProperty("rpc.slow-call.ms", "500"), quote(System.getProperty("java.version")),
                quote(System.getProperty("os.name") + " " + System.getProperty("os.version")),
                String.valueOf(Runtime.getRuntime().availableProcessors()));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public long getTotal() { return total; }
    public long getSuccess() { return success; }
    public long getFailure() { return failure; }
    public double getAverageMillis() { return averageMillis; }
    public double getP50Millis() { return p50Millis; }
    public double getP95Millis() { return p95Millis; }
    public double getP99Millis() { return p99Millis; }
    public long getTimeouts() { return timeouts; }
    public long getRateLimited() { return rateLimited; }
    public long getCircuitRejected() { return circuitRejected; }
    public long getExceptions() { return exceptions; }
    public long getPostFaultTotal() { return postFaultTotal; }
    public long getPostFaultSuccess() { return postFaultSuccess; }
    public long getPostFaultFailure() { return postFaultFailure; }
}
