# Session Strategy: JWT + Redis

> Requirements: 1.3, 1.4, 2.4, 26.2

## Decision: JWT Handles Session State — Spring Session Redis Is Not Required

The platform uses **stateless JWT access tokens** for request authentication across all
services. There is no server-side HTTP session to replicate or share, making Spring Session
Redis (`@EnableRedisHttpSession`) unnecessary for the API layer.

### Why JWT Is Sufficient

| Concern | How JWT Addresses It |
|---------|---------------------|
| Authentication state | Encoded in the JWT; validated at the API Gateway and by each service |
| Session expiry | JWT TTL = 15 minutes (Req 1.4); refresh token TTL = 7 days (Req 1.5) |
| Cross-instance sharing | Stateless — any instance validates the token via the signing key |
| Logout / invalidation | Refresh token revoked in MySQL; short-lived access token expires naturally |
| Role changes | Auth-service evicts cached permissions in Redis (Req 2.13, 2.14) |

### Where Redis Is Used Instead of Sessions

Rather than full HTTP sessions, the platform uses Redis for targeted shared state:

| Service | Redis Purpose | Replaces |
|---------|--------------|----------|
| auth-service | Role/permission decision cache (`aisa:cache:auth:`) | Server-side session with permissions |
| api-gateway | Rate-limit counters (`aisa:ratelimit:gateway:`) | Session-based throttle |
| ai-chat-service | 20-message context window (`aisa:chat:context:`) | Session-scoped conversation buffer |
| notification-service | WebSocket subscription registry (`aisa:notify:subscriptions:`) | Session-scoped WS state |

### When Spring Session Redis Would Be Needed

If the platform adds a traditional web UI with form-based login and CSRF tokens (not
currently planned), Spring Session Redis would be the correct choice. For the current
JWT-based API architecture, the combination of:

- Short-lived JWTs (stateless per-request auth)
- Redis-backed authorization caches (fast permission lookups)
- Redis-backed rate limiting (per-client enforcement)

achieves the same horizontal scaling benefit without the overhead of full session
serialization.

### Configuration Reference

```yaml
# NOT configured — documented here for clarity:
# spring:
#   session:
#     store-type: redis
#     redis:
#       namespace: aisa:session:auth
#
# The auth-service's @EnableRedisHttpSession is reserved for potential future use
# with an admin dashboard that uses form-based login. For the API layer, JWT tokens
# replace sessions entirely.
```

## References

- Design Document §Auth Service: "Access tokens are short-lived JWTs"
- Requirement 1.3: JWT issuance on login
- Requirement 1.4: 15-minute JWT TTL
- Requirement 26.2: Shared state externalized to Redis
