package com.rpc.service.loadtest;

import com.rpc.api.protocol.Response;
import com.rpc.client.constants.Constants;
import com.rpc.client.core.RpcClientStats;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoadTestToolTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void clearProperties() {
        System.clearProperty("rpc.load.output.dir");
        System.clearProperty("rpc.load.warmup.seconds");
    }

    @Test
    public void modesProvideExpectedDefaults() {
        LoadTestConfig smoke = LoadTestConfig.parse(new String[]{"smoke"});
        LoadTestConfig benchmark = LoadTestConfig.parse(new String[]{"benchmark"});
        LoadTestConfig stress = LoadTestConfig.parse(new String[]{"stress"});

        assertEquals(2, smoke.getConcurrency());
        assertEquals(10, smoke.getDurationSeconds());
        assertEquals(32, benchmark.getConcurrency());
        assertEquals(30, benchmark.getDurationSeconds());
        assertEquals(64, stress.getConcurrency());
        assertEquals(180, stress.getDurationSeconds());
    }

    @Test
    public void calculatesAllRequiredMetricsFromRecordedCalls() {
        LoadTestStatistics statistics = new LoadTestStatistics();
        statistics.record(response(Constants.SUCCESS), 1_000, false);
        statistics.record(response(Constants.TIMEOUT), 2_000, false);
        statistics.record(response(Constants.RATE_LIMITED), 3_000, true);
        statistics.record(response(Constants.CIRCUIT_OPEN), 4_000, true);
        statistics.recordException(5_000, true);

        RpcClientStats.Snapshot before = RpcClientStats.snapshot();
        LoadTestResult result = statistics.toResult(
                LoadTestConfig.parse(new String[]{"smoke"}), 1.0,
                RpcClientStats.snapshot().minus(before));

        assertEquals(5, result.getTotal());
        assertEquals(1, result.getSuccess());
        assertEquals(4, result.getFailure());
        assertEquals(3.0, result.getAverageMillis(), 0.001);
        assertEquals(1, result.getTimeouts());
        assertEquals(1, result.getRateLimited());
        assertEquals(1, result.getCircuitRejected());
        assertEquals(1, result.getExceptions());
        assertEquals(3, result.getPostFaultTotal());
        assertEquals(0, result.getPostFaultSuccess());
        assertTrue(result.getP50Millis() >= 3.0);
        assertTrue(result.getP99Millis() >= 5.0);
    }

    @Test
    public void appendsCsvWithSingleHeader() throws Exception {
        Path output = temporaryFolder.newFolder("results").toPath();
        System.setProperty("rpc.load.output.dir", output.toString());
        LoadTestConfig config = LoadTestConfig.parse(new String[]{"smoke"});
        LoadTestStatistics statistics = new LoadTestStatistics();
        statistics.record(response(Constants.SUCCESS), 1_000, false);
        RpcClientStats.Snapshot before = RpcClientStats.snapshot();
        LoadTestResult result = statistics.toResult(
                config, 1.0, RpcClientStats.snapshot().minus(before));

        Path csv = result.appendCsv();
        result.appendCsv();
        List<String> lines = Files.readAllLines(csv);

        assertEquals(3, lines.size());
        assertTrue(lines.get(0).startsWith("timestamp,mode,fault_mode"));
        assertTrue(lines.get(1).contains("\"smoke\""));
    }

    private Response response(String code) {
        Response response = new Response();
        response.setCode(code);
        return response;
    }
}
