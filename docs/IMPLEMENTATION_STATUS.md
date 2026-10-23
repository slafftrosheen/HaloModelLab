## Current checkpoint — 2026-10-23 — bounded evidence AIDL

Lab source commit: 779932a (C10 exporter/client). Halo source commits: a9d41429 and d1c87d58 (authenticated endpoint). Canonical protocol is v1.1 at SHA-256 b23f23b9812bb425862adaa98e5b4ad7549678967291ee6959909bbf6bf97859; both protocol files match, and both AIDL interface copies declare the same ParcelFileDescriptor method.

The API transfers only a complete terminal fixed-smoke text output, under a 2,048-byte cap, with Halo consent and caller signature/package checks. Model artifact IDs and bytes are rejected. Lab calls the API while exporting, bounds the read, and checks exact bytes and SHA-256 against the replay event. Other opaque artifact rows remain unavailable because Halo has no producer for them.

Host verification: Lab build PASS, 132/132 unit tests PASS, and Android test APK compile PASS. Halo build PASS and focused HaloLabEvidenceArtifactTest 4/4 PASS; full Halo suite has 8 existing failures. Phone: 24/24 existing Lab instrumentation tests PASS after fake-clock boot-ID repair. Lab was installed/launched and rendered the real catalog; its inventory refresh exposed `BRIDGE_NOT_SUPPORTED`. Source fix removes an invalid default-category filter from package-scoped AIDL discovery, and a focused Binder handshake test compiles. That test did not run because Android blocked test-APK installation; the current Lab APK is absent after the test runner cleanup and another install returned `INSTALL_FAILED_USER_RESTRICTED`. Settings reports a 24-hour wait for unverified developers. Paired Binder verification remains pending.

Build: PASS. Automated tests: Lab unit 132/132 PASS; connected instrumentation 24/24 PASS before the latest AIDL change; focused Halo 4/4 PASS; full Halo suite FAIL (8 existing failures). Updated AIDL smoke and model inference: NOT_RUN until Android permits installing both Lab and its instrumentation APK.
## Latest verification checkpoint — 2026-10-23 (authorized V0)

- Lab compile blockers found during V0 were repaired in source commit `c1c4fda`; Room schema v3 was generated and committed. Lab `:app:assembleDebug`, `:app:testDebugUnitTest` (131 tests, 24 suites, 0 failures), and `:app:assembleDebugAndroidTest` all PASS. Warnings remain, but no compile or test failures.
- Current Halo `main` is `023ef76013b599ee7ca3513014af184a5242e2b5`; the full debug APK build PASSed after syncing this upstream commit. Halo APK SHA-256: `096c3dc0c5a6b139cd2f4a4536dd39a47228809ada0dd114bddfdfaedb1afe54`. The Halo worktree is clean and the original dirty checkout remains untouched.
- Protocol v1.0 canonical schema files remain byte-identical at SHA-256 `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`. Lab debug APK SHA-256: `af163d42eb7edc69aeb023ff61e784c04039c67940b43f9826083afaea6ff787`.
- `adb devices -l` returned an empty list. Install, connected instrumentation, synthetic V1, and real-model V1 are therefore **NOT_RUN**. Do not claim a usable phone installation, native-crash resilience, or real-model acceptance from the build alone.
- C8 remains explicitly unsupported without Halo acquisition support; Lab still has no downloader, INTERNET permission, or weight cache. Opaque evidence artifact bytes remain unavailable without a bounded Halo AIDL reader. No real model files are copied to Lab.
## C7/C9 continuation checkpoint — 2026-10-22

- Lab C7 enforcement now watches committed LOAD/EXECUTE/UNLOAD events. Halo records those milestones at the LiteRT load/generation/cleanup boundaries, persists the three accepted stage budgets before execution, and runs an independent watchdog in the isolated modelbench process. A stage overrun marks an environment timeout and kills the worker; the Halo supervisor waits for worker death before releasing leases. This is source-only; native crash resilience is not verified.
- Admission enforces a 256 MiB free-space reserve and pauses when Lab-owned database/WAL/SHM plus report-cache files reach 64 MiB. Halo model storage is excluded. Unknown storage readings pause with explicit unknown reasons. Thermal UNKNOWN/MODERATE readings block; after severe status, the Lab-private persisted cooldown requires 60 continuous seconds of NONE/LIGHT and restarts conservatively if elapsed realtime reset after reboot.
- C9 maps replayed terminal events only when the exact plan scope, fixture, artifact, backend and runtime are verified. It stores bounded observations per exact scope and recomputes qualification across prior device sessions. History has a `Show excluded` view of persisted exact-scope projections. Catalog filtering remains unavailable while the current plan's source/effective identity is unknown; no unknown identity can hide a model.
- Added source tests for stage-budget validation, thermal hysteresis, storage admission, cross-session qualification aggregation and unknown-scope mapping. **Build: NOT_RUN; Automated tests: NOT_RUN; Phone tests: NOT_RUN.** Static `git diff --check`, continuation JSON parsing, and exact protocol v1.0 byte/hash comparison passed. No Gradle, tests, ADB, install, emulator, or benchmark command was run.
- Lab `main` is pushed through `fd9f239791e66a95d6a1aeb5a325c355358b7e73`; C7/C9 source commit `b20f9e1`. Halo authorized worktree `C:\Users\slavd\HaloEco\Halo-main-hml` is local at `5b1ed695`, 7 ahead / 33 behind its recorded origin ref and not pushed. Original Halo checkout remains untouched. Protocol v1.0 files remain byte-identical at `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`.
## C7/C9 continuation checkpoint — 2026-10-22

