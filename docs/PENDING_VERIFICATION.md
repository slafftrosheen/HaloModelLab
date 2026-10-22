# Pending verification — current v1 continuation

**Historical V0/V1 record (2026-10-22): Build PASS; automated tests PASS; device instrumentation PASS; synthetic manual smoke PASS. During the current source continuation, Build, Automated tests, and Phone tests are NOT_RUN. Real-model acceptance remains NOT_RUN pending the separately permitted verification stage.**

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

For this continuation, **Build: NOT_RUN; Automated tests: NOT_RUN; Phone tests: NOT_RUN.** Do not interpret the older V0/V1 PASS reports below as verification of the new bridge/worker source.

Before any later build or device acceptance, first commit the Lab and Halo source independently and record the exact compatible commit pair plus protocol SHA. Then, after separate verification authorization, run Lab unit/migration tests and assemble both apps; check AIDL generation and signing-certificate compatibility; install both permitted debug packages; enable Halo consent in Halo's model settings; perform handshake/schema validation and inventory read-only first. Only then try one verified local model with the fixed `fast-smoke/1/short-ready` CPU fixture. Inspect event replay/dedup after reconnect, cancel during native inference and confirm unload or worker death before another attempt, and confirm no provider selection/role change or Halo artifact mutation. Finally export one bounded ZIP and verify its reproduction manifest/checksums. Never download automatically; acquisition currently returns `HALO_ACQUISITION_NOT_IMPLEMENTED`. Do not infer native crash resilience or real-model qualification from a successful build or synthetic run.
