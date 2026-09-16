# Mobile Harness (Fork) - Project Context

> Autonomous AI development workspace for Android (PRoot, Claude Code, GitHub OAuth, Dev Dashboard).

Fork of [techjarves/Mobile-Harness](https://github.com/techjarves/Mobile-Harness) with automated GitHub Releases and bugfixes. Managed via VibeWorks: https://vibeworks.morncloud.de/projects/cmu1h6t3p02rzspn3wx1p165j

## Tech Stack
- Kotlin 2.2.21, Jetpack Compose (Material 3), AGP 8.13.2, Gradle Kotlin DSL
- PRoot Ubuntu userspace runtime (third_party/proot, libandroid-shmem as git submodules)
- OkHttp, Android Keystore (ApiKeyVault), Storage Access Framework
- CI: GitHub Actions (build + release), Fastlane metadata
- Android 9+ (minSdk 28), arm64-v8a only, app id `com.jarves.mh`, current version 1.0.5 (versionCode 6)

## Key Features (R1-R5, all implemented)
1. **R1 Message backup**: `data/MessageBackupManager.kt` writes `message_backups.json` to filesDir; `MainViewModel.sendPrompt` halts with error state on backup failure; `TerminalScreen` shows the error above the input (`WindowInsets.safeDrawing`).
2. **R2 GitHub OAuth**: `singleTask` MainActivity + deep link `mobileharness://github-callback`; `data/GitHubAuthManager.kt` exchanges the code for a token, stored via `ApiKeyVault`.
3. **R3 Dev dashboard**: `ui/GitHubDevDashboardScreen.kt` (OkHttp) shows failed workflow runs and CodeRabbit PR reviews; dedicated workspace tab.
4. **R4 Command auto-suggest & /compact**: `AssistChip` suggestions on "/" in `ui/TerminalScreen.kt`; `/compact` runs silently via `MainViewModel.executeSilentCommand`.
5. **R5 MCP & skills config**: `data/McpSkillManager.kt` manages `.mcp.json` and `.claude/commands/*.md` in the PRoot workspace with explicit permissions; accordion in `ui/SettingsScreenModern.kt`.

## Build & Test (mandatory verification)
The app has product flavors `online` and `offline` - `testDebugUnitTest` is ambiguous.

```bash
gradlew :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest :app:assembleOnlineDebug
```

Baseline: 33 unit tests per flavor, 0 failures + BUILD SUCCESSFUL.

## Current State (2026-09-16)
- **Current release: v1.0.5** (2026-09-15, commit 8261ed2, CI run #14 green): CI downloads the 3 runtime bundles from upstream release runtime-2026.09.4 (SHA-256 verified) before building; offline APK ~782 MB with `assets/offline/runtime/*` fully bundled (online APK ~44 MB); versionCode 6 / versionName 1.0.5.
- Release history: v1.0.4 (2026-09-14, commits d858494 + aa51dd2, CI runs #12/#13 green), v1.0.3 and earlier.
- R1-R5 implemented, all unit tests green (33/33 per flavor), assembleOnlineDebug BUILD SUCCESSFUL.
- Repo-Check: 5 semgrep findings verified as false positives and silenced with official `nosemgrep` annotations (commit 1e40305, Build + Repo-Check both green) - stops the recurring repo-check task loop. Remaining legitimate alert: the DeepSeek key in the git history (Issue-Hub #3, awaiting upstream rotation).
- Known MCP limitation: `get_code_graph` returns `graph.errors.noFiles` (Issue-Hub #5, upstream bug) - use `search_code` + file memos as the code-network ground truth until fixed.
- MCP setup facts (endpoint, `Authorization: Bearer vw_...`, 401 codes, traps): VibeWorks doc "VibeWorks MCP setup (verified facts for agents)" and Issue-Hub `docs/vibeworks-mcp-setup.md`.

## Workflow (see .clinerules/workflow.md)
- ALWAYS check for changes first (git status + log), then sync VibeWorks MCP (project "Harness", formerly "Mein Projekt").
- VibeWorks FIRST (2026-09-16): pre-register every local change in VibeWorks (task DOING or decision note) BEFORE editing files locally; update records after every change.
- Mandatory (vendor rules 2026-09-16): no work without a task; status always true (DOING + assignee, BLOCKED with reason, DONE only when finished); end every reply by checking open tasks; work through tasks completely highest-first; recurring tasks - DONE creates the next occurrence, never complete the new occurrence to clear the list.
- Code network first for code facts (anti-hallucination): `list_code_files` at session start (refreshes the repo copy), `search_code`/`get_code_graph` before code claims, durable "why" knowledge as file memos (`add_code_memo`). Never reconstruct ids/line numbers from truncated output - re-fetch.
- Repo-check loop: close the individual finding tasks VibeWorks creates (English risk explanation) - notes alone cause re-creation.
- ALL artifacts in ENGLISH - including every VibeWorks record (project fields, tasks, notes). Keep VibeWorks always up to date after every change.
- Task lifecycle: DOING on start, DONE on finish. Decisions as project notes.
- Error Inbox (since 2026-09-15): write-only ingest URL `https://vibeworks.morncloud.de/api/errors/in/_xHuKoZj2qQTFQdRCjEMPB_XkiGETNX8` (key in URL = write-only, rotate on abuse). Check `list_errors` after CI runs/changes; resolve fixed errors. Recommended variant: Script/curl (Android app: native crash reporter).
- ALWAYS review CI runs via VibeWorks after every push: `get_repo_status` + `list_problems` (red CI), `list_errors` in the same pass.
- Operational gotchas (PowerShell traps, background helpers, session hygiene) and the release recipe: see .clinerules/workflow.md sections 6-8.
- Repo & issue separation (since 2026-09-15): GitHub issues tracked centrally in JONIMONI09/Issue-Hub; forks (JONIMONI09/vibeworks) carry no issues; cross-project analyses live in Issue-Hub/docs (the vibeworks 401 analysis was moved out of this repo at commit 77d3175). See .clinerules/workflow.md section 9.
- Issue-Hub upkeep is a standing duty (2026-09-16): every upstream finding gets an Issue-Hub issue + docs analysis + watchlist entry; check open Issue-Hub issues at session start; close only with a resolution comment; at session end every cross-repo finding has an issue.
- VibeWorks MCP key was rotated 2026-09-15 evening (new key verified, HTTP 200); keys live only in cline_mcp_settings.json - on 401: new key in VibeWorks UI, swap header, verify with initialize curl.
- Vendor agent-rules sync (2026-09-16): when VibeWorks says the agent rules changed, re-fetch `get_agent_rules`, merge into `.clinerules/vibeworks.md` (keep English-records override), call `confirm_agent_rules`, and sync all rule files in the same session.