- Lab source now passes saved stage budgets into `BenchmarkRequest`; the coordinator watches committed stage events and records over-budget timeouts as environment evidence with unknown model validity. Halo's current replay emits an execute-stage marker around an adapter call that combines load and generation, and normal completion does not yet provide independently timed load and unload markers. Exact per-stage coverage still requires those Halo worker markers; Lab cancellation itself is not native hard cancellation.
- Admission now enforces a 256 MiB free-space reserve and pauses when Lab-owned database/WAL/SHM plus report-cache evidence reaches 64 MiB. It measures only Lab files; it never examines Halo model storage. Unknown file-size measurements remain null with a reason.
- The coordinator blocks unknown/moderate thermal readings and requires 60 seconds of continuous NONE/LIGHT readings after severe thermal status. The cooldown checkpoint is stored in Lab-private preferences with elapsed-realtime reset handling after reboot.
- C9 adds a replayed-terminal observation mapper that returns unknown scope/assertion unless the plan and terminal identity are verified, plus `Show excluded` in History for persisted exact-scope projections. It does not yet automatically gather observations across sessions, recompute projections, or filter the current catalog. No current model is excluded based on unknown identity.
- Added source tests for thermal hysteresis and unknown-scope mapping. **Build: NOT_RUN; Automated tests: NOT_RUN; Phone tests: NOT_RUN.** Static `git diff --check`, continuation JSON parsing, and exact protocol v1.0 byte/hash comparison passed. No Gradle, tests, ADB, install, emulator, or benchmark command was run.
- Lab branch `main`; current source is being committed locally and push remains authorized. Halo worktree remains at `53ef62d5626832f2b9b59d00a1807eed6d36def9`, 6 ahead / 33 behind its recorded `origin/main`, not pushed or rebased. Protocol v1.0 files remain byte-identical at `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`.
# Implementation status — Halo Model Lab

## Latest continuation checkpoint — 2026-10-23 (C10)

- C10 comparison now emits metric deltas only for exact verified device scope and policy matches. It keys metrics by name/unit/scope, uses the median of finite samples attached to `VALID` device attempts, and preserves missing/unavailable measurements as null with a reason. `comparison.json` is schema v2; no delta is described as a regression.
- The bounded ZIP now includes `reproduction/<attemptId>.json` descriptors for at most 200 attempts, with stable case/configuration digests and attempt outcome/validity, and no raw model paths or weights. Targeted failure JSON remains per attempt. `artifacts/index.json` lists bounded metadata and includes only a complete, hash-verified output preview from the fixed nonpersonal FAST smoke fixture; arbitrary/opaque artifact bytes remain unavailable because Lab's `openEvidenceArtifact` has no Halo AIDL counterpart. Model weights are never exported.
- The first-read `report.md` now has reserved room inside its 8 KiB cap for real-run coverage counts and qualification status. It lists only persisted `REJECTED_FOR_PROFILE` records as exact-scope exclusions; unrecognized status values remain `UNKNOWN_STATUS`, never exclusions. A source test covers this mapping. Opaque artifact transfer still lacks a versioned Halo AIDL method.
- Lab C10 source commit `738ff79` adds this report follow-up after the pushed commits `7d8a6e7` and `a05bb3b`. **Build: NOT_RUN; Automated tests: NOT_RUN; Phone tests: NOT_RUN.** Halo merge `5a4047fe` is unchanged and pushed. Lab and Halo protocol v1.0 schemas remain byte-identical at SHA-256 `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`. Opaque artifact transfer and runtime acceptance remain open.

## Latest source checkpoint — 2026-10-23

