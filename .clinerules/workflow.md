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

### If the MCP API key is invalid (HTTP 401 "Invalid or revoked API key")
- Verify the outage with a direct curl against `https://vibeworks.morncloud.de/api/mcp` (initialize) before assuming anything else.
- Tell the user immediately: a new API key must be generated in the VibeWorks UI; MCP bearer keys can be revoked at any time.
- Continue all work that does not need MCP (git, builds, GitHub API, releases); record every pending VibeWorks update as an explicit backfill list and execute it as soon as the key works again.
- Bridge-401 vs. key-401: Cline's MCP bridge can answer 401 transiently while the key is still valid. Verify with a direct curl `initialize` (expect HTTP 200) BEFORE rotating the key (verified 2026-09-16).
- Tool inventory: 34 tools as of 2026-09-16 (verified via MCP `tools/list`; vendor agent rules saved at `.clinerules/vibeworks.md`). Re-check `tools/list` after VibeWorks updates – new tools appear there first.

### Project protection (star protection)
- Project "Harness" is star-protected: status and repository fields can ONLY be changed in the VibeWorks UI itself (`update_project` for status is rejected by design). Report this to the user instead of retrying.

### Session end
- Update task statuses and project progress.
- Add a short English work-log note with the outcome.
- Session hygiene audit: kill every background helper process started this session (CI watchers, downloads, builds), delete their temp scripts/logs, verify `git status --short` is clean.

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

## 6. Background helpers (watchers, downloads, long builds)
- Anything longer than ~25 s must run as a background `Start-Process powershell -File build\<name>.ps1` writing to a `build\<name>.log`, then poll the log in short sleeps (single commands time out at 30 s).
- Never inline long loops in the main shell. Every helper must have a bounded loop or an exit condition (e.g. `if ($run.status -eq 'completed') { break }`).
- Before deleting a helper script, kill its process (check `Get-CimInstance Win32_Process` by command line pattern) - a deleted script can leave an orphaned process behind.

## 7. Windows/PowerShell operations gotchas (hard-learned, ALWAYS apply)
- `git push` stderr looks like a failure but succeeds - verify with `git status -sb` and `git log origin/main --oneline -1`, never trust the stderr block.
- Never inline JSON in curl args; always temp file + `--data-binary @file`.
- PS 5.1 `Set-Content -Encoding UTF8` writes a BOM -> GitHub API answers 400 "Problems parsing JSON". Use `[System.IO.File]::WriteAllText($path, $json, (New-Object System.Text.UTF8Encoding($false)))`.
- PowerShell has no bash `<<<`; feed stdin via a file and `cmd /c "git credential fill < file"`.
- GitHub release asset uploads go to `https://uploads.github.com/...`, NOT `https://api.github.com/...`.
- Release download URLs redirect (302): use `curl -L`; to verify availability use a range request `-r 0-0` (expect 206), `-I` HEAD alone is not conclusive.
- Expect the 30 s single-command timeout: design every command to either finish fast or delegate to a background helper (see rule 6).
- `curl -H "Bearer ..."` WITHOUT the field-name prefix silently DROPS the header -> hidden 401 "missing". Always pass the full header: `-H "Authorization: Bearer ..."`.
- Never use `$$` or doubled `$` in inline commands (the command runner mangles them into parse errors). Multi-statement PowerShell goes into a temp `.ps1` script instead.
- `git pull` stderr trips PS 5.1 NativeCommandError - verify sync via `git log --oneline -1` / `git status -sb`, never via the exit code.

## 8. Release procedure & docs
- The full release recipe lives in the VibeWorks docs subpage "Release procedure v1.0.x" (tag at the version-bump commit, retag build-<n>, versioned asset names, upload manifest via uploads.github.com, e2e with -L/-r).
- Error inbox + monitoring details: docs subpage "Error Inbox & Monitoring".
- Project handbook: docs page "Project Handbook - Mobile Harness" - keep it current when project facts change.

## 9. Repository & issue separation (since 2026-09-15)
- GitHub issues live CENTRALLY in `JONIMONI09/Issue-Hub` (local: `D:\Issue-Hub`), never on forks. Template: `.github/ISSUE_TEMPLATE/upstream-finding.md`.
- `JONIMONI09/vibeworks` = code mirror/fork of MoinMornhart/vibeworks (issues disabled by design) for potential upstream PRs; its topics are tracked as Issue-Hub issues.
- Cross-project incident analyses belong in `Issue-Hub/docs/`, NOT in product repos (moved out of Mobile-Harness at 77d3175). Product repos only get product issues.
- Upstream finding workflow: duplicate check on upstream -> upstream issue (if appropriate) -> Issue-Hub tracking issue with checklist -> analysis doc under Issue-Hub/docs/ -> link both in Issue-Hub README watchlist.
- VibeWorks MCP keys: rotate in UI on 401; key lives only in cline_mcp_settings.json (never in rules/repos/notes); verify swap with MCP initialize curl (HTTP 200).
