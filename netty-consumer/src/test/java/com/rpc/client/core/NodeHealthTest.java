package com.rpc.client.core;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
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
        AtomicLong now = new AtomicLong();
        NodeHealth health = new NodeHealth(now::get);
        boolean opened = false;

        for (int i = 0; i < RpcClientConfig.CIRCUIT_FAILURE_THRESHOLD + 10; i++) {
            assertTrue(health.tryAcquire());
            now.addAndGet(RpcClientConfig.SLOW_CALL_MILLIS + 1);
            if (health.complete(true, RpcClientConfig.SLOW_CALL_MILLIS * 2)) {
                opened = true;
                break;
            }
        }

        assertTrue(opened);
        assertFalse(health.tryAcquire());
    }

    @Test
    public void simultaneousSlowCompletionsAreTreatedAsTransientJitter() {
        AtomicLong now = new AtomicLong();
        NodeHealth health = new NodeHealth(now::get);
        int calls = RpcClientConfig.CIRCUIT_FAILURE_THRESHOLD + 8;
        for (int i = 0; i < calls; i++) {
            assertTrue(health.tryAcquire());
        }

        for (int i = 0; i < calls; i++) {
            assertFalse(health.complete(true, RpcClientConfig.SLOW_CALL_MILLIS * 2));
        }

        assertTrue(health.tryAcquire());
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

    @Test
    public void concurrentFailuresOpenCircuitOnlyOnce() {
        NodeHealth health = new NodeHealth();
        int calls = RpcClientConfig.CIRCUIT_FAILURE_THRESHOLD + 8;
        for (int i = 0; i < calls; i++) {
            assertTrue(health.tryAcquire());
        }

        int openEvents = 0;
        for (int i = 0; i < calls; i++) {
            if (health.complete(false, 10)) {
                openEvents++;
            }
        }

        assertEquals(1, openEvents);
        assertFalse(health.tryAcquire());
    }

    @Test
    public void staleSuccessDoesNotCloseOpenCircuit() {
        NodeHealth health = new NodeHealth();
        int calls = RpcClientConfig.CIRCUIT_FAILURE_THRESHOLD + 1;
        for (int i = 0; i < calls; i++) {
            assertTrue(health.tryAcquire());
        }
        for (int i = 0; i < RpcClientConfig.CIRCUIT_FAILURE_THRESHOLD; i++) {
            health.complete(false, 10);
        }

        health.complete(true, 1);

        assertFalse(health.tryAcquire());
    }
}
