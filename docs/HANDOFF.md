## Latest verification checkpoint — 2026-10-23 (authorized V0)

- Lab compile blockers found during V0 were repaired in source commit `c1c4fda`; Room schema v3 was generated and committed. Lab `:app:assembleDebug`, `:app:testDebugUnitTest` (131 tests, 24 suites, 0 failures), and `:app:assembleDebugAndroidTest` all PASS. Warnings remain, but no compile or test failures.
- Current Halo `main` is `023ef76013b599ee7ca3513014af184a5242e2b5`; the full debug APK build PASSed after syncing this upstream commit. Halo APK SHA-256: `096c3dc0c5a6b139cd2f4a4536dd39a47228809ada0dd114bddfdfaedb1afe54`. The Halo worktree is clean and the original dirty checkout remains untouched.
- Protocol v1.0 canonical schema files remain byte-identical at SHA-256 `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`. Lab debug APK SHA-256: `af163d42eb7edc69aeb023ff61e784c04039c67940b43f9826083afaea6ff787`.
- `adb devices -l` returned an empty list. Install, connected instrumentation, synthetic V1, and real-model V1 are therefore **NOT_RUN**. Do not claim a usable phone installation, native-crash resilience, or real-model acceptance from the build alone.
- C8 remains explicitly unsupported without Halo acquisition support; Lab still has no downloader, INTERNET permission, or weight cache. Opaque evidence artifact bytes remain unavailable without a bounded Halo AIDL reader. No real model files are copied to Lab.
## Latest bounded checkpoint — 2026-10-23

- Lab `main` is pushed through source commit `738ff79` and docs checkpoint `3993ca2`; branch clean and synced. Halo managed-worktree `main` remains pushed through merge `5a4047fe`, clean and synced. The original dirty Halo checkout is preserved.
- Protocol v1.0 files remain byte-identical at SHA-256 `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`. Runtime compatibility remains unverified. Build, automated tests, and phone tests remain **NOT_RUN**.
- C10 report includes coverage and exact-scope exclusion summaries while preserving unknown status values. Opaque artifact bytes remain unavailable because Halo has no output-artifact registry or bounded AIDL read operation; model file descriptors are never evidence exports.

## C7/C9 continuation checkpoint — 2026-10-23

- Lab commit `4aa497d` completes the requested C7 source gates: saved LOAD/EXECUTE/UNLOAD budgets trip at the limit in the Lab event watchdog and Halo's isolated worker watchdog; the supervisor retains leases until cleanup or worker death is confirmed. Thermal checkpoints persist synchronously on state changes and require 60 continuous safe seconds after severe status. The service explicitly wires a 256 MiB free-space reserve and a 64 MiB quota over Lab database/WAL/SHM and report exports; Halo model storage is excluded. This is source evidence only; native crash resilience is not verified.
- Follow-up Lab commit `1384fb1` makes thermal storage fail closed: unreadable or unwritable checkpoints keep the gate blocked/cooling instead of terminating the monitor. The source test covers write failure; it is NOT_RUN.
- The existing persisted session total is also enforced at Room attempt admission through boot-aware active-time accounting. This continuation does not replace that accounting or add an unattended run path.
- C9 maps replayed terminal output and identity conservatively, treats complete fixed-fixture mismatches as model failures and truncated output as unknown, and recomputes only bounded observations for the same verified scope and policy across sessions. History's `Show excluded` list scrolls, deduplicates by scope/policy, opens retained evidence, and has a deliberate catalog retest action. Catalog auto-exclusion remains gated while the current source/effective scope is unknown; unknown identity never hides a model.
- Added test sources for thermal persistence, storage admission, cross-session exact-scope qualification and fixture output mapping. **Build: NOT_RUN; Automated tests: NOT_RUN; Phone tests: NOT_RUN.** `git diff --check`, strings XML and continuation JSON parsing, and protocol v1.0 byte/hash comparison passed. No Gradle, tests, ADB, install, emulator, or benchmark command was run.
- Lab `main` source commit `1384fb1` is pushed to authorized `origin/main`; the current Lab branch is clean. Halo commits `5b1ed695` and `8bee5863` are included in pushed merge commit `5a4047fe` on Halo `origin/main`; the worktree is clean and synced. The merge preserved 34 remote commits and all 8 local commits without rebase or force-push. The original dirty Halo checkout remains untouched. Protocol v1.0 files remain byte-identical at `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`.
## C7/C9 continuation checkpoint — 2026-10-22

