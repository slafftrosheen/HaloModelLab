# Handoff to Codex — Halo Model Lab v0

## v1 continuation checkpoint — 2026-10-22

The user authorized the Halo checkout at `C:\Users\slavd\HaloEco\Halo-main` for edits while preserving existing work, and authorized creating/pushing the public empty GitHub remote `slafftrosheen/HaloModelLab`. The Lab's original history is pushed to `origin/main`. Halo remains a separate local repository; no Halo changes have been pushed.

Source changes currently in progress in both repos define protocol v1.0, SHA-256 `264f430a5cd7b399f82130347572c72494a6a51f70a48f0eddcf83bc31090c3e`. Halo includes opt-in signed AIDL access, file/resource leases, durable attempt events, and an isolated worker for only the fixed CPU `fast-smoke/1/short-ready` test. Lab has an AIDL gateway exposed through `AppContainer`, but no real plan UI, session-coordinator engine, or remote event importer/export path uses it yet. Acquisition remains explicitly unsupported. C4 source work is partial; C6 and C8 remain incomplete.

Build / Automated tests / Phone tests are NOT_RUN for this continuation. Keep V0/V1 reports below as historical results only. The user asked for the source implementation without Gradle, builds, automated test runs, ADB, installation, or phone benchmarks.

## Current continuation status (2026-10-22)

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
