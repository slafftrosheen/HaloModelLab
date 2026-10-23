## Current checkpoint — 2026-10-23 — bounded evidence AIDL

Lab source commit: 779932a (C10 exporter/client). Halo source commits: a9d41429 and d1c87d58 (authenticated endpoint). Canonical protocol is v1.1 at SHA-256 b23f23b9812bb425862adaa98e5b4ad7549678967291ee6959909bbf6bf97859; both protocol files match, and both AIDL interface copies declare the same ParcelFileDescriptor method.

The API transfers only a complete terminal fixed-smoke text output, under a 2,048-byte cap, with Halo consent and caller signature/package checks. Model artifact IDs and bytes are rejected. Lab calls the API while exporting, bounds the read, and checks exact bytes and SHA-256 against the replay event. Other opaque artifact rows remain unavailable because Halo has no producer for them.

Host verification: current Lab assembleDebug PASS, current Lab unit tests 132/132 PASS, and Android test APK compiles. Halo assembleDebug PASS; focused HaloLabEvidenceArtifactTest PASS (4/4). Halo's full unit suite completed with 1,454 tests and 8 failures in existing provider initialization, language resource, brand settings, and workspace image recovery tests. Phone: all 24 existing Lab connected instrumentation tests passed after fixing the fake test clock's boot ID. Lab was installed and launched; the real catalog screen rendered. Tapping “Refresh from Halo” on the pre-fix APK returned `BRIDGE_NOT_SUPPORTED` even though the authenticated Halo AIDL service was registered and signatures matched. Lab's resolver now avoids `MATCH_DEFAULT_ONLY`, which hid Halo's package-scoped versioned service action; the fix and a focused AIDL handshake instrumentation test compile. The focused test did not run because Android blocked installation of the separate test APK with `INSTALL_FAILED_USER_RESTRICTED`; the latest Lab reinstall was also blocked and the Lab package is currently absent. The phone's “Apps from unverified developers” setting states that enabling it requires a 24-hour wait. Do not bypass this device protection. The resolver fix still needs a paired Binder smoke after Android permits the install.

Build: PASS. Automated tests: Lab unit 132/132 PASS; connected instrumentation 24/24 PASS before the latest AIDL resolver fix; focused AIDL test NOT_RUN because its test APK install was blocked; focused Halo 4/4 PASS; full Halo suite FAIL (8 existing failures). Phone UI launch/catalog PASS on the earlier APK; updated AIDL Binder smoke and model inference NOT_RUN pending Android install authorization. No real-model behavior or native-crash resilience is claimed.
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
# Pending verification — current v1 continuation

## Current source after C7/C9 completion — 2026-10-23

- Lab source commit `1384fb1` (including `4aa497d`) and Halo stage-boundary commit `8bee5863` have no build or automated-test evidence. Build / Automated tests / Phone tests remain **NOT_RUN**. New source tests cover exact stage deadline handling, thermal cooldown persistence/write failure, quota admission, exact-scope aggregation, and output assertion mapping; they were not executed.
- Lab explicitly wires the 256 MiB free-space reserve and 64 MiB Lab evidence cap. It reads only Lab database/WAL/SHM and report-export files, never Halo model storage. The cooldown store commits state transitions synchronously. Halo's separate process watchdog kills on a stage deadline, and the supervisor waits for worker death before releasing execution leases. These are source claims only; runtime compatibility, cleanup behavior, and native crash resilience are unverified.
- C9 review actions preserve evidence and offer a user-driven catalog retest. Automatic catalog exclusion remains unavailable until exact source/effective configuration identity is verified. No unknown scope is hidden.
- Lab commit `1384fb1` is pushed to authorized `origin/main`; Halo merge `5a4047fe` is pushed to Halo `origin/main`. The exact protocol v1.0 files still match byte-for-byte at SHA-256 `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`. The original dirty Halo checkout is untouched.

## Current source head after C7/C9/C10 edits

- Lab source commit `b20f9e1` and Halo stage enforcement commit `5b1ed695` are not yet covered by builds or automated tests. Current Build / Automated tests / Phone tests are **NOT_RUN**. Added C7 test sources cover the persisted circuit breaker and stage-deadline validation/reboot behavior; Halo source test covers canonical fixture identity. Exact protocol v1.0 files still parse and match byte-for-byte; this does not verify bridge runtime compatibility.
- Room schema 3 and its additive 2→3 migration need generation/validation on the separately authorized V0 pass. Protocol bytes match between Lab and the Halo managed worktree (SHA-256 `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`).
- Current Halo source compile previously failed on unrelated app errors. Halo branch still diverges from its local `origin/main` ref (4 ahead / 33 behind); no rebase or push has occurred.
- Manual Lab install previously returned `INSTALL_FAILED_USER_RESTRICTED`. No bypass was attempted; current APK usability, V1 synthetic export, paired Halo connection, real inference, cleanup-before-next-allocation, native crash resilience and real qualification remain unverified.

