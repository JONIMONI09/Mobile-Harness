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
2. Load project state from VibeWorks MCP: `get_project` / `list_tasks` for project "Harness" (id `cmu1h6t3p02rzspn3wx1p165j`).
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
- VibeWorks project: "Harness" (id `cmu1h6t3p02rzspn3wx1p165j`), formerly named "Mein Projekt".
- Acceptance baseline: 33 unit tests per flavor + assembleDebug must stay green.

## 5. Error Inbox (VibeWorks, since 2026-09-15)
- Ingest URL (write-only, key embedded in the URL): `https://vibeworks.morncloud.de/api/errors/in/_xHuKoZj2qQTFQdRCjEMPB_XkiGETNX8`
- Anyone holding the URL can only WRITE errors into the project inbox; reading requires a VibeWorks login. On abuse, generate a new key in the VibeWorks UI.
- Ingest variants: Script/curl = universal default for this user; Browser = only for own websites (insert before all other scripts); Node.js = only for Node projects; Android apps = native crash reporter that POSTs the same payload shape (message, type, stack, url) to this URL.
- Cline MUST call `list_errors` on the project after every CI run, app change or release, and resolve fixed errors with `resolve_error`.
- ALWAYS review CI runs via VibeWorks after every push: `get_repo_status` (repository.ci.state + runs) and `list_problems` (redCi); check `list_errors` in the same pass.
- Use the task list (create_task/add_to_today/get_today) to plan and track work; keep it current.
