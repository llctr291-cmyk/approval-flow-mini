package com.approvalflow.service;

import com.approvalflow.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalEngineTest {
    private final ApprovalEngine engine = new ApprovalEngine();

    @Test
    void anyCounterConvergesOnceUnderConcurrentActionsAndIdempotency() throws Exception {
        ApprovalInstance instance = engine.create("alice", List.of(
                new ApprovalNode("n1", CounterMode.ANY, List.of("boss", "manager")),
                new ApprovalNode("n2", CounterMode.ALL, List.of("director"))
        ));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        Future<?> a = pool.submit(() -> {
            ready.countDown();
            return engine.act(instance.getId(), instance.getTasks().get(0).getId(),
                    Decision.APPROVE, "boss", "key-boss");
        });
        Future<?> b = pool.submit(() -> {
            ready.countDown();
            return engine.act(instance.getId(), instance.getTasks().get(1).getId(),
                    Decision.APPROVE, "manager", "key-manager");
        });
        ready.await();
        a.get();
        b.get();
        pool.shutdown();

        assertEquals(1, instance.getCurrentNodeIndex());
        assertEquals(ApprovalStatus.RUNNING, instance.getStatus());
        assertEquals(instance, engine.act(instance.getId(), instance.getTasks().get(0).getId(),
                Decision.APPROVE, "boss", "key-boss"));
    }

    @Test
    void allCounterWaitsForEveryApproverAndRollbackRestoresNode() {
        ApprovalInstance instance = engine.create("bob", List.of(
                new ApprovalNode("n1", CounterMode.ALL, List.of("a", "b"))
        ));
        String taskA = instance.getTasks().get(0).getId();
        String taskB = instance.getTasks().get(1).getId();

        engine.act(instance.getId(), taskA, Decision.APPROVE, "a", "approve-a");
        assertEquals(ApprovalStatus.RUNNING, instance.getStatus());
        engine.act(instance.getId(), taskB, Decision.APPROVE, "b", "approve-b");
        assertEquals(ApprovalStatus.APPROVED, instance.getStatus());
    }

    @Test
    void dynamicAddSignAndCasRollback() {
        ApprovalInstance instance = engine.create("carol", List.of(
                new ApprovalNode("n1", CounterMode.ALL, List.of("a")),
                new ApprovalNode("n2", CounterMode.ALL, List.of("b"))
        ));
        engine.addSign(instance.getId(), "n2", "c", "add-c");
        assertTrue(instance.getTasks().stream().anyMatch(t -> t.getAssignee().equals("c")));

        long version = instance.getVersion();
        engine.rollback(instance.getId(), "n1", "missing evidence", version, "rollback-1");
        assertEquals(0, instance.getCurrentNodeIndex());
        assertThrows(CasConflictException.class,
                () -> engine.rollback(instance.getId(), "n1", "stale",
                        version, "rollback-stale"));
        assertFalse(engine.eventsAfter(instance.getId(), 0).isEmpty());
    }
}
