package com.rpc.service;

import com.rpc.api.service.UserRemote;
import com.rpc.client.core.ChannelManager;
import com.rpc.client.core.NettyClient;
import com.rpc.client.core.RpcClientStats;
import com.rpc.client.constants.Constants;
import com.rpc.client.exception.RpcException;
import com.rpc.service.config.ClientConfig;
import com.rpc.service.loadtest.LoadTestConfig;
import com.rpc.service.loadtest.LoadTestResult;
import com.rpc.service.loadtest.LoadTestStatistics;
import com.rpc.service.service.ClientService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 面向真实 ZooKeeper 与 RPC 服务端的轻量压测入口。
 * 参数：smoke|benchmark|stress [并发线程数] [持续秒数]。
 * 仍兼容旧参数形式：并发线程数、持续秒数。
 */
public class RpcLoadTest {

    public static void main(String[] args) throws Exception {
        LoadTestConfig config = LoadTestConfig.parse(args);
        System.out.printf("启动 RPC 压测：mode=%s, concurrency=%d, duration=%ds, warmup=%ds, fault=%s%n",
                config.getMode().name().toLowerCase(), config.getConcurrency(),
                config.getDurationSeconds(), config.getWarmupSeconds(), config.getFaultMode());

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ClientConfig.class)) {
            ClientService client = context.getBean(ClientService.class);
            awaitProvider();

            if (config.getWarmupSeconds() > 0) {
                System.out.println("预热 " + config.getWarmupSeconds() + " 秒（不计入结果）...");
                runPhase(client, config.getConcurrency(), config.getWarmupSeconds(), null, -1);
            }

            LoadTestStatistics statistics = new LoadTestStatistics();
            RpcClientStats.Snapshot before = RpcClientStats.snapshot();
            signalReady(config);
            double elapsedSeconds = runPhase(
                    client, config.getConcurrency(), config.getDurationSeconds(), statistics,
                    config.getFaultAtSeconds());
            RpcClientStats.Snapshot governance = RpcClientStats.snapshot().minus(before);
            LoadTestResult result = statistics.toResult(config, elapsedSeconds, governance);
            result.printSummary();
            Path csv = result.appendCsv();
            System.out.println("CSV 结果已保存：" + csv);
        }
    }

    private static double runPhase(ClientService client, int concurrency, int durationSeconds,
                                   LoadTestStatistics statistics, int postFaultAfterSeconds)
            throws InterruptedException {
        ExecutorService workers = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        AtomicLong deadlineNanos = new AtomicLong();
        AtomicLong postFaultNanos = new AtomicLong(Long.MAX_VALUE);
        List<Future<?>> tasks = new ArrayList<>(concurrency);
        for (int thread = 0; thread < concurrency; thread++) {
            final int workerId = thread;
            tasks.add(workers.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    long sequence = 0;
                    while (System.nanoTime() < deadlineNanos.get()) {
                        long callStart = System.nanoTime();
                        boolean postFault = callStart >= postFaultNanos.get();
                        try {
                            client.findRemote(
                                    workerId * 1_000_000L + sequence++, "load-test");
                            if (statistics != null) {
                                statistics.recordSuccess(
                                        (System.nanoTime() - callStart) / 1_000L, postFault);
                            }
                        } catch (RpcException error) {
                            if (statistics != null) {
                                statistics.recordFailure(error.getCode(),
                                        (System.nanoTime() - callStart) / 1_000L, postFault);
                            }
                            backoffAfterImmediateRejection(error.getCode());
                        } catch (Exception error) {
                            if (statistics != null) {
                                statistics.recordException((System.nanoTime() - callStart) / 1_000L,
                                        postFault);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        ready.await();
        long startedNanos = System.nanoTime();
        deadlineNanos.set(startedNanos + TimeUnit.SECONDS.toNanos(durationSeconds));
        postFaultNanos.set(postFaultAfterSeconds < 0
                ? Long.MAX_VALUE
                : startedNanos + TimeUnit.SECONDS.toNanos(postFaultAfterSeconds));
        start.countDown();
        workers.shutdown();
        if (!workers.awaitTermination(durationSeconds + 60L, TimeUnit.SECONDS)) {
            workers.shutdownNow();
            throw new IllegalStateException("压测线程未在预期时间内结束");
        }
        for (Future<?> task : tasks) {
            try {
                task.get();
            } catch (ExecutionException e) {
                throw new IllegalStateException("压测工作线程异常", e.getCause());
            }
        }
        double elapsedSeconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
        if (elapsedSeconds < durationSeconds * 0.9) {
            throw new IllegalStateException("压测阶段过早结束：elapsed=" + elapsedSeconds
                    + "s, expected=" + durationSeconds + "s");
        }
        return elapsedSeconds;
    }

    private static void signalReady(LoadTestConfig config) throws Exception {
        Path readyFile = config.getReadyFile();
        if (readyFile == null) return;
        if (readyFile.getParent() != null) Files.createDirectories(readyFile.getParent());
        Files.write(readyFile, "ready".getBytes(StandardCharsets.UTF_8));
    }

    private static void backoffAfterImmediateRejection(String code) {
        if (!Constants.CIRCUIT_OPEN.equals(code)
                && !Constants.RATE_LIMITED.equals(code)
                && !Constants.NO_HEALTHY_NODE.equals(code)) {
            return;
        }
        long backoffMillis = Math.max(0L, Long.getLong("rpc.load.rejection.backoff.ms", 1L));
        if (backoffMillis > 0) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(backoffMillis));
        }
    }

    private static void awaitProvider() throws InterruptedException {
        int timeoutSeconds = Integer.getInteger("rpc.load.readiness.timeout.seconds", 30);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        String serviceName = UserRemote.class.getName();
        while (System.nanoTime() < deadline) {
            try {
                NettyClient.refreshServices();
            } catch (Exception refreshError) {
                System.err.println("刷新服务发现失败，继续等待：" + refreshError.getMessage());
            }
            if (ChannelManager.hasActiveNode(serviceName)) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(500);
        }
        throw new IllegalStateException("等待 Provider 就绪超时：" + serviceName);
    }
}