- Lab source now passes saved stage budgets into `BenchmarkRequest`; the coordinator watches committed stage events and records over-budget timeouts as environment evidence with unknown model validity. Halo's current replay emits an execute-stage marker around an adapter call that combines load and generation, and normal completion does not yet provide independently timed load and unload markers. Exact per-stage coverage still requires those Halo worker markers; Lab cancellation itself is not native hard cancellation.
- Admission now enforces a 256 MiB free-space reserve and pauses when Lab-owned database/WAL/SHM plus report-cache evidence reaches 64 MiB. It measures only Lab files; it never examines Halo model storage. Unknown file-size measurements remain null with a reason.
- The coordinator blocks unknown/moderate thermal readings and requires 60 seconds of continuous NONE/LIGHT readings after severe thermal status. The cooldown checkpoint is stored in Lab-private preferences with elapsed-realtime reset handling after reboot.
- C9 adds a replayed-terminal observation mapper that returns unknown scope/assertion unless the plan and terminal identity are verified, plus `Show excluded` in History for persisted exact-scope projections. It does not yet automatically gather observations across sessions, recompute projections, or filter the current catalog. No current model is excluded based on unknown identity.
- Added source tests for thermal hysteresis and unknown-scope mapping. **Build: NOT_RUN; Automated tests: NOT_RUN; Phone tests: NOT_RUN.** Static `git diff --check`, continuation JSON parsing, and exact protocol v1.0 byte/hash comparison passed. No Gradle, tests, ADB, install, emulator, or benchmark command was run.
- Lab branch `main`; current source is being committed locally and push remains authorized. Halo worktree remains at `53ef62d5626832f2b9b59d00a1807eed6d36def9`, 6 ahead / 33 behind its recorded `origin/main`, not pushed or rebased. Protocol v1.0 files remain byte-identical at `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`.
# Handoff to Codex — Halo Model Lab v1

## Latest bounded checkpoint — 2026-10-23

Lab `main` is pushed through `1384fb1` (latest source) with a clean branch. Halo managed-worktree `main` is pushed through merge commit `5a4047fe`, which contains the local C4/C7 source commits and the fetched remote history. The merge did not rebase or overwrite either side; the original dirty Halo checkout remains untouched. Protocol files remain v1.0 and byte-identical at SHA-256 `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`. Runtime compatibility is unverified. Build / Automated tests / Phone tests remain **NOT_RUN**. Continue from `docs/CONTINUATION_STATE.json`; do not run verification while coding.

## Current C4/C8 checkpoint — 2026-10-22

Lab source commit `1f328c9e16463751df1946a2b01e69a95d19f1eb` and docs checkpoint `e9eabe2a06387ce39078546a6c9621855231dad8` are pushed to authorized `origin/main`; Halo authorized managed worktree source commit `75da9ca49bfeea2da6fd914dde5380e8f6745e9d` (not pushed). The original dirty Halo checkout remains preserved. The shared protocol bytes match at SHA-256 `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`.

Lab V0 passed: debug assemble, 95 unit tests, Android test APK compile, and 21 connected instrumentation tests on Xiaomi `23124RA7EO` (Android 15 / API 35). Halo app compile still fails on unrelated app sources; the changed C4 service/worker sources had no errors in the latest compile. C8 is explicitly unsupported (`HALO_ACQUISITION_NOT_IMPLEMENTED`) with no Lab download path, internet permission or weight cache.

Manual Lab V1 synthetic install was blocked by Android `INSTALL_FAILED_USER_RESTRICTED`, so there was no launch or ZIP inspection. Real-model V1, usable paired APK, native crash resilience and model qualification are not claimed. See `docs/CONTINUATION_STATE.json` and `docs/PENDING_VERIFICATION.md` for exact evidence.
## Current continuation status (2026-10-22)

