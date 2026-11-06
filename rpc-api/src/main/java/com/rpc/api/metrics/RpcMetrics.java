package com.rpc.api.metrics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/** 无额外运行时依赖的 Prometheus 文本格式指标注册表。 */
public final class RpcMetrics {

    private static final ConcurrentHashMap<MetricKey, LongAdder> COUNTERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<MetricKey, SummaryValue> SUMMARIES = new ConcurrentHashMap<>();

    private RpcMetrics() {
    }

    public static void increment(String name, Map<String, String> labels) {
        COUNTERS.computeIfAbsent(new MetricKey(name, labels), ignored -> new LongAdder()).increment();
    }

    public static void observeMillis(String name, Map<String, String> labels, long millis) {
        SummaryValue value = SUMMARIES.computeIfAbsent(
                new MetricKey(name, labels), ignored -> new SummaryValue());
        value.count.increment();
        value.sum.add(millis);
    }

    public static Endpoint startEndpoint(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rpc-prometheus-" + port);
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/metrics", RpcMetrics::handleMetrics);
        server.start();
        return new Endpoint(server, executor);
    }

    private static void handleMetrics(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        byte[] body = render().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    static String render() {
        StringBuilder output = new StringBuilder();
        COUNTERS.forEach((key, value) -> output.append(key.name).append(key.labelsText())
                .append(' ').append(value.sum()).append('\n'));
        SUMMARIES.forEach((key, value) -> {
            output.append(key.name).append("_count").append(key.labelsText())
                    .append(' ').append(value.count.sum()).append('\n');
            output.append(key.name).append("_sum").append(key.labelsText())
                    .append(' ').append(value.sum.sum()).append('\n');
        });
        Runtime runtime = Runtime.getRuntime();
        output.append("rpc_jvm_memory_used_bytes ")
                .append(runtime.totalMemory() - runtime.freeMemory()).append('\n');
        output.append("rpc_jvm_threads ").append(Thread.activeCount()).append('\n');
        return output.toString();
    }

    public static final class Endpoint implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private Endpoint(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class SummaryValue {
        private final LongAdder count = new LongAdder();
        private final DoubleAdder sum = new DoubleAdder();
    }

    private static final class MetricKey {
        private final String name;
        private final Map<String, String> labels;

        private MetricKey(String name, Map<String, String> labels) {
            this.name = name;
            this.labels = labels == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new TreeMap<>(labels));
        }

        private String labelsText() {
            if (labels.isEmpty()) {
                return "";
            }
            List<String> names = new ArrayList<>(labels.keySet());
            Collections.sort(names);
            StringBuilder text = new StringBuilder("{");
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) text.append(',');
                String name = names.get(i);
                text.append(name).append("=\"")
                        .append(labels.get(name).replace("\\", "\\\\").replace("\"", "\\\""))
                        .append('"');
            }
            return text.append('}').toString();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof MetricKey)) return false;
            MetricKey other = (MetricKey) object;
            return name.equals(other.name) && labels.equals(other.labels);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + labels.hashCode();
        }
    }
}