## C4/C8 checkpoint — 2026-10-22

- Lab source commit `1f328c9e16463751df1946a2b01e69a95d19f1eb` and docs checkpoint `e9eabe2a06387ce39078546a6c9621855231dad8` are pushed to authorized `origin/main`; Halo managed worktree source commit `75da9ca49bfeea2da6fd914dde5380e8f6745e9d`. Exact protocol bytes match (SHA-256 `2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c`); client schema SHA-256 is `d866967284a236d37c39a720f7c13c46f39b1813f02f560c1857943727bcf231`.
- Lab V0 PASS: debug assemble; 95 unit tests, 0 failures; Android test APK compile; 21 connected instrumentation tests, 0 failures on Xiaomi `23124RA7EO`, Android 15 / API 35. Debug APK hash: `41759c1cad8c92f307487d7e33fdf1be8fc85edd3ba1182e36119bafc32be2a0`.
- Halo app compile FAIL: `:app:compileDebugKotlin` reports unrelated preexisting errors in `FileVideoSemanticIndexStore`, `WorkspaceSemanticService`, `ManualDependencyGraph`, `HaloScreen`, onboarding, notebook and `OnDeviceModelsScreen`. No errors were reported from the changed C4 bridge/worker sources on the latest compile.
- Lab install attempt was blocked by Android `INSTALL_FAILED_USER_RESTRICTED`; the app was not installed or launched. Manual synthetic V1 and ZIP inspection are NOT_RUN. The Halo app also lacks a successful build, so paired app/real-model V1 is NOT_RUN.
- C6 source adds the explicit capability status list in the real catalog and `docs/CAPABILITY_MATRIX.md`. It records API owners and exact bridge/fixture gaps; no multimodal paths are wired. Build and source tests after C5/C6 edits are NOT_RUN.
- C5 source follow-up adds pure validators for text and fixture tool-call assertions, keeps adapter decode failures UNKNOWN, and marks only the fixed FAST smoke implemented. New C5 unit test sources are NOT_RUN because coding continues.
- C8 acquisition is explicitly unavailable (`HALO_ACQUISITION_NOT_IMPLEMENTED`). No Lab downloader, model cache, or `INTERNET` permission exists.
- Source/model compatibility at runtime, real inference, event recovery on phone, native cancellation/process death, cleanup-before-next-allocation, production Halo isolation, real ZIP reproduction checks, native crash resilience and qualification remain NOT_RUN. Never infer these from source compilation, unit tests, or Lab-only instrumentation.
## Historical verification records

V0 used JDK 21, Android SDK 37 and Windows Gradle wrapper 9.6.0. Final commands:

```text
:app:testDebugUnitTest                 PASS (91 tests, 0 failures)
:app:assembleDebug                     PASS
:app:assembleDebugAndroidTest          PASS
:app:connectedDebugAndroidTest         PASS (21 tests, 0 failures)
```

The connected device was a Samsung SM-G990B2 running Android 16 / API 36. The generated Room schema v2 and Room 1→2 migration test passed. The last build produced `app/build/outputs/apk/debug/app-debug.apk` (debug signed); it was installed and launched without clearing app data.

The bounded V1 smoke selected `demo/success`, created and ran a synthetic session, verified that History retained `COMPLETED` with `origin=SYNTHETIC`, exported the ZIP, and checked all 9 `checksums.sha256` records plus safe ZIP entry paths. ZIP schema was 2 and `report.md` explicitly states that real inference is absent. This is evidence for the synthetic Lab path only.

Real model discovery/acquisition, inference, worker/supervisor crash isolation, 30-minute standard run, soak, and Halo production-use isolation remain **NOT_RUN** because the Halo editable target and integration are blocked. No model artifact was downloaded. Do not claim a usable real-model laboratory, native crash resilience, or model qualification from the checks above. The Lab protocol file remains a draft; no compatible Halo commit pair exists.

The first verification passes surfaced compile/test fixture defects, an open-session query mismatch, missing migration schema assets, and a Kotlin serialization runtime mismatch in Room migration tests. Those were repaired before the final passing commands above.

---

