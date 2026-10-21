# Handoff to Codex — Halo Model Lab v0

## Current continuation status (2026-10-22)

This file is the historical v0 handoff. The active v1 continuation is resolved from the workspace single-file package `HaloModelLab_CODEX_COMPLETE.md`; current C0 findings and the read-only Halo map are in `docs/TAKEOVER_AUDIT.md` and `docs/HALO_INTEGRATION_MAP.md`. Build, automated tests, and phone tests for this continuation are all **NOT_RUN**. The prior 2026-10-21 results below remain historical reports only.

The Lab checkout is on `main` with no remote configured. The separate Halo checkout was inspected read-only and was already dirty; this continuation does not have authority to edit it. The bridge, leases, worker, and native supervisor remain `BLOCKED_HALO_WRITE_ACCESS`. See `docs/CONTINUATION_STATE.json` for the resumable batch and commit state.

C1 now defines and persists real-plan snapshots and the additive Room migration source. The migration's generated v2 schema and every C1 test remain `NOT_RUN`; the Lab client protocol file is a draft with no Halo compatibility claim.

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
| `export/ExportDtos.kt`, `export/ExportJson.kt` | схема v1; при добавлении полей runtime поднять `schemaVersion` |
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