### C10 source checkpoint (2026-10-23)

Pushed C10 baseline `7d8a6e7b304a87489d9249adda794eb44ee7ae8d` adds exact-scope/policy metric comparison, schema-v2 `comparison.json`, bounded `reproduction/<attemptId>.json` descriptors, and targeted failure files. Follow-up source commit `a05bb3b5050b3aa791880ab2209a0275508f4efc` exports only complete, hash-verified output previews from the fixed nonpersonal FAST smoke fixture under `artifacts/`. Commit `738ff79` adds coverage and qualification summaries to the first-read report while reserving space inside its 8 KiB limit; only persisted `REJECTED_FOR_PROFILE` rows are described as exact-scope exclusions, and unknown statuses remain unknown. Opaque artifact transfer has no matching Halo AIDL method and stays unavailable. Model weights are never exported. C10 tests are source-only. Build, automated tests, and phone tests remain NOT_RUN. See the top of `docs/CONTINUATION_STATE.json` for commits/protocol versions and the C10 boundary.

This file is the historical v0 handoff. The active v1 continuation is resolved from the workspace single-file package `HaloModelLab_CODEX_COMPLETE.md`; current C0 findings and the authorized Halo source map are in `docs/TAKEOVER_AUDIT.md` and `docs/HALO_INTEGRATION_MAP.md`. Historical V0 results on 2026-10-22: build PASS, 91/91 unit tests PASS, and 21/21 connected instrumentation tests PASS. A bounded synthetic `demo/success` phone smoke and schema-v2 ZIP checksum check also passed. Real-model acceptance remains NOT_RUN; no current real-model test is claimed. The prior 2026-10-21 results below remain historical reports only.

At the initial takeover, the Lab had no remote configured and Halo was read-only. Those conditions were later superseded: the user authorized the public `slafftrosheen/HaloModelLab` remote and editing `C:\Users\slavd\HaloEco\Halo-main` while preserving its changes. Current source and commit state is in `docs/CONTINUATION_STATE.json`.

At the time of this historical handoff, C4/C6/C8 were blocked because no authorized Halo target had been supplied. The owner later authorized Halo editing and the public Lab remote; active source/commit status is maintained at the beginning of this file and in `docs/CONTINUATION_STATE.json`. The v1 stage transition replaces only the starter scope limit and one-batch stop rule; signing/privacy, repository authority and verification restrictions remain active.

Состояние: HML-L0…HML-L4 + C0 завершены. Приложение **собрано** (`assembleDebug`),
unit-тесты **проходят** (55/55), instrumentation-исходники **компилируются**;
на устройстве **не проверялось**. Настоящего inference, моста Halo, весов и runtime
здесь нет.

## 1. Что уже работает по исходникам

```text
CatalogScreen ──> CatalogViewModel ──> CatalogRepository ──> DemoCatalog
SessionScreen ──> SessionViewModel ──> SessionRepository ──> Room (сессия, план, очередь)
      │                                     ▲
      └─ BenchForegroundService.start ──> SessionCoordinator ──> FakeBenchmarkEngine
                                             │
                                             └─ DeviceConditions (зарядка, thermal, место, wake lock)

HistoryScreen ──> HistoryViewModel ──> Room
SessionDetailsScreen ──> SessionDetailsViewModel ──> ReportExporter ──> cache/exports/*.zip
                                                          │
                                                          └─ ExportShare ──> FileProvider / create-document
```

Ключевые инварианты, которые нельзя «упростить» при интеграции:

- план неизменяем: правки параметров создают новую сессию;
- `beginAttempt` коммитит `BEFORE_EXECUTION` до запуска движка;
- pause завершает текущую ограниченную попытку, stop её отменяет;
- storage failure останавливает очередь и даёт `STORAGE_FAILURE`, а не FAILED модели;
- origin фиксируется в плане и попытке; несовпадение origin → HARNESS/INVALID и остановка;
- отсутствующая телеметрия хранится как null + причина, не как 0;
- recovery после потери процесса даёт INTERRUPTED/NEEDS_DECISION, не переписывает
  завершённые попытки;