# Historical pending verification — HML-L0…L4 + C0 (report dated 2026-10-21)

Сборка и unit-тесты **выполнены 21 октября 2026** (C0, JDK 21, Gradle 9.6.0,
AGP 9.4.0, compileSdk 37):

```text
./gradlew :app:assembleDebug            BUILD SUCCESSFUL (app-debug.apk собран)
./gradlew :app:testDebugUnitTest        BUILD SUCCESSFUL, 55 tests, 0 failed
./gradlew :app:assembleDebugAndroidTest BUILD SUCCESSFUL (только компиляция androidTest)
./gradlew :app:connectedDebugAndroidTest NOT_RUN — устройства/emulator нет
```

## Закрыто пакетом C0

1. **Room + KSP.** Room 2.8.5 + KSP 2.3.6 скомпилировались: `@Query` с `IN (:list)`,
   `SessionSummaryRow`, `combine`, `JournalMode` — без правок запросов.
2. **Схема Room.** `app/schemas/com.slafftrosheen.halomodellab.data.LabDatabase/1.json`
   сгенерирована первой сборкой и закоммичена.
3. **Toolchain.** compileSdk/targetSdk 37 (платформа установлена), JDK 21 из
   `~/.gradle/jdks`, Gradle wrapper 9.6.0 отработал. Системный `java` 1.8 не использовался.
4. **AGP 9 DSL и `ksp {}`.** Все блоки build.gradle.kts валидны.
5. **Moshi `JsonWriter`.** API проверен сборкой и тестами. Найденная и исправленная
   ошибка: `JsonWriter` по умолчанию пропускает null-значения, поэтому
   `"inferenceRuntime": null` молча выпадал из manifest.json; включён `serializeNulls`.
6. **Source-тесты.** `QualificationGuardTest`, `SessionStateMachineTest`,
   `FakeBenchmarkEngineTest`, `SessionCoordinatorTest`, `ReportFormatterTest`,
   `ReportExporterTest` — 55/55 проходят. В ходе C0 исправлены три ошибки самих
   фикстур/ожиданий (см. IMPLEMENTATION_STATUS, раздел C0): binding trailing-ламбды в
   `SessionViewModel`, `repeatIndex` в тестовом плане, ожидание CSV без attemptId-колонки.
7. **`kotlinx-coroutines-test` для androidTest.** Отсутствовавшая зависимость добавлена;
   `SessionRepositoryTest` компилируется.

## Требует запуска на устройстве/emulator

8. **Room-инварианты вживую.** Уникальность `(sessionId, seq)` и `(queueItemId, number)`;
   compare-and-set терминального результата; блокировка второй сессии в транзакции;
   recovery не меняет завершённые попытки. `SessionRepositoryTest` компилируется, но не
   запускался: `./gradlew :app:connectedDebugAndroidTest`.
9. **Foreground service.** `ServiceCompat.startForeground` с типом `specialUse` (API 34+),
   manifest `<property>` subtype, `FOREGROUND_SERVICE_SPECIAL_USE`; на API 34+ сервис
   обязан стартовать из видимого UI, иначе `ForegroundServiceStartNotAllowedException`.
10. **Уведомления.** Канал, `POST_NOTIFICATIONS` на API 33+, Stop `PendingIntent` с
    `FLAG_IMMUTABLE`, отказ в разрешении (уведомление подавляется, сессия продолжается).
11. **Условия запуска.** `BatteryManager.BATTERY_PROPERTY_STATUS`,
    `PowerManager.currentThermalStatus` (API 29+), `StatFs`, `PARTIAL_WAKE_LOCK` с timeout.
    v0 трактует `MODERATE` как блокирующий: ручного подтверждения нет — осознанное
    ограничение, а не поведение «как в SPEC».
12. **UI.** Поворот не создаёт сессию/попытку; повторный Start не запускает второй run;
    Stop во время `demo/timeout` даёт CANCELLED; share-sheet и `ACTION_CREATE_DOCUMENT`
    на API 26/33/34.
13. **Иконка launcher** не задана (системная заглушка). `windowLightNavigationBar` —
    атрибут API 27 при minSdk 26.
14. **Экспорт вживую.** ZIP открывается на компьютере, `sha256sum -c checksums.sha256`,
    `failures/` по файлу на attemptId, `report.md` ≤ 8192 байт; выдача URI FileProvider,
    отсутствие доступа к другим файлам cache.
15. **Частичная запись.** Копирование во внешний provider не атомарно; при обрыве частичный
    файл у провайдера может остаться. Документированное ограничение, не гарантия.
