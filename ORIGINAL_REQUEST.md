# Original User Request

## 2026-09-14T14:50:21Z

# Teamwork Project Prompt — Draft

> Status: Launched
> Goal: Craft prompt → get user approval → delegate to teamwork_preview
> Requested team: Small, focused team (per user request)

This is a single self-contained implementation task based on a strict plan; keep it small and focused. Implement 5 major features into the `mobile-harness` Android codebase based on an approved detailed implementation plan.

Working directory: `d:\Master_mind_agent_git`
Integrity mode: development

## Requirements

### R1. Implement Local Message Backup System
- Create `MessageBackupManager.kt` with a blocking `suspend` function to write to `message_backups.json` in `filesDir`.
- Modify `MainViewModel.kt` to intercept `sendPrompt`, halting execution and setting an error state if the backup fails.
- Modify `TerminalScreen.kt` to display the error state above the input field using `WindowInsets.safeDrawing`.

### R2. Implement Native GitHub OAuth Integration
- Modify `AndroidManifest.xml` to use `singleTask` for `MainActivity` and add an intent-filter for `mobileharness://github-callback`.
- Override `onNewIntent` in `MainActivity.kt` and pass the intent to `MainViewModel`.
- Create `GitHubAuthManager.kt` to exchange the code for a token, and save it using `ApiKeyVault.kt`.

### R3. Implement CodeRabbit & Actions Dashboard
- Create a new `GitHubDevDashboardScreen` composable that fetches failed workflow runs and PR reviews using OkHttp.
- Add a new `WorkspaceTab.DASHBOARD` in `PocketDevApp.kt` to route to this screen.

### R4. Implement Command Auto-Suggest UI & `/compact` Trigger
- Modify `TerminalScreen.kt` to show a row of `AssistChip` suggestions (e.g., `/compact`) above the keyboard when the user types `/`.
- Add `executeSilentCommand` in `MainViewModel.kt` to write directly to the runtime bridge without adding a chat message or showing output.

### R5. Implement MCP Server & Custom Skills Configuration
- Create `McpSkillManager.kt` using IO coroutines to manage `.mcp.json` and `.claude/commands/*.md` files inside the PRoot workspace, explicitly granting read/write/execute permissions.
- Extend `SettingsScreenModern.kt` with an accordion for these settings.

## Acceptance Criteria

### Implementation Quality
- [ ] Code compiles without errors using `./gradlew assembleDebug`.
- [ ] No `// TODO` placeholders exist in the newly written code.
- [ ] All new UI components utilize `WindowInsets.safeDrawing` or `imePadding` to avoid system UI overlap.
- [ ] The app successfully runs on an Android emulator or device without crashing on startup.

## 2026-09-14T15:14:16Z

USER DIRECTIVE: To save tokens, absolutely DO NOT spawn any new subagents. Wrap up your current verification process immediately and declare victory. Terminate all work as quickly as possible.

## 2026-09-14T15:32:15Z

USER DIRECTIVE OVERRIDE: The user has explicitly demanded to save tokens and finish immediately. YOU MUST ABORT Review Round 2 and Review Round 3. Proceed IMMEDIATELY to the Victory Audit using the current passing state (all 33 unit tests pass, assembleDebug passes). Do not spawn any more reviewers. Declare victory now.


