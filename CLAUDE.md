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

## Current State (2026-09-14)
- R1-R5 implemented, all unit tests green (33/33 per flavor), assembleOnlineDebug BUILD SUCCESSFUL
- v1.0.4 released (2026-09-14, commits d858494 + aa51dd2, CI runs #12/#13 green, tagged release with signed APKs + update manifest)
- v1.0.5 shipped (2026-09-15, commit 8261ed2, CI run #14 green): CI downloads the 3 runtime bundles from upstream release runtime-2026.09.4 (SHA-256 verified) before building; offline APK now ~782 MB with `assets/offline/runtime/*` fully bundled (online APK ~44 MB); versionCode 6 / versionName 1.0.5

## Workflow (see .clinerules/workflow.md)
- ALWAYS check for changes first (git status + log), then sync VibeWorks MCP (project "Harness", formerly "Mein Projekt").
- ALL artifacts in ENGLISH - including every VibeWorks record (project fields, tasks, notes). Keep VibeWorks always up to date after every change.
- Task lifecycle: DOING on start, DONE on finish. Decisions as project notes.
- Error Inbox (since 2026-09-15): write-only ingest URL `https://vibeworks.morncloud.de/api/errors/in/_xHuKoZj2qQTFQdRCjEMPB_XkiGETNX8` (key in URL = write-only, rotate on abuse). Check `list_errors` after CI runs/changes; resolve fixed errors. Recommended variant: Script/curl (Android app: native crash reporter).
- ALWAYS review CI runs via VibeWorks after every push: `get_repo_status` + `list_problems` (red CI), `list_errors` in the same pass.