- Current Lab source commit `1384fb1` is pushed to authorized `origin/main` and adds the requested C7 completion: independent stage limits trip at their saved boundaries, the Halo worker watchdog terminates a native-stuck process, and supervisor leases remain fenced until cleanup/death confirmation. Lab service explicitly wires the 256 MiB free-storage reserve and 64 MiB Lab-owned evidence quota. Severe thermal recovery persists synchronous monotonic checkpoints and requires a continuous safe minute. Halo counterpart commits are pushed in merge `5a4047fe`; no native crash-resilience claim is made without permitted runtime evidence.
- Follow-up Lab source commit `1384fb1` fails closed if thermal checkpoint reads or writes fail, so the condition monitor does not lose control through a persistence exception.
- RealRunPlan's total session budget remains enforced by Room's persisted, boot-aware active-time accounting before each attempt allocation.
- C9 maps verified terminal outputs and runtime identity into bounded per-scope observations, converts a complete fixture mismatch into a model-attributed failure, and keeps truncated or unverified evidence unknown. Cross-session aggregation requires distinct worker instances. History exclusions are now scrollable, deduplicated, evidence-reviewable, and link to an intentional catalog retest. Catalog auto-hiding remains unavailable while current source/effective identity is unknown.
- Current source test additions are NOT_RUN. **Build: NOT_RUN; Automated tests: NOT_RUN; Phone tests: NOT_RUN.** Static diff check, JSON/XML parsing, and protocol hash checks are allowed and recorded in `docs/CONTINUATION_STATE.json`.

- Lab commits through `1384fb1` include C7 accounting and the requested per-stage/thermal/storage enforcement; Lab `main` is pushed and clean. Halo commits `7a8994e`, `53ef62d`, `5b1ed695` and `8bee5863` are pushed in merge `5a4047fe`; the merge retained both histories. The existing Room session active-time budget remains enforced before each attempt allocation. Runtime compatibility and native crash resilience remain unverified.
- C9 source now treats a lone model-attributed failure as `PARTIAL`, excludes timed-out checks from qualification/rejection, requires confirmed failed outcomes for structural exclusion, adds exact-scope matching/retest planning, and has a bounded Room projection store that rejects non-device sessions and malformed digests. A safe terminal observation mapper and History “Show excluded” view now exist; cross-session exact-scope projection recomputation is source-wired; catalogue filtering remains open until a verified current scope exists, and unknown identity never auto-hides.
- C10 report output no longer labels all sessions `NOT_IMPLEMENTED` or says real inference is absent. The ZIP adds bounded failure clusters retaining attempt IDs and groups by code/domain/stage/subject/role/requested backend. Manifest runtime identity is populated only when every imported terminal payload hash verifies and all terminal versions agree. The details screen now allows selecting a completed device session; comparison output is generated only for exact verified scope and policy matches, and `report.md` summarizes new/resolved/persistent failure counts without calling them regressions. Runtime source identity is currently unverified in the fixed smoke plan, so those runs will correctly export as incomparable. Metric deltas, artifact capture into the ZIP, and post-coding export verification remain open.
- Room schema source is now version 3. Its schema asset has not yet been generated by KSP. For the current source revision, **Build: NOT_RUN; Automated tests: NOT_RUN; Phone tests: NOT_RUN**. The earlier V0/V1 results below are historical and do not validate C5–C10 or the new Halo deadline code.
- C8 remains explicitly unsupported (`HALO_ACQUISITION_NOT_IMPLEMENTED`). Halo source head is `53ef62d5626832f2b9b59d00a1807eed6d36def9` on managed local `main`, 6 ahead / 33 behind the locally recorded `origin/main`; preserve it without rebase or push. Protocol v1.0 bytes still match at SHA-256 `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`. The additive optional `stageBudgets` JSON field keeps old v1.0 clients on their 60-second limit; new Lab requests carry the persisted values. The shared FAST fixture constant matches the canonical protocol JSON by source inspection only.

The starter's simulation-only phase and stop-after-one-starter-batch rule were superseded by the v1 continuation. Privacy, signing, repository authority and verification restrictions remain active.

## Active v1 continuation checkpoint — 2026-10-22

### C4/C8 source integration and authorized verification

