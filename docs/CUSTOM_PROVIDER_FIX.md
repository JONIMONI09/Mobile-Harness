# Bug Report & Documentation: Custom Provider Enhancements & Fixes

**Repository:** JONIMONI09/Mobile-Harness (fork of techjarves/Mobile-Harness)  
**Status:** ✅ Fully Resolved & Verified — All unit tests green, APK builds and deploys successfully  

---

## 1. Executive Summary

Custom API endpoints (such as self-hosted proxies, FastAPI/Express gateways, LiteLLM, Ollama, vLLM, OpenRouter, and `b.ai`/Qwen endpoints) previously failed with misleading error messages such as:
- *"The API key was rejected"*
- *"The API endpoint was not found"*
- *"Access forbidden (HTTP 403)"*
- *"The provider returned HTTP 400 (max_tokens must be greater than 2)"*

These errors occurred even when the user provided a completely valid API key and URL.

This document details the root causes identified, technical solutions implemented, and verification results across all unit test suites.

---

## 2. Root Cause Analysis

### Bug 1: Path Appending and Doubled `/v1` Segments
* **Problem:** `ProviderApiClient.messagesEndpoint()` blindly appended `/v1/messages` or `/v1/chat/completions` to any provided base URL.
* **Impact:** If a user entered a base URL already ending in `/v1` or `/v1/chat` (e.g., `https://api.b.ai/v1/chat`), candidate URLs evaluated to invalid paths like `https://api.b.ai/v1/chat/chat/completions` or `https://api.b.ai/v1/v1/messages`, triggering HTTP 404 or HTTP 403 WAF rejection rules.

### Bug 2: Gateways Enforcing Minimum Token Limits (`max_tokens > 2`)
* **Problem:** To validate connections before saving, `ProviderApiClient.validationBody()` sent a minimal payload containing `"max_tokens": 1`.
* **Impact:** Strict gateways (such as `b.ai` / Qwen) enforce minimum token limits (e.g., `"max_tokens must be greater than 2"`). Sending `max_tokens = 1` triggered an HTTP 400 Bad Request error during validation.

### Bug 3: Premature Cross-Protocol Fallback Bails on HTTP 401
* **Problem:** Custom OpenAI-compatible endpoints returned HTTP 401 when tested with Anthropic `/v1/messages` formatting. The validation logic flagged `authRejected = true` and skipped the OpenAI cross-protocol fallback check.
* **Impact:** Users were informed that their API key was rejected, when in reality the key was valid and only the protocol format differed.

### Bug 4: Generic / Obscured Error Detail Display
* **Problem:** When providers returned HTTP 401, 403, or 400 responses with detailed error JSON objects, the client stripped the detail message and displayed generic text ("The API key was rejected").
* **Impact:** Users could not identify gateway policy restrictions, model mismatches, or IP whitelist issues.

### Bug 5: Key Formatting and User-Agent Rejections
* **Problem:** Copy-pasting keys with trailing whitespace, newlines, or duplicate `"Bearer "` prefixes caused header duplication (`Authorization: Bearer Bearer sk-...`). Requests lacking a standard `User-Agent` were also blocked by Cloudflare / WAF rules with HTTP 403.

---

## 3. Implemented Technical Fixes

### 1. Automatic Base URL Normalization (`normalizeBaseUrl`)
* Added `normalizeBaseUrl()` in `ProviderApiClient.kt` and `RuntimeBridge.kt`.
* Automatically strips trailing endpoint path suffixes (`/chat/completions`, `/chat`, `/completions`, `/messages`, `/responses`) if entered into the Base URL field.
* Prevents path doubling while preserving essential path segments.

### 2. Standardized Minimum Test Tokens (`max_tokens = 16`)
* Updated `validationBody()` in `ProviderApiClient.kt` to send `"max_tokens": 16` and `"max_output_tokens": 16`.
* Satisfies strict provider gateways enforcing `max_tokens > 2` (such as `b.ai` / Qwen) while remaining lightweight and fast.

### 3. Smart Cross-Protocol Auto-Detection & Profile Switch
* Implemented protocol auto-probing in `ProviderApiClient.kt`.
* When a custom provider test fails on the primary protocol (including HTTP 401/404), the client automatically tests the alternate protocol (`OPENAI_CHAT` ↔ `ANTHROPIC_GATEWAY`).
* If the alternate protocol succeeds (HTTP 200), `validate()` flags the detected format, and `MainViewModel.kt` automatically updates `profile.kind` to `CUSTOM_OPENAI` or `CUSTOM` upon saving.

### 4. Comprehensive Error Detail Extraction (`friendlyHttpError`)
* Enhanced JSON response parsing in `ProviderApiClient.kt` to extract `error.message`, `error.detail`, or root `message`/`detail` fields.
* Formats user-facing error messages as: `HTTP <code\> Detail: <provider_message\>`.

### 5. Key Sanitization and Standard User-Agent Header
* Sanitizes API keys across `ProviderApiClient.kt`, `MainViewModel.kt`, and `LocalFormatGateway.kt` by trimming whitespace/newlines and removing duplicate `Bearer ` prefixes.
* Sets `User-Agent: MobileHarness/1.0 (Android)` on all outbound HTTP requests.

---

## 4. Modified Source Files

| File | Changes Made |
|---|---|
| `app/src/main/java/com/jarves/mh/network/ProviderApiClient.kt` | Added `normalizeBaseUrl()`, updated `validationBody()` (`max_tokens = 16`), updated `probeProtocol()` with cross-protocol fallback, enhanced `friendlyHttpError()`, added `User-Agent` header, and sanitized keys. |
| `app/src/main/java/com/jarves/mh/runtime/RuntimeBridge.kt` | Added `normalizeAnthropicBaseUrl()` to strip trailing `/v1` before configuring `ANTHROPIC_BASE_URL` for Claude Code. |
| `app/src/main/java/com/jarves/mh/runtime/LocalFormatGateway.kt` | Handled base URL normalization and key sanitization for local loopback proxy requests. |
| `app/src/main/java/com/jarves/mh/ui/MainViewModel.kt` | Added automatic profile kind conversion in `validateProvider()` upon protocol auto-detection. |
| `app/src/main/java/com/jarves/mh/model/Models.kt` | Verified `CUSTOM` and `CUSTOM_OPENAI` provider configurations and metadata. |
| `app/src/test/java/com/jarves/mh/network/CustomProviderEndpointTest.kt` | Updated candidate path assertions and added unit test cases. |

---

## 5. Verification & Test Suite Results

All unit tests pass across online and offline build variants:

```bash
./gradlew :app:testOnlineDebugUnitTest
# Result: 19 passed, 0 failed

./gradlew :app:testOfflineDebugUnitTest
# Result: 19 passed, 0 failed

./gradlew :app:assembleOnlineDebug
# Result: BUILD SUCCESSFUL
```

---

## 6. Verification Steps for End Users

1. Open **Settings → AI Provider & Settings**.
2. For OpenAI-compatible endpoints (e.g. `https://api.b.ai/v1` or `https://api.b.ai/v1/chat`):
   - **Base URL:** `https://api.b.ai/v1` (or paste full endpoint; auto-normalized)
   - **Model:** Select from discovered model list or type exact model ID (e.g. `qwen3.8-flash`)
   - **API Key:** Paste your key
3. Tap **Find available models** or **Continue**.
4. The connection test verifies successfully and saves the provider credentials securely.
