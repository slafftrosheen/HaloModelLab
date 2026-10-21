# Pending verification — current v1 continuation

**Current continuation status (2026-10-22): Build NOT_RUN; automated tests NOT_RUN; phone tests NOT_RUN.** The historical C0 report below records what the prior agent reported on 2026-10-21. It is not a verification result for the current source tree or this continuation.

No Gradle, build, automated test, emulator, ADB, install, or phone benchmark command was run in this continuation. Allowed source-only checks and their results are recorded in `docs/TAKEOVER_AUDIT.md` and `docs/CONTINUATION_STATE.json`.

C1 adds Room schema version 2 with a source-defined 1→2 migration. The v2 generated schema is intentionally absent until V0; migration and real-plan persistence instrumentation sources are **NOT_RUN**. C2 protocol/event import, C3 inventory, C5 text profile, C7 budget, C9 policy, and C10 export test sources are all **NOT_RUN**. Export schema v2 has not been built or opened as a ZIP. `protocol/lab-client-contract-v1.json` remains a Lab-local draft, so canonical protocol hash and compatible Halo commit pair are unknown. Binder security, consent enforcement, worker, leases, and server replay durability remain `BLOCKED_HALO_WRITE_ACCESS`.

The prior report must not be used to claim a usable APK, native crash resilience, or real-model success. Device and real-inference acceptance remains open. C1-C10 source verification will remain `NOT_RUN` until a separate V0/V1 authorisation.

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