- Lab source commit `1f328c9e16463751df1946a2b01e69a95d19f1eb` implements persisted real execution scope validation and C4 integration; this source is on `main` and pushed to the authorized public remote (latest docs checkpoint `e9eabe2a06387ce39078546a6c9621855231dad8`). The previous implementation/docs commits are `e348bc7f3661ffd913e61c0b892aff9a83fa4673` and `807e653caa4cc7f662a0569c0f63a19617074643`.
- Halo counterpart source is committed in authorized managed worktree `C:\Users\slavd\HaloEco\Halo-main-hml` at `75da9ca49bfeea2da6fd914dde5380e8f6745e9d`; preceding C4 source commits are `dfac0b073301003186eec71a53a111d8c74f154e` and `e590a9cb`. Local branch is `main`, four commits ahead and 32 behind `origin/main`. Halo was not pushed. The original dirty `Halo-main` checkout and its user change remain untouched.
- C4 permits only the fixed `fast-smoke/1/short-ready` FAST/CPU plan for a verified installed LiteRT artifact after explicit consent. The real request is reconstructed from persisted canonical plan/scope data. The bridge uses exact artifact leases, same-attempt recovery, replay/dedup before acknowledgement, cleanup confirmation and terminal output/backend/runtime verification. Halo journals bounded terminal output and available metrics. C8 remains explicitly unsupported because Halo answers `HALO_ACQUISITION_NOT_IMPLEMENTED`; Lab has no downloader, `INTERNET` permission or weight cache.
- Protocol bytes match exactly between Lab and Halo: SHA-256 `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`; Lab client schema SHA-256 `d866967284a236d37c39a720f7c13c46f39b1813f02f560c1857943727bcf231`.
- **Lab V0 PASS:** `:app:assembleDebug`, `:app:testDebugUnitTest` (95 passed, 0 failed), and `:app:assembleDebugAndroidTest` passed on JDK 21 / Android SDK 37. Current Lab debug APK SHA-256: `41759c1cad8c92f307487d7e33fdf1be8fc85edd3ba1182e36119bafc32be2a0`.
- **Phone instrumentation PASS:** `:app:connectedDebugAndroidTest` passed 21 tests, 0 failures on Xiaomi `23124RA7EO`, Android 15 / API 35. This verifies Lab instrumentation only.
- Halo V0 compile remains **FAIL** at `:app:compileDebugKotlin` due existing errors outside the C4 bridge/worker: semantic index/service API mismatches, `ManualDependencyGraph`, `HaloScreen`, onboarding, notebook, and `OnDeviceModelsScreen`. The repaired C4 files emitted no errors in the latest compile. Do not broaden this change to unrelated app code.
- **Manual Lab synthetic V1 not run:** installing the current Lab APK was blocked by Android `INSTALL_FAILED_USER_RESTRICTED`; the package did not install, so launch/synthetic ZIP check did not occur. The paired Halo app did not compile. Do not claim current phone app usability, real-model execution, native crash resilience, or model qualification.
- C6 source audit: added an explicit catalog capability matrix. FAST has only the fixed smoke; CHAT/THINK/ACTION are contract-only; VISION/STT/TTS/EMBEDDING/VAD show exact missing Lab protocol/fixture/lease paths despite existing Halo owner APIs. The catalog UI exposes these statuses. See `docs/CAPABILITY_MATRIX.md`. No multimodal execution is claimed.
- C5 follow-up: the fixed FAST smoke is now the only profile marked source-implemented. Added bounded pure validators for exact text, JSON-field, no-tool, fixture-tool-call and continuation assertions; transport-invalid observations remain UNKNOWN. CHAT/THINK/ACTION and warm-series contracts remain non-runnable pending adapter support. Added negative source tests; these new C5 tests have not been run yet.
- Source diff checks and exact protocol/schema hash checks passed. The v1 stage transition supersedes only the starter simulation-only scope limit and stop-after-one-starter-batch rule. Signing/privacy, repository authority, and verification restrictions remain in force.
Последнее обновление: 22 октября 2026. Исторические отчёты ниже относятся к прежним этапам; актуальные V0/V1 результаты зафиксированы в разделах ниже и в `docs/PENDING_VERIFICATION.md`.

## Продолжение v1 — 2026-10-22

## V0/V1 — авторизованная проверка — 2026-10-22