16. **Лимит 32 MiB.** Вывод `EXPORT_TOO_LARGE` тестом не покрыт (нужен очень большой
    фикстур); проверка срабатывает только при суммарном размере payload свыше лимита.
17. **L4-гонки.** Отмена engine-корутины при уничтожении сервиса, `COORDINATOR_FAILURE`,
    «Start → Pause до первого suspension», отмена экспорта во время записи ZIP,
    повторный Start во время завершения job — код исправлен, но сценарии не воспроизводились.
18. **Главный поток сервиса.** Оркестрация на `Dispatchers.Main.immediate`; доступность
    Main dispatcher во всех процессах не проверялась (в v0 процесс один).
19. **Ручная приёмка.** `docs/MANUAL_ACCEPTANCE.md` — 15 сценариев, все `NOT_RUN`.
20. **Boundary.** v0: UI, Room, service и fake engine — один процесс. Это не защита от
    native-падения будущего inference; отдельный процесс и supervisor — этап Codex.

## Не входит в текущий scope

21. Настоящий inference, мост Halo, веса, IPC — следующий этап Codex.
22. Удаление истории и сравнение baseline — не реализованы.

## Что нельзя делать при проверке

- Не добавлять network/analytics SDK и разрешение `INTERNET`.
- Не подключать Halo как source dependency и не копировать его runtime.
- Не включать destructive migration «чтобы собиралось».
- Не выдавать успешный `assembleDebug` за подтверждение реального inference.
- Не подменять отсутствующий thermal/battery-датчик нулём или «всё хорошо».
- Не помечать SYNTHETIC-архив как DEVICE_MEASUREMENT и не добавлять фиктивные model hash.
- Не расширять FileProvider до `root-path` или всего `filesDir`.
- Не утверждать, что ZIP проверен, пока он не открыт и checksums не сверены.
# Active v1 verification gate — 2026-10-22

## C10 export additions — 2026-10-23

- Build: **NOT_RUN**. Automated tests: **NOT_RUN**. Phone tests: **NOT_RUN** for the metric comparison schema v2, per-attempt reproduction files, artifact metadata index, and ZIP checksum/size behavior.
- Later source checks must cover exact-scope/policy matching, per-case metric identity, invalid/environmental sample exclusion, null unknowns, non-finite delta handling, comparison JSON bounds, reproduction path safety/redaction, 200-entry caps, fixed-smoke allowlisting, terminal/payload hash checks, truncated preview rejection, and checksums covering every new ZIP member.
- Opaque artifact payload transfer remains **BLOCKED_BY_PROTOCOL_IMPLEMENTATION**: Lab's `openEvidenceArtifact` has no matching AIDL method in the current Halo bridge and currently returns null. A complete output preview is separately allowlisted only for the packaged fixed FAST smoke fixture after event hash, output hash, length and truncation checks. Do not generalize preview export to arbitrary cases. Any future opaque transfer requires a versioned, permission-checked bounded API and lease/copy cleanup before export.
- Follow-up Lab source commit `738ff79` adds queue/remote-event coverage counts and qualification status/exact-scope exclusion summaries to the report. The report reserves appendix room within its 8 KiB cap. Only the explicit persisted `REJECTED_FOR_PROFILE` status is named as excluded; unknown status values are reported as `UNKNOWN_STATUS`. A new test source covers this mapping. Build, automated tests, and phone tests remain **NOT_RUN** for this source revision.

For this continuation, **Build: NOT_RUN; Automated tests: NOT_RUN; Phone tests: NOT_RUN.** Do not interpret the older V0/V1 PASS reports below as verification of the new bridge/worker source.

Before any later build or device acceptance, first commit the Lab and Halo source independently and record the exact compatible commit pair plus protocol SHA. Then, after separate verification authorization, run Lab unit/migration tests and assemble both apps; check AIDL generation and signing-certificate compatibility; install both permitted debug packages; enable Halo consent in Halo's model settings; perform handshake/schema validation and inventory read-only first. Only then try one verified local model with the fixed `fast-smoke/1/short-ready` CPU fixture. Inspect event replay/dedup after reconnect, cancel during native inference and confirm unload or worker death before another attempt, and confirm no provider selection/role change or Halo artifact mutation. Finally export one bounded ZIP and verify its reproduction manifest/checksums. Never download automatically; acquisition currently returns `HALO_ACQUISITION_NOT_IMPLEMENTED`. Do not infer native crash resilience or real-model qualification from a successful build or synthetic run.
