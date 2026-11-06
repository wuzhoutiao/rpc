package com.rpc.client.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NodeHealthTest {

    @Test
    public void opensCircuitAfterConsecutiveFailures() {
        NodeHealth health = new NodeHealth();

        for (int i = 0; i < RpcClientConfig.CIRCUIT_FAILURE_THRESHOLD; i++) {
            assertTrue(health.tryAcquire());
            health.complete(false, 10);
        }

        assertFalse(health.tryAcquire());
    }

    @Test
    public void slowCallsAreTreatedAsUnhealthy() {
        NodeHealth health = new NodeHealth();

        for (int i = 0; i < RpcClientConfig.CIRCUIT_FAILURE_THRESHOLD; i++) {
            assertTrue(health.tryAcquire());
            health.complete(true, RpcClientConfig.SLOW_CALL_MILLIS + 1);
        }

        assertFalse(health.tryAcquire());
    }

    @Test
    public void healthyCallResetsFailureStreak() {
        NodeHealth health = new NodeHealth();
        assertTrue(health.tryAcquire());
        health.complete(false, 10);
        assertTrue(health.tryAcquire());
        health.complete(true, 10);

        for (int i = 1; i < RpcClientConfig.CIRCUIT_FAILURE_THRESHOLD; i++) {
            assertTrue(health.tryAcquire());
            health.complete(false, 10);
        }

        assertTrue(health.tryAcquire());
    }
}