- экспорт читает снимок короткой транзакцией, не копирует WAL-базу, не переименовывает
  SYNTHETIC и не выдумывает model hash/runtime version.

## 2. Файлы, которые затрагивает Codex

| Файл | Зачем |
|---|---|
| `domain/BenchContracts.kt` | `BenchmarkEngine`, `BenchmarkRequest`, `AttemptResult`, `AttemptEventSink`, `FailureInfo`, enum-ы origin/validity/domain |
| `domain/SessionStateMachine.kt` | переходы сессии и очереди; расширять только вместе с тестами |
| `data/SessionRepository.kt` | контракт хранилища: claim/begin/finish/pause/stop/recovery/snapshot |
| `data/RoomSessionRepository.kt` | транзакционные инварианты и recovery |
| `data/LabDatabase.kt`, `data/*Entity.kt`, `data/LabDao.kt` | схема; добавочные поля — новая версия Room + миграция, без destructive fallback |
| `runner/SessionCoordinator.kt` | оркестрация; здесь появится настоящий worker/watchdog |
| `runner/DeviceConditions.kt` | условия запуска и wake lock |
| `runner/BenchForegroundService.kt` | foreground host; не запускать работу при старте процесса |
| `export/ExportDtos.kt`, `export/ExportJson.kt` | schema v2 добавляет bounded Lab-side real-plan/replay summaries; inference facts remain gated on Halo |
| `export/ReportExporter.kt` | снимок и ZIP; лимиты 8 KiB/32 MiB сохранить |
| `AppContainer.kt` | единственная точка подстановки настоящего gateway/engine |

`FakeBenchmarkEngine` — фикстура для demo-сценариев; она не должна попасть в real catalog.

## 3. Чего здесь нет и что нельзя имитировать

- `HaloLabGateway`, IPC/handshake, artifact leases, downloader, каталог Halo;
- модель, веса, backend CPU/GPU/NPU, реальные latency/память/токены;
- model hash, adapter revision, runtime version и source revision (сейчас
  `sourceRevision = "not_recorded"`, `inferenceRuntime = null`, `bridgeStatus = NOT_IMPLEMENTED`);
- hard timeout для native/JNI и отдельный процесс: `withTimeout` работает только с
  кооперативным fake engine;
- baseline-сравнение прогонов и удаление истории.

Если поле неизвестно, оставить null/`NOT_IMPLEMENTED` и добавить omission/причину.
Заполнять его «правдоподобным» значением запрещено: отчёт должен оставаться доказательством.

## 4. Предлагаемый следующий шаг (не выполнен)

1. **C0 — ВЫПОЛНЕН (21.10.2026).** `assembleDebug`, `testDebugUnitTest` (55/55),
   компиляция `assembleDebugAndroidTest` — зелёные; исправлены ошибки компиляции и
   три ошибки тестовых фикстур/ожиданий (см. `docs/IMPLEMENTATION_STATUS.md`, раздел C0).
   `connectedDebugAndroidTest` НЕ запускался — нет устройства/emulator.
2. **C1.** Заморозить `HaloLabGateway` и `BridgeStatus`; добавить типы для artifact manifest
   hash, runtime version, adapter revision. Не подключать Halo source tree.
3. **C2.** Один каталог и один downloader; повторный запуск не скачивает модель заново;
   lease освобождается при любом терминальном состоянии.
4. **C3.** Настоящий `BenchmarkEngine` + watchdog в отдельном процессе; origin строго
   `DEVICE_MEASUREMENT`; при сомнении — `validity=UNKNOWN`, без вердикта о модели.
5. **C4.** Полевой прогон и только затем — квалификационные выводы, с полным scope
   (device profile, OS/build, app/adapter revision, runtime, artifact hash, backend,
   параметры, suite/dataset hash).

## 5. Открытые проверки

Всё устройственное из `docs/PENDING_VERIFICATION.md` остаётся `NOT_RUN`
(`connectedDebugAndroidTest`, foreground service, уведомления, датчики, share-sheet,
ручная приёмка). Перед изменениями схемы прочитать разделы «Room + KSP»,
«Foreground service», «Экспорт» и «FileProvider».
