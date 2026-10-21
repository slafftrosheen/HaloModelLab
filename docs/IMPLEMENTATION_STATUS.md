# Implementation status — Halo Model Lab

Последнее обновление: 21 октября 2026. Пакеты: **HML-L0 … HML-L1**.

## Итог

Реализованы: самостоятельное Android-приложение, четыре экрана, полный domain-контракт,
qualification guard, каталог с честной demo-границей, неизменяемые планы сессий, очередь,
попытки, события, Room-хранилище, восстановление прерванного владельца, история с фильтрами
и экран деталей.

Приложение **не собрано**, **не протестировано** и **не проверено на устройстве**.
Настоящего inference, моста Halo, очереди исполнения и экспорта пока нет.

```text
Build: NOT_RUN
Automated tests: NOT_RUN
Device tests: NOT_RUN
```

## Матрица проверки

| Область | Исходники | Тестовые исходники | Сборка | Тесты | Устройство |
|---|---|---|---|---|---|
| Domain-контракт, план, состояние | есть | `SessionStateMachineTest`, `QualificationGuardTest` | NOT_RUN | NOT_RUN | NOT_RUN |
| Room-схема, DAO, репозиторий | есть | `SessionRepositoryTest` (androidTest) | NOT_RUN | NOT_RUN | NOT_RUN |
| Recovery после потери процесса | есть | `SessionRepositoryTest` | NOT_RUN | NOT_RUN | NOT_RUN |
| Каталог и demo-сценарии | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |
| План сессии, сохранение черновика | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |
| История и детали | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |
| Очередь, service, coordinator | отсутствует (L2) | — | NOT_RUN | NOT_RUN | NOT_RUN |
| ZIP-экспорт | отсутствует (L3) | — | NOT_RUN | NOT_RUN | NOT_RUN |

## Что связано в исходниках

1. `MainActivity` → `LabApp` → `NavHost`: каталог, сессия, история, настройки и вложенный
   маршрут `session_details/{sessionId}`.
2. `AppContainer` создаёт один `LabDatabase` и один `RoomSessionRepository`; recovery
   запускается один раз на процесс и публикует `StartupState` (Recovering/Ready/Failed).
3. Каталог → `CatalogRepository` → выбор demo-сценариев → `SessionViewModel`.
4. `SessionViewModel` валидирует параметры (повторы 1–20, таймаут 1–120 с, бюджет 1–60 мин)
   и вызывает `createSession(plan)`: одна транзакция пишет `sessions` + `queue_items` +
   событие `SESSION_CREATED`. Вторая незавершённая сессия блокируется в транзакции.
5. `SessionRepository` реализует `claimSession`, `beginAttempt` (BEFORE_EXECUTION до запуска),
   `recordStage`, `finishAttempt` (compare-and-set), pause/stop/complete/fail/cancel,
   `skipRemaining`, `retryItem`, `skipItem`, `recoverInterruptedOwner`.
6. `HistoryViewModel` читает проекцию `SessionSummaryRow` из Room и фильтрует
   все/завершённые/прерванные/synthetic; детали показывают план, пункты, попытки, ошибки,
   события и телеметрию (пустую до L2).
7. Каждое изменение состояния и его событие пишутся в одной транзакции; `seq` выдаётся
   внутри транзакции через `lastSeq + 1`, а не `count() + 1`.

## Что недоступно по замыслу

- Настоящие модели, каталог Halo, мост, веса, inference, backend CPU/GPU.
- Очередь исполнения, foreground service, условия запуска, уведомления — HML-L2.
- ZIP-экспорт, FileProvider, share/save — HML-L3.
- Удаление истории (отдельное подтверждённое действие) не реализовано.
- Выбор сценариев в каталоге остаётся в памяти; план после сохранения неизменяем.
- Длительность/ETA не оценивается: судьба бюджета решается только исполнителем.

## Фактически выполненные статические проверки

Выполнялись чтение, разбор XML, сверка строк, поиск по исходникам и git-операции.

1. `python`-разбор 4 XML-файлов (`AndroidManifest.xml`, `strings.xml`, `colors.xml`,
   `themes.xml`) — без ошибок.
2. Сверка `R.string.*`: все использованные ключи определены, неиспользуемых нет
   (`app_name` используется из manifest).
3. Все Kotlin-пакеты совпадают со структурой каталогов; TODO/FIXME/NotImplementedError нет.
4. Поиск запрещённого: нет `uses-permission`, сетевых SDK, Analytics, Firebase,
   OpenAI/OpenRouter, `includeBuild`, ссылок на Halo source tree.
5. Чтение POM/Maven metadata: AGP 9.4.0 зависит от Kotlin 2.2.10 (совпадает с Compose
   plugin 2.2.10); Room 2.8.5, KSP 2.3.6, lifecycle 2.11.0, activity-compose 1.10.1,
   navigation-compose 2.10.1, Compose BOM 2026.09.00 и androidx.test-артефакты существуют.
6. `git diff --check` — без пробельных ошибок (см. отчёт пакета).
7. Source review всех изменённых файлов, включая Room-запросы, транзакции и recovery.

Сборка, unit-тесты, instrumentation-тесты, эмулятор, ADB и проверки на телефоне
не запускались.

## Следующий разрешённый пакет

HML-L2 — автономный demo runner, foreground service и условия запуска.