- Владелец отдельно разрешил V0/V1. Lab checkout оставался на `main`, исходный HEAD — `aad3e7fdc04827ad83fb4181746a44705e36d05d`; перед проверкой рабочее дерево было чистым. Halo остаётся отдельным dirty checkout, только для чтения.
- Исправлены ошибки компиляции в `RealBenchmarkContracts.kt` и доступности валидаторов `QualificationPolicy.kt`; исправлены невалидные/неполные фикстуры тестов. Room `findOpenSession` и `observeOpenSession` теперь выбирают состояния из `OPEN_NAMES`, что восстановило запрет второй сессии и отображение созданного черновика.
- Room 1→2 schema включена в instrumentation assets; schema v2 создана настоящей KSP-сборкой: `app/schemas/com.slafftrosheen.halomodellab.data.LabDatabase/2.json` (SHA-256 `3b269021e3647ccd15487c5f04d8e8477d7b61cdbaca1911487e542710f154c1`). Для `MigrationTestHelper` версии kotlinx.serialization выровнены до 1.8.1 в runtime configurations.
- V0 PASS: `:app:testDebugUnitTest` — 91 тест, 0 failures; `:app:assembleDebug` — PASS; `:app:assembleDebugAndroidTest` — PASS; `:app:connectedDebugAndroidTest` — 21 тест, 0 failures на Samsung SM-G990B2, Android 16 / API 36. Использованы JDK 21 и Android SDK 37.
- V1 synthetic smoke PASS: debug app установлена и открыта; `demo/success` сохранился и завершился как `SYNTHETIC`; экспортирован ZIP schema 2 (5,676 bytes), 9 checksum-записей проверены, абсолютных/`..` entry path не обнаружено. Архив явно говорит, что inference отсутствует. Не запускались реальные модели или долгие soak/fault-injection сценарии.
- APK SHA-256: `0adf72aba5c9dcb79c81fd1b63d283b471163bdf32a54f84fecf154bd378bb97`; source-fix commit: `015b497`.
- Реальная V1 приёмка остаётся `BLOCKED_HALO_WRITE_ACCESS`: нет авторизованного Halo editable target, моста, worker/supervisor, lease и реального adapter flow. Сборка не подтверждает native-crash resilience или real-model qualification. Установленная debug app содержит один synthetic smoke session; данные не очищались.
- Первые V0 прогоны выявили и устранили compile errors, two unit fixture errors, open-session SQL regression, missing schema assets и Room migration serialization runtime mismatch. Итоговые результаты выше относятся к последнему зелёному прогону.

- **Stage transition:** this v1 continuation supersedes only the starter's simulation-only scope limit and stop-after-one-starter-batch instruction. Signing/privacy, repository authority, and verification restrictions continue to apply.
- **C2 Lab-side contracts and replay importer implemented.** Added bounded typed bridge request/response contracts, explicit unknown-state decoding, protocol negotiation, typed event payloads, artifact lease and inventory DTOs, and a Room transaction that verifies payload hashes, rejects run identity conflicts/gaps, deduplicates committed events, stores attempt correlation, and advances only a contiguous cursor. Unit and instrumentation tests PASS in V0.
- C2 importer follow-up requires the source session to be a persisted `DEVICE_MEASUREMENT` real-bridge plan and attempt events to reference an attempt in that same session. Instrumentation uses a real plan and includes a synthetic-origin rejection case; PASS in V0.
- **C2 integration remains blocked at the authority boundary.** No Binder adapter, authenticated Halo service, consent store, manifest package binding, or canonical shared wire schema is claimed. The Lab protocol file remains a draft. `BLOCKED_HALO_WRITE_ACCESS` is recorded because the Halo checkout was not supplied as an authorized editable target.
- **C3 Lab inventory projection implemented as source.** Added bounded paging, explicit empty/unavailable results, duplicate-identity and pagination-cycle rejection, open-string role/backend decoding, and a verified-manifest precondition for lease eligibility. Unit tests PASS. The repository remains an adapter contract; it is not connected to live Halo inventory, leases, or runtime admission.
- **C4 blocked:** real worker, supervisor, native adapter, resource admission, and stop confirmation require Halo write access. No real executor is present in Lab.
- **C5 Lab text probe contracts implemented.** Versioned FAST, CHAT, THINK, ACTION and warm-series fixture descriptions now have canonical fixture hashes and bounded prompts/assertions. Unit tests PASS. Every profile remains `CONTRACT_ONLY`; C4 adapter execution, streaming measurements, validator execution and tool-call fixture dispatch are not wired.
- **C6 blocked by C4 and unverified Halo capability surfaces.** No STT/TTS/embedding/vision/VAD suite is advertised as runnable; Lab does not infer those capabilities from model names.
- **C9 policy source remains partial.** Current rule repairs and test sources are described in the latest checkpoint above. Observation persistence, exact-scope aggregation and History “Show excluded” are source-wired; current catalog filtering remains open; tests after the latest edits are NOT_RUN.
- **C7 bounded-run source remains partial.** Boot-aware accounting and the resume-safe harness circuit breaker are source-wired. Thermal cooldown, stage-budget and storage-quota gates are source-wired; full session-budget controller integration remains open; no unattended or native-crash claim is supported. Tests after the latest edits are NOT_RUN.
- **C10 export source remains partial.** The ZIP still has checksums, bounds and cancellation handling; this continuation adds bounded failure clusters and corrects origin-sensitive report language. Compatible-session comparison and complete real UI wiring are open. Latest tests are NOT_RUN.
- **C8 blocked:** acquisition must call Halo's existing owners; Lab has no downloader, model cache, or Halo endpoint.
- **C0: аудит исходников завершён; V0 source/unit/instrumentation checks PASS.** Основа L0–L4 присутствует в исходниках; ограниченный synthetic phone smoke тоже пройден, но не является real-model verification. См. `docs/TAKEOVER_AUDIT.md`.
- **C1: Lab-side real-plan contracts and durable schema implemented.** Added distinct plan/export/policy/protocol versions, canonical artifact and plan identity, exact role mapping, bounded budgets/configuration, strict unknown-value parsing, and draft profile identities. Added Room v1→v2 migration and plan/model/configuration/remote-event/output/qualification/acquisition tables; real plans are persisted with `DEVICE_MEASUREMENT` origin and explicit unavailable-artifact reasons. Generated schema v2 and migration test PASS in V0.
- **C1 protocol remains a draft.** `protocol/lab-client-contract-v1.json` is Lab-owned client metadata only. It is not a Halo canonical protocol and has no compatibility hash/commit pair. No Halo writes or live bridge behavior are claimed.
- Lab находится на `main`, базовый commit `9b09e78`; takeover начат с чистого дерева. Локальные commit разрешены, remote не настроен и не создавался, push не разрешён.
- Handoff разрешён как `HaloModelLab_CODEX_COMPLETE.md` в workspace; отдельные `docs/hml-codex` и attached continuation folder не обнаружены. `HaloModelLab_Agent_Pack/SPEC.md` сохранён неизменённым в `baseline/STARTER_SPEC.md`.
- Halo checkout прочитан только для интеграционной карты: HEAD `2d7b650faa3ade3c00f504f13bf9f91495d6b73d`, worktree уже был dirty. Editable Halo target не предоставлен: реальная bridge/worker/lease интеграция остановлена на `BLOCKED_HALO_WRITE_ACCESS`.
- Версия клиента пока `1`; canonical Halo protocol version, compatible commit pair, and protocol hash остаются `null` до согласованного canonical source на стороне Halo.
- Добавлен source regression для параллельного создания сессий. Existing source tests already cover repeated Start at repository boundary, terminal result immutability, process recovery, storage failure, origin mismatch, and four demo scenarios. Activity recreation and in-progress ZIP cancellation remain source gaps assigned to later batches.
- Historical L0–L4 verification results below are not current v1 evidence. Current V0/V1 results are recorded at the top of this file.
- Next: stop real-model work at `BLOCKED_HALO_WRITE_ACCESS`. V0 and the bounded synthetic phone smoke are complete; real-model V1 remains unverified because the authorized Halo target and integration are absent.

