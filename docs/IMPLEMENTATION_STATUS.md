# Implementation status — Halo Model Lab

Последнее обновление: 22 октября 2026. Историческая запись ниже отражает отчёты прошлой работы. В этой continuation сборки и тесты не запускались; актуальный статус — см. раздел «Продолжение v1».

## Продолжение v1 — 2026-10-22

- **Stage transition:** this v1 continuation supersedes only the starter's simulation-only scope limit and stop-after-one-starter-batch instruction. Signing/privacy, repository authority, and verification restrictions continue to apply.
- **C2 Lab-side contracts and replay importer implemented.** Added bounded typed bridge request/response contracts, explicit unknown-state decoding, protocol negotiation, typed event payloads, artifact lease and inventory DTOs, and a Room transaction that verifies payload hashes, rejects run identity conflicts/gaps, deduplicates committed events, stores attempt correlation, and advances only a contiguous cursor. Added unit and instrumentation test sources; they are **NOT_RUN**.
- **C2 integration remains blocked at the authority boundary.** No Binder adapter, authenticated Halo service, consent store, manifest package binding, or canonical shared wire schema is claimed. The Lab protocol file remains a draft. `BLOCKED_HALO_WRITE_ACCESS` is recorded because the Halo checkout was not supplied as an authorized editable target.
- **C3 Lab inventory projection implemented as source.** Added bounded paging, explicit empty/unavailable results, duplicate-identity and pagination-cycle rejection, open-string role/backend decoding, and a verified-manifest precondition for lease eligibility. The repository remains an adapter contract; it is not connected to live Halo inventory, leases, or runtime admission. Tests are **NOT_RUN**.
- **C0: аудит исходников завершён; текущая проверка только статическая.** Основа L0–L4 присутствует в исходниках, но её runtime/verification состояние не переутверждается. См. `docs/TAKEOVER_AUDIT.md`.
- **C1: Lab-side real-plan contracts and durable schema source implemented.** Added distinct plan/export/policy/protocol versions, canonical artifact and plan identity, exact role mapping, bounded budgets/configuration, strict unknown-value parsing, and draft profile identities. Added Room v1→v2 migration and plan/model/configuration/remote-event/output/qualification/acquisition tables; real plans are persisted with `DEVICE_MEASUREMENT` origin and explicit unavailable-artifact reasons. The generated schema v2 is intentionally pending V0.
- **C1 protocol remains a draft.** `protocol/lab-client-contract-v1.json` is Lab-owned client metadata only. It is not a Halo canonical protocol and has no compatibility hash/commit pair. No Halo writes or live bridge behavior are claimed.
- Lab находится на `main`, базовый commit `9b09e78`; takeover начат с чистого дерева. Локальные commit разрешены, remote не настроен и не создавался, push не разрешён.
- Handoff разрешён как `HaloModelLab_CODEX_COMPLETE.md` в workspace; отдельные `docs/hml-codex` и attached continuation folder не обнаружены. `HaloModelLab_Agent_Pack/SPEC.md` сохранён неизменённым в `baseline/STARTER_SPEC.md`.
- Halo checkout прочитан только для интеграционной карты: HEAD `2d7b650faa3ade3c00f504f13bf9f91495d6b73d`, worktree уже был dirty. Editable Halo target не предоставлен: реальная bridge/worker/lease интеграция остановлена на `BLOCKED_HALO_WRITE_ACCESS`.
- Версия клиента пока `1`; canonical Halo protocol version, compatible commit pair, and protocol hash остаются `null` до согласованного canonical source на стороне Halo.
- Добавлен source regression для параллельного создания сессий. Existing source tests already cover repeated Start at repository boundary, terminal result immutability, process recovery, storage failure, origin mismatch, and four demo scenarios. Activity recreation and in-progress ZIP cancellation remain source gaps assigned to later batches.
- Build: **NOT_RUN**. Automated tests: **NOT_RUN**. Phone tests: **NOT_RUN**. Historical results from 2026-10-21 below are not current verification for this continuation.
- Next: continue with other unblocked Lab-side batches (profile/fixture contracts, budgets, qualification, export). Live inventory, artifact leases, and admission remain blocked with the Halo integration boundary. No Gradle, test, emulator, ADB, install, or phone benchmark commands.

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
| Domain, план, состояние | есть | `SessionStateMachineTest`, `QualificationGuardTest` | PASSED | PASSED | NOT_RUN |
| Room-схема, DAO, репозиторий | есть | `SessionRepositoryTest` (androidTest) | PASSED | NOT_RUN (компилируется) | NOT_RUN |
| Recovery после потери процесса | есть | `SessionRepositoryTest` | PASSED | NOT_RUN (компилируется) | NOT_RUN |
| Каталог и demo-сценарии | есть | — | PASSED | — | NOT_RUN |
| Demo engine | есть | `FakeBenchmarkEngineTest` | PASSED | PASSED | NOT_RUN |
| Coordinator: pause/stop/budget/условия | есть | `SessionCoordinatorTest` | PASSED | PASSED | NOT_RUN |
| Foreground service и уведомление | есть | — | PASSED | — | NOT_RUN |
| История и детали | есть | — | PASSED | — | NOT_RUN |
| ZIP-экспорт и отчёт | есть | `ReportFormatterTest`, `ReportExporterTest` | PASSED | PASSED | NOT_RUN |
| Share/save через FileProvider | есть | — | PASSED | — | NOT_RUN |
| Ручная приёмка (15 сценариев) | есть | — | — | — | NOT_RUN |

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
