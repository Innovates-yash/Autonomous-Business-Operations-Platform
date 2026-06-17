package com.aisa.commons.redis;

/**
 * Central registry of Redis key-prefix constants used across platform services.
 *
 * <p>Each service owns its own key space under the {@code aisa:} root. Using these constants
 * in configuration classes ensures namespace consistency and prevents accidental collision.
 *
 * <p>This class is intentionally a constants holder (no instantiation).
 *
 * @see <a href="../../../../docs/scalability/redis-usage-strategy.md">Redis Usage Strategy</a>
 */
public final class RedisNamespaces {

    private RedisNamespaces() {
        // utility class
    }

    // ---- auth-service: session management ----

    /** Spring Session namespace for auth-service HTTP sessions. TTL: 30 min. */
    public static final String SESSION_AUTH = "aisa:session:auth";

    // ---- api-gateway: rate limiting ----

    /** Rate-limit counter namespace. Keys: principal + window. TTL: 60 s. */
    public static final String RATELIMIT_GATEWAY = "aisa:ratelimit:gateway";

    // ---- ai-provider-gateway: provider selection cache ----

    /** Provider selection and availability cache. TTL: 5 s. */
    public static final String CACHE_PROVIDER = "aisa:cache:provider";

    // ---- project-service: read-through cache ----

    /** Project metadata read cache. TTL: 5 min. */
    public static final String CACHE_PROJECT = "aisa:cache:project";

    // ---- ai-chat-service: context window ----

    /** 20-message rolling context window per conversation. TTL: 24 h. */
    public static final String CHAT_CONTEXT = "aisa:chat:context";

    // ---- orchestrator-service: distributed locks ----

    /** Distributed lock namespace for saga step coordination. TTL: 120 s. */
    public static final String LOCK_ORCHESTRATOR = "aisa:lock:orchestrator";

    // ---- notification-service: WebSocket subscription state ----

    /** Active WebSocket subscription registry per user. TTL: 1 h after last heartbeat. */
    public static final String NOTIFY_SUBSCRIPTIONS = "aisa:notify:subscriptions";

    /** Last-delivered event offset per project for reconnect resync. TTL: 24 h. */
    public static final String NOTIFY_FANOUT = "aisa:notify:fanout";
}