## Итог

Реализованы: приложение, четыре экрана, domain-контракт, demo-каталог, неизменяемые планы,
Room-хранилище, очередь, попытки, события, recovery, история с деталями, автономный
demo-исполнитель в foreground service, пауза/остановка, проверки зарядки/нагрева/места,
bounded wake lock, уведомление с Stop, телеметрия контроллера, настоящий ZIP-экспорт
с checksums, диагностический отчёт и безопасный share/save через FileProvider.

L4 — только обзор и точечные исправления: единая защита координатора от необработанных
исключений, отмена engine-корутины при уничтожении сервиса, различение storage-ошибки
при завершении очереди, очистка control-flags до запуска, удаление мёртвого кода.
Новых подсистем не добавлено.

Приложение **собрано** (`assembleDebug`, debug-подпись), unit-тесты **проходят**,
instrumentation-тесты **компилируются**. На устройстве не проверялось.

```text
Build: PASSED (assembleDebug, JDK 21, Gradle 9.6.0, AGP 9.4.0, compileSdk 37)
Automated tests: PASSED (testDebugUnitTest: 55 tests, 0 failed)
Instrumentation: sources compile (assembleDebugAndroidTest); execution NOT_RUN
Device tests: NOT_RUN
```

## Матрица проверки

| Область | Исходники | Тестовые исходники | Сборка | Тесты | Устройство |
|---|---|---|---|---|---|
| Domain, план, состояние | есть | `SessionStateMachineTest`, `QualificationGuardTest` | PASS | PASS | NOT_RUN |
| Room-схема, DAO, репозиторий | есть | `SessionRepositoryTest`, `RealPlanMigrationTest` | PASS | PASS (connected) | PASS (instrumentation) |
| Recovery после потери процесса | есть | `SessionRepositoryTest` | PASS | PASS (connected) | NOT_RUN (force-stop path) |
| Каталог и demo-сценарии | есть | `InstalledCatalogProjectionTest` | PASS | PASS | PARTIAL (`demo/success`) |
| Demo engine | есть | `FakeBenchmarkEngineTest` | PASS | PASS | PARTIAL (`demo/success`, synthetic) |
| Coordinator: pause/stop/budget/условия | есть | `SessionCoordinatorTest` | PASS | PASS | NOT_RUN (manual pause/stop) |
| Foreground service и уведомление | есть | — | PASS | — | PARTIAL (start + notification prompt) |
| История и детали | есть | — | PASS | — | PARTIAL (completed synthetic session observed) |
| ZIP-экспорт и отчёт | есть | `ReportFormatterTest`, `ReportExporterTest`, `RealEvidenceExportJsonTest` | PASS | PASS | PARTIAL (schema-v2 synthetic ZIP, checksums verified) |
| Share/save через FileProvider | есть | — | PASSED | — | NOT_RUN |
| Ручная приёмка (15 сценариев) | есть | — | — | — | PARTIAL (см. `docs/MANUAL_ACCEPTANCE.md`) |

