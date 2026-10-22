# HaloModelLab takeover audit

Date: 2026-10-22 (Europe/Riga)

## Resolved inputs and authority

- Lab checkout: `C:\Users\slavd\HaloTestApk\HaloModelLab`.
- Handoff source actually present: `C:\Users\slavd\HaloTestApk\HaloModelLab_CODEX_COMPLETE.md`, a single-file edition containing the continuation plan, protocol/code reference, acceptance matrix, source notes, and start prompt. Neither `HaloModelLab\docs\hml-codex\` nor an attached `HaloModelLab_Codex_Continuation\` folder is present.
- The adjacent `HaloModelLab_Agent_Pack` is the original L0-L4 input. It is not proof those batches were implemented. Its `SPEC.md` is preserved in `baseline/STARTER_SPEC.md` from the supplied source file without edits.
- The supplied spec and preserved baseline SHA-256 both equal `47eea7be2d5b711830dec38a803421cc97754774b10d7ba0b9092d283e9607b6`.
- No `AGENTS.md` exists in the Lab checkout. The adjacent agent pack's instructions apply as inherited constraints except where the v1 continuation explicitly replaces the simulation-only phase and stop-after-one-batch rules. A transition note was added to the Lab README; privacy, signing, repository authority, and verification restrictions remain.
- Checkout at takeover: branch `main`, base `9b09e78` (`HML-C0: first verified build and green unit tests`), clean worktree, no configured Git remote. No reset, branch switch, fetch, or push was performed.
- The separately available Halo checkout is `C:\Users\slavd\HaloEco\Halo-main`, at `2d7b650faa3ade3c00f504f13bf9f91495d6b73d`; it has numerous pre-existing modified and untracked files. It was read only. The user did not provide it as an editable target for this continuation. No Halo files were changed.
- Local commits on the existing Lab `main` are authorised. Push and remote creation are not authorised.

## Inherited source map and demo trace

1. `MainActivity` and `LabNavigation` route to catalog, session, history, settings, and detail screens. The catalog's DEMO selection goes through `CatalogScreen` → `CatalogViewModel` → `CatalogRepository`; only IDs in `DemoCatalog.scenarioIds` can be selected. The real catalog remains visibly unavailable as `BRIDGE_NOT_IMPLEMENTED`.
2. `SessionScreen` calls `SessionViewModel.createSession()`. The ViewModel builds an immutable `SessionPlan`; `RoomSessionRepository.createSession()` checks the single open-session slot and writes session, ordered queue rows, and `SESSION_CREATED` in one Room transaction.
3. Start requests notification permission when needed, then invokes `BenchForegroundService.start()`. The non-exported, user-started service promotes itself to foreground and creates `SessionCoordinator` with `FakeBenchmarkEngine`.
4. `SessionCoordinator.runSession()` loads and claims the plan, checks device conditions, then calls `beginAttempt()`. The repository transaction marks one queue item active, creates an attempt, and appends `BEFORE_EXECUTION` before returning the request. The coordinator records device telemetry and runs the cooperative engine under a coroutine timeout.
5. `FakeBenchmarkEngine.execute()` rejects non-synthetic origins, non-demo subjects, and unknown scenarios. The four cases are success, controlled load failure, controlled empty output, and cooperative timeout. Their delay is a fixture and is not model latency. Result origin is checked against both request and engine; mismatches are stored as HARNESS/INVALID and stop the queue.
6. `finishAttempt()` uses a conditional Room update so a terminal outcome cannot be overwritten. Events, queue state, telemetry, and session state are persisted; storage failures stop processing instead of being described as a model failure. A paused/failed/interrupted/completed session appears through `HistoryViewModel` and `SessionDetailsViewModel` from Room flows.
7. `SessionDetailsViewModel.export()` calls `ReportExporter.export()`. Export takes a consistent repository snapshot, creates bounded `report.md`, results JSON, event JSONL, telemetry CSV, per-attempt failure JSON, manifest and checksums, then closes and renames a ZIP under private `cache/exports`. Sharing uses the narrow FileProvider `cache/exports/` path; saving uses the document picker.

## Controls reviewed

- Catalog: real/demo mode, search, scenario selection, clear selection, and navigation to the session plan. Real mode has no fake entries.
- Session: repeats, case timeout, total budget, charging requirement, create/cancel draft, start/pause/stop, retry/skip, clear selection, open catalog, and dismiss message. The persistent plan is independent of the Activity after creation.
- History: session filtering and navigation to details.
- Details: export, share, and save; export is disabled for active states and failures remain visible.
- Settings: static build/bridge/evidence information; no hidden bridge connection, download, provider change, or destructive action.

## Safety and source findings

- Manifest declares notification, foreground-service, special-use foreground-service, and wake-lock permissions. It declares no `INTERNET`; service/provider components are non-exported except the launcher Activity. Backup is disabled. FileProvider exposes only `cache/exports/`.
- All main-resource XML files and the Android manifest parsed successfully; `docs/CONTINUATION_STATE.json` parsed successfully; `git diff --check` passed. These are source/metadata checks, not build or runtime verification.
- Dependency catalog/build files show Android UI, Room, coroutines, Moshi, and test dependencies; no cloud, analytics, or inference runtime dependency was found by source inspection. No Halo source include, symlink, weight cache, or model artifact is present in tracked Lab files.
- `AppContainer` owns one Room repository and starts process-owner recovery before the session ViewModel enables start. Recovery is startup-only. In the present single-process Lab there is no native worker or independent Halo supervisor; `withTimeout` only bounds the cooperative fake engine.
- Existing test sources cover four demo outcomes, coordinator pause/stop/timeout/storage/origin behavior, state transitions, Room unique-open-session and terminal-write-once behavior, recovery preservation, and ZIP contents/checksums/redaction. A concurrent session-creation instrumentation regression source was added in this audit.
- Gaps retained for later batches: Activity recreation/UI button automation, export cancellation during ZIP writing, and full storage-failure injection. These are explicitly `NOT_RUN`; relevant continuation batches must close source gaps or name them precisely.
- The V0 run exposed an inherited open-session query mismatch: session creation tested the wrong state set, while the observer used a different set. Both Room paths now use `OPEN_NAMES`; connected tests cover single-open-session admission and the app now observes the saved draft. The synthetic implementation is preserved and repaired. This does not establish real-model behavior or native-crash resilience.

## Current checkpoint

See `docs/CONTINUATION_STATE.json` for the machine-readable batch, repository, protocol, verification, and blocker state. After separate owner authorization, V0 build/unit/instrumentation checks passed and a bounded synthetic phone smoke/export passed. Real-model phone acceptance remains `NOT_RUN` at `BLOCKED_HALO_WRITE_ACCESS`.
# Continuation update — 2026-10-22

The user has now explicitly authorized editing `C:\Users\slavd\HaloEco\Halo-main` while preserving changes, and separately confirmed the empty public remote `slafftrosheen/HaloModelLab`. The Lab remote was created and its prior `main` history pushed. Current Lab base is `a3040e2fd30bdd6e2a6129e28d30670788706510`; current Halo base observed for this continuation is `fec8fdd0dc58d8660fee7687ea8d27151cc42f54` on local `main`, one commit ahead of `origin/main`.

Halo source edits now add the minimal opt-in bridge, private native worker/supervisor, model leases and existing LiteRT adapter call. Lab source edits add its AIDL gateway and `AppContainer` exposure. The shared protocol SHA-256 is `264f430a5cd7b399f82130347572c72494a6a51f70a48f0eddcf83bc31090c3e`. This is implementation source, not a verified compatible release. Real UI/session execution wiring, end-to-end event import, bounded real ZIP reproduction, and acquisition remain unfinished. Build, automated tests, and phone tests are NOT_RUN for this continuation.
