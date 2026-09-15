# VibeWorks MCP 401 Incident Analysis (2026-09-15)

Analyzes the HTTP 401 `{"error":"Invalid or revoked API key."}` from `https://vibeworks.morncloud.de/api/mcp` and the simultaneous web logout. Based on the public source: github.com/MoinMornhart/vibeworks, commit `ce21d36` (v0.7.2), cloned 2026-09-15 ~18:23 CEST. Live probes were run against the instance the same evening.

## TL;DR
- **Nothing was lost.** The logout is the **8-hour idle timeout** (`SESSION_IDLE_HOURS`, default 8) deleting the browser session server-side. The MCP 401 means the specific `ApiToken` row is gone (revoked/deleted) or the account was deactivated - API keys have **no expiry**, so a key can never "time out".
- Recovery: log in again, create a new API key (Account -> API keys), update the MCP client Authorization header.

## Evidence (live checks, 2026-09-15)
| Check | Result |
|---|---|
| `GET /` (app root) | HTTP 200, login page rendered ("Anmelden") - session gone |
| `POST /api/mcp` with old key | HTTP 401 `{"error":"Invalid or revoked API key."}` |
| `POST /api/errors/in/_xHu...` (write-only ingest) | HTTP 202 `{"ok":true,"result":"new"}` - project intact |
| `POST /api/inbox/in/ZF5p...` | HTTP 201 with new item id - inbox intact |
| User data export `vibeworks-export-2026-09-15.json` (15:57 UTC, app 0.7.2) | all projects/tasks/notes/docs present |

## Root cause in code

### MCP authentication - src/lib/mcp/token.ts
- Keys are `vw_` + 32 random bytes; only the SHA-256 is stored (`ApiToken.tokenHash @unique`, prisma/schema.prisma L711) with a hint like `vw_AbCd...9f3a`.
- `authenticateApiToken()` (token.ts L24-37) returns null -> 401 in src/app/api/mcp/route.ts L26-30 for exactly three reasons:
  1. Bearer header malformed (must start with `vw_`, max 200 chars) - bearerOf() L17-21.
  2. **No ApiToken row with that SHA-256** - the key was revoked/deleted (`DELETE /api/account/api-tokens/[id]`, UI "Revoke" button with "Really revoke?" confirm).
  3. `user.active = false` - account deactivated (src/app/api/admin/users/[id]/route.ts ends all sessions when deactivating).
- **API keys have NO expiry** (no expiresAt column) - a 401 can never mean "key expired".
- `lastUsedAt` writes are throttled to 60 s (token.ts L33-35).

### Web session - src/lib/auth/session.ts
- Cookie `__Host-vw_session` (secure) / `vw_session`, httpOnly, sameSite=lax.
- Two TTLs (config.ts L33-37): absolute `SESSION_TTL_DAYS` default **30 days**, idle `SESSION_IDLE_HOURS` default **8 hours**.
- `validateSession()` (session.ts L74-92) **deletes the session row** when absolute-expired, idle-timed-out, or the user is inactive - the browser then lands on the login page with no explanation.
- `destroyAllSessions()` is also called on password change (api/account/password/route.ts) and on admin deactivation.

### Why both happened together
Not a data-loss event. Most plausible sequence: the ApiToken row disappeared (revoked via UI dialog, by an admin, or via the API) while the web session was later dropped by the 8 h idle timer (last UI activity before the logout; the 15:57 export proves the account was still active and the data intact). MCP clients fail with 401 from the moment the key row is gone; the UI gives no hint that a key changed.

## Findings & recommendations (for VibeWorks)
- **F1 - Silent idle logout (usability bug):** after 8 h idle the user is logged out without any message; the login page shows only the login form. Suggest: flash "Session expired" on the login redirect, optional warning banner before idle expiry, and document `SESSION_IDLE_HOURS` prominently.
- **F2 - Opaque MCP 401 (diagnosability):** one generic message covers three different causes (malformed / revoked / deactivated). Suggest: structured error codes (`token_revoked`, `account_inactive`, `malformed`) in the 401 body, plus an in-app/email notification when an ApiToken is deleted while its `lastUsedAt` is recent - that means a live MCP client was just cut off.
- **F3 - No token usage metadata:** sessions store IP/UA (session.ts L39-40) but ApiToken does not - a leaked or revoked key cannot be audited. Suggest: `lastUsedIp` / `lastUsedUserAgent` on ApiToken.
- **F4 - Docs gap:** the repo documents the `claude mcp add --transport http ...` command (api/mcp/route.ts L14 comment) but not key lifecycle (no expiry, immediate revocation, idle logout default 8 h). Suggest a "Keys & sessions" docs page.
- **F5 - Positive:** show-once secrets, SHA-256-only storage for tokens AND sessions, rate-limited key creation (20 per 10 min, api-tokens/route.ts L19), MAX_TOKENS = 20 - solid design.

## Recovery (verified against code)
1. Log in at https://vibeworks.morncloud.de - all data is intact.
2. Account -> API keys -> create a new key (format `vw_...`).
3. Update the MCP client Authorization header; verify with an MCP `initialize` request (expect HTTP 200).
4. If keys keep disappearing: check Admin -> Users (active flag) and Account -> Sessions for other devices.

## Related reports
- VibeWorks error inbox: incident report + analysis digest, both HTTP 202 on 2026-09-15.
- GitHub issue on MoinMornhart/vibeworks: see link in the session summary (created after duplicate check).