## Что связано в исходниках

1. `SessionScreen` → `SessionViewModel` → `createSession`: транзакционная запись плана,
   очереди и события `SESSION_CREATED`.
2. Кнопка запуска → `BenchForegroundService.start()` (после запроса `POST_NOTIFICATIONS`
   на API 33+; отказ не отменяет запуск) → `startForeground` с типом `specialUse`.
3. Сервис создаёт `SessionCoordinator`; `SessionCoordinator.runSession`:
   `claimSession` → проверка условий → `beginAttempt` (BEFORE_EXECUTION) → `FakeBenchmarkEngine`
   → `recordStage` → `finishAttempt` → следующий пункт.
4. `withTimeout` ограничивает cooperative-попытку; `TimeoutCancellationException` даёт
   `TIMED_OUT`, Stop отменяет job и даёт `CANCELLED`, отключение зарядки/перегрев
   отменяют попытку с `validity=INVALID` и переводят сессию в `PAUSED`.
5. Исчерпание бюджета: `beginAttempt` возвращает `SESSION_BUDGET_EXHAUSTED`, оставшиеся
   пункты становятся `SKIPPED`, сессия — `COMPLETED`.
6. Ошибка записи журнала (`AttemptEventSink`) останавливает очередь и переводит сессию в
   `FAILED` с `STORAGE_FAILURE`; она не маскируется под FAILED модели.
7. Пауза завершает текущую ограниченную попытку и не запускает следующую; «после последней
   попытки» приоритет у `COMPLETED`.
8. Каждой попытке пишутся telemetry `free_storage` и `charging` со scope `device`;
   отсутствующее значение — null с причиной.
9. Уведомление содержит открытие приложения и Stop; прогресс и ETA не выдумываются.
10. История и детали читают Room; фильтры all/completed/interrupted/synthetic.
11. Кнопка «Экспортировать ZIP» в деталях сессии → `SessionDetailsViewModel.export()` →
    `ReportExporter.export(sessionId)`: проверка `RUNNING/PAUSING/STOPPING`, короткая
    read-транзакция `sessionDetail`, сборка DTO, `report.md` ≤ 8 KiB, checksums,
    запись `cache/exports/<name>.zip.part`, закрытие и переименование в `.zip`.
12. Готовый архив показывается только после успешного переименования. «Поделиться» отдаёт
    `content://…exports/…zip` через неэкспортируемый FileProvider с временным read grant;
    «Сохранить» использует `ACTION_CREATE_DOCUMENT`, отмена picker ничего не пишет,
    ошибка копирования показывается как failure, а не как успех.
13. Детали показывают ошибку экспорта с кодом (`SESSION_NOT_QUIESCENT`, `SESSION_MISSING`,
    `EXPORT_TOO_LARGE`, `EXPORT_FAILED`) и не называют архив готовым заранее.
14. `SessionCoordinator` имеет последний рубеж: неизвестное исключение репозитория даёт
    `COORDINATOR_FAILURE` и завершает сессию, а не убивает job сервиса без следа.
15. Отмена host-job (уничтожение сервиса) отменяет engine-корутину, иначе осиротевшая
    попытка продолжала бы писать в Room; `withTimeout` по-прежнему только для fake engine.
16. Завершение очереди различает «не удалось перейти в COMPLETED» и storage failure;
    условия предыдущей попытки не переносятся в следующую.
17. Сервис не теряет повторный Start, пришедший в момент завершения прошлого job:
    intent запоминается и запускается после освобождения очереди; протухший join не
    обнуляет состояние нового job и не останавливает сервис во время работы.

## Что недоступно по замыслу

- Настоящие модели, каталог Halo, мост, веса, inference, backend CPU/GPU.
- Удаление истории и сравнение с baseline.
- Hard timeout для native/JNI, отдельный процесс и supervisor — этап Codex.
- Ручное подтверждение запуска при `MODERATE` нагреве: v0 консервативно блокирует.
- Уведомление не обновляет прогресс в реальном времени: только состояние выполнения.
- Атомарная запись во внешний provider не гарантируется; частичный файл возможен.

## Пакет C0: первая сборка и её исправления

