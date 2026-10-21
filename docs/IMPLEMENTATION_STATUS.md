# Implementation status — Halo Model Lab

Последнее обновление: 21 октября 2026. Пакеты: **HML-L0 … HML-L4**.

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

Приложение **не собрано**, **не протестировано** и **не проверено на устройстве**.

```text
Build: NOT_RUN
Automated tests: NOT_RUN
Device tests: NOT_RUN
```

## Матрица проверки

| Область | Исходники | Тестовые исходники | Сборка | Тесты | Устройство |
|---|---|---|---|---|---|
| Domain, план, состояние | есть | `SessionStateMachineTest`, `QualificationGuardTest` | NOT_RUN | NOT_RUN | NOT_RUN |
| Room-схема, DAO, репозиторий | есть | `SessionRepositoryTest` (androidTest) | NOT_RUN | NOT_RUN | NOT_RUN |
| Recovery после потери процесса | есть | `SessionRepositoryTest` | NOT_RUN | NOT_RUN | NOT_RUN |
| Каталог и demo-сценарии | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |
| Demo engine | есть | `FakeBenchmarkEngineTest` | NOT_RUN | NOT_RUN | NOT_RUN |
| Coordinator: pause/stop/budget/условия | есть | `SessionCoordinatorTest` | NOT_RUN | NOT_RUN | NOT_RUN |
| Foreground service и уведомление | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |
| История и детали | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |
| ZIP-экспорт и отчёт | есть | `ReportFormatterTest`, `ReportExporterTest` | NOT_RUN | NOT_RUN | NOT_RUN |
| Share/save через FileProvider | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |
| Ручная приёмка (15 сценариев) | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |

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

## Фактически выполненные статические проверки

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

Сборка, unit-тесты, instrumentation, эмулятор, ADB и проверки на телефоне не запускались.
`ReportFormatterTest` и `ReportExporterTest` добавлены как исходники и помечены NOT_RUN.

## Следующий шаг

Пакеты L0…L4 внешнего агента завершены. Дальше — C0 из `docs/HANDOFF.md`:
сборка и закрытие ошибок компиляции, затем заморозка bridge-контракта. Без отдельного
разрешения не запускать сборку и не подключать Halo.
