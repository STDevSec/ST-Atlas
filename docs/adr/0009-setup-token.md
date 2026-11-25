# 0009 — One-time setup token

**Context.** A fresh instance has no administrator. Whoever reaches the setup
wizard first owns the installation.

**Decision.** At start-up, if setup is pending, the backend generates a
random 128-bit token, stores only its hash, and prints the token to its log
(`docker compose logs backend`). `POST /setup` requires it. The token is
discarded once setup completes; setup endpoints return 404 afterwards.

**Consequences.** One extra step for the owner (copy a token from the log);
an exposed fresh instance cannot be hijacked by a stranger.