Сборка выполнена на JDK 21 (Adoptium из `~/.gradle/jdks`), Gradle 9.6.0 wrapper,
AGP 9.4.0, Kotlin 2.2.10, KSP 2.3.6, compileSdk 37. Найдено и исправлено:

1. `SessionViewModel.cancelOpenSession` — trailing-ламбда передавалась в последний
   параметр `launchStorageCommand` (там `successMessage`), а не в `block`; вызов переведён
   на именованные аргументы. Это была ошибка компиляции, не поведения.
2. `ExportJson`: Moshi `JsonWriter` по умолчанию пропускает null-значения —
   `"inferenceRuntime": null` молча выпадал из manifest.json, нарушая честность
   runtime-блока. Включён `serializeNulls` в обоих writer'ах (manifest/DTO и events),
   теперь null — явное поле схемы.
3. `SessionCoordinatorTest.plan()` — фикстура выставляла `repeatIndex = index` очереди
   при `repeatCount = 1`, что нарушает `require(repeatIndex in 0 until repeatCount)` для
   многосценарных планов. Исправлено на `repeatIndex = 0` для каждого пункта.
4. `ReportExporterTest.telemetryCsvEscapesAndNeutralizesFormulas` — ожидание
   `battery_temp,,celsius` не учитывало пустую колонку attemptId у session-level
   телеметрии; фактический формат `battery_temp,,,celsius` (обе колонки пусты, значение
   не подменяется нулём). Ожидание уточнено, проверка «пусто, а не 0» сохранена.
5. `app/build.gradle.kts` — добавлена недостающая `androidTestImplementation`
   зависимость `kotlinx-coroutines-test`: `SessionRepositoryTest` использует `runTest`.
6. Сгенерирована и добавлена Room-схема
   `app/schemas/com.slafftrosheen.halomodellab.data.LabDatabase/1.json` (identityHash
   `6536b41e873be1f22ba57f4f3e32183c`).

Ошибки исправлены по причине, без подавления: ни одного `@Ignore`, подавления
lint/type, ослабленного утверждения или destructive migration.

## Фактически выполненные проверки (C0)

1. Python-разбор 5 XML-файлов — без ошибок (manifest с сервисом и FileProvider,
   `export_paths.xml`).
2. Сверка `R.string.*`: все ключи определены; неиспользуемых нет.
3. Все Kotlin-пакеты совпадают с каталогами; TODO/FIXME/NotImplementedError отсутствуют
   (поиск по границам слов; вхождения `toDocument` — часть имени метода, не TODO).
4. Поиск запрещённого: нет `uses-permission android.permission.INTERNET`, сетевых SDK,
   Analytics, Firebase, OpenAI/OpenRouter, `includeBuild`, ссылок на Halo source tree.
5. Баланс скобок/кавычек в Kotlin-файлах: единственное «расхождение» — char-литералы
   `'{'`/`'('` в `SessionPlan.kt`, то есть ложное срабатывание эвристики.
6. `git diff --check` — без пробельных ошибок.
7. Source review: DTO mapping без потери origin/validity/reasonCode, сериализация через
   `JsonWriter` без reflection, лимиты секций, `.part` cleanup, FileProvider paths,
   ViewModel/Compose-состояния экспорта.
8. Обзор call graph UI → service → coordinator → repository → engine → export и обратной
   связи через Room/StateFlow; проверены отмена, освобождение wake lock, закрытие ZIP,
   удаление `.part` и видимость ошибок.
9. Поиск мёртвого кода по именам: удалены `isRunning`, `resetExport`, `activeQueueItem`;
   неиспользуемых `implementation`/`testImplementation` зависимостей не найдено.
10. Эвристика неиспользуемых импортов: срабатывания только на `getValue`/`setValue`,
    которые нужны для `by`-делегатов; реальных лишних импортов нет.
11. `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL; `app-debug.apk` собран.
12. `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL; 55 тестов, 0 падений
    (включая `ReportFormatterTest`, `ReportExporterTest`, `SessionCoordinatorTest`).
13. `./gradlew :app:assembleDebugAndroidTest` — BUILD SUCCESSFUL; instrumentation-исходники
    компилируются.

Эмулятор, ADB и проверки на телефоне не запускались; `connectedDebugAndroidTest` и
15 сценариев ручной приёмки остаются NOT_RUN.

## Следующий шаг

Пакеты L0…L4 и C0 завершены: сборка зелёная, unit-тесты зелёные. Дальше — C1 из
`docs/HANDOFF.md`: заморозка bridge-контракта (`HaloLabGateway`, `BridgeStatus`, типы
artifact hash/runtime version/adapter revision). Подключать Halo source tree нельзя.
Отдельно остаются устройство-проверки: `connectedDebugAndroidTest` и ручная приёмка.
