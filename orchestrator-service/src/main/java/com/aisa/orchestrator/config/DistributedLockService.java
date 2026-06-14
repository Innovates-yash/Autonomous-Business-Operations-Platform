package com.aisa.orchestrator.config;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.stereotype.Component;

/**
 * Utility for acquiring Redis-backed distributed locks in the orchestrator service.
 *
 * <h2>Load Distribution Design (Requirements 26.2, 26.3, 26.4)</h2>
 *
 * <p>This service ensures fair work distribution across orchestrator replicas by
 * guarding agent step dispatch with per-step locks keyed by {@code runId:agentType}.
 * The design achieves the ≤20% above-mean load distribution target as follows:
 *
 * <ol>
 *   <li><strong>Per-step locking:</strong> Each agent invocation is guarded by a unique
 *       lock ({@code aisa:lock:orchestrator:<runId>:<agentType>}). When multiple
 *       orchestrator replicas consume from the agent-tasks Kafka topic, only one
 *       instance acquires the lock and processes the step, preventing duplicate work.</li>
 *   <li><strong>Short lock acquisition window:</strong> {@code tryLock} with a bounded
 *       wait (default 5s) ensures replicas that lose contention quickly move on to the
 *       next available task from Kafka — Kafka's partition-based consumer balancing
 *       distributes work across replicas evenly.</li>
 *   <li><strong>Crash safety:</strong> Lock TTL = 120s (matching agent timeout, Req 6.4).
 *       On instance crash, the lock auto-expires so another replica re-queues the work
 *       within 30s (Req 26.6).</li>
 *   <li><strong>Stateless services + Redis shared state:</strong> No replica holds
 *       in-memory assignment state. All coordination is via Redis keys, so any instance
 *       serves any request — satisfying the ≤5s new-project-creation target under 100
 *       concurrent generations (Req 26.4) by eliminating single-instance bottlenecks.</li>
 * </ol>
 *
 * <p>The ≤20% fairness and ≤5s latency targets are validated by integration/load tests
 * in task 31 (performance validation). This utility provides the building block.
 *
 * @see RedisLockConfiguration
 */
@Component
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);

    /** Default time to wait for lock acquisition before giving up. */
    private static final long DEFAULT_WAIT_MS = 5_000L;

    private final RedisLockRegistry lockRegistry;

    public DistributedLockService(RedisLockRegistry lockRegistry) {
        this.lockRegistry = lockRegistry;
    }

    /**
     * Attempts to acquire a distributed lock for the given key, executes the action,
     * and releases the lock afterwards.
     *
     * @param lockKey  unique key identifying the resource to lock (e.g., "runId:agentType")
     * @param action   the work to perform while holding the lock
     * @param <T>      the action's return type
     * @return the result of the action, or {@code null} if the lock was not acquired
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        return executeWithLock(lockKey, DEFAULT_WAIT_MS, action);
    }

    /**
     * Attempts to acquire a distributed lock with a custom wait timeout.
     *
     * @param lockKey  unique key identifying the resource to lock
     * @param waitMs   maximum milliseconds to wait for lock acquisition
     * @param action   the work to perform while holding the lock
     * @param <T>      the action's return type
     * @return the result of the action, or {@code null} if the lock was not acquired
     */
    public <T> T executeWithLock(String lockKey, long waitMs, Supplier<T> action) {
        Lock lock = lockRegistry.obtain(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.debug("Could not acquire lock for key={} within {}ms — another instance owns it",
                        lockKey, waitMs);
                return null;
            }
            log.debug("Acquired lock for key={}", lockKey);
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for lock key={}", lockKey);
            return null;
        } finally {
            if (acquired) {
                lock.unlock();
                log.debug("Released lock for key={}", lockKey);
            }
        }
    }

    /**
     * Attempts to acquire a distributed lock and executes a void action.
     *
     * @param lockKey unique key identifying the resource to lock
     * @param action  the work to perform while holding the lock
     * @return {@code true} if the lock was acquired and the action executed
     */
    public boolean executeWithLock(String lockKey, Runnable action) {
        return executeWithLock(lockKey, DEFAULT_WAIT_MS, action);
    }

    /**
     * Attempts to acquire a distributed lock with a custom wait timeout and
     * executes a void action.
     *
     * @param lockKey unique key identifying the resource to lock
     * @param waitMs  maximum milliseconds to wait for lock acquisition
     * @param action  the work to perform while holding the lock
     * @return {@code true} if the lock was acquired and the action executed
     */
    public boolean executeWithLock(String lockKey, long waitMs, Runnable action) {
        Lock lock = lockRegistry.obtain(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.debug("Could not acquire lock for key={} within {}ms — another instance owns it",
                        lockKey, waitMs);
                return false;
            }
            log.debug("Acquired lock for key={}", lockKey);
            action.run();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for lock key={}", lockKey);
            return false;
        } finally {
            if (acquired) {
                lock.unlock();
                log.debug("Released lock for key={}", lockKey);
            }
        }
    }
}
