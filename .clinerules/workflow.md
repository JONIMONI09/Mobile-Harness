# Standing Workflow Rules (Cline x VibeWorks)

These rules were set by the user on 2026-09-14 and apply to EVERY session in this workspace.

## 1. Language rule (mandatory)
- ALWAYS create/edit all artifacts in ENGLISH: code, comments, commit messages, docs, tasks, notes, CLAUDE.md, reports, PR texts.
- VibeWorks records (project fields, tasks, notes, docs) are ALWAYS in ENGLISH and ALWAYS kept up to date after every change. German or other foreign-language content found on VibeWorks is translated to English on sight.
- Chat replies may mirror the user's language, but every created file/record is English.

## 2. VibeWorks MCP is part of the workflow (mandatory)
- The VibeWorks MCP server (`https://vibeworks.morncloud.de/api/mcp`, configured in `C:\Users\ggjon\.cline\data\settings\cline_mcp_settings.json`) is used in every session.

### Session start (ALWAYS first)
1. Check for changes: `git status --short` and `git log --oneline -5`.
2. Load project state from VibeWorks MCP: `get_project` / `list_tasks` for project "Mein Projekt".
3. Compare working tree vs. tasks and report deviations before doing anything else.

### During work
- Set the current task to DOING (update_task) when you start and DONE when finished.
- Record decisions, findings and results as English project notes (create_note).
- Keep task titles short and clear.

### Session end
- Update task statuses and project progress.
- Add a short English work-log note with the outcome.

## 3. Verify before claiming done
- After code changes run: `gradlew :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest :app:assembleOnlineDebug`.
- Never mark work DONE without a green build/test run (or an explicit blocker note).

## 4. Project facts
- Repository: JONIMONI09/Mobile-Harness (fork of techjarves/Mobile-Harness), branch main.
- VibeWorks project: "Mein Projekt" (id `cmu1h6t3p02rzspn3wx1p165j`).
- Acceptance baseline: 33 unit tests per flavor + assembleDebug must stay green.
