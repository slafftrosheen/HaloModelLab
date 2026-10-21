# Implementation status — Halo Model Lab

Последнее обновление: 21 октября 2026. Пакеты: **HML-L0 … HML-L2**.

## Итог

Реализованы: приложение, четыре экрана, domain-контракт, demo-каталог, неизменяемые планы,
Room-хранилище, очередь, попытки, события, recovery, история с деталями, автономный
demo-исполнитель в foreground service, пауза/остановка, проверки зарядки/нагрева/места,
bounded wake lock, уведомление с Stop и телеметрия контроллера.

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
| ZIP-экспорт | отсутствует (L3) | — | NOT_RUN | NOT_RUN | NOT_RUN |

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

## Что недоступно по замыслу

- Настоящие модели, каталог Halo, мост, веса, inference, backend CPU/GPU.
- ZIP-экспорт и share/save — HML-L3; удаление истории не реализовано.
- Hard timeout для native/JNI, отдельный процесс и supervisor — этап Codex.
- Ручное подтверждение запуска при `MODERATE` нагреве: v0 консервативно блокирует.
- Уведомление не обновляет прогресс в реальном времени: только состояние выполнения.

## Фактически выполненные статические проверки

1. `python`-разбор 4 XML-файлов — без ошибок (включая manifest с сервисом).
2. Сверка `R.string.*`: все ключи определены; неиспользуемых нет.
3. Все Kotlin-пакеты совпадают с каталогами; TODO/FIXME/NotImplementedError отсутствуют.
4. Поиск запрещённого: нет `INTERNET`, сетевых SDK, Analytics, Firebase,
   OpenAI/OpenRouter, `includeBuild`, ссылок на Halo source tree.
5. POM/Maven metadata: AGP 9.4.0 → Kotlin 2.2.10; Compose plugin 2.2.10; Room 2.8.5;
   KSP 2.3.6; core-ktx 1.18.0; androidx.test-артефакты существуют.
6. `git diff --check` — без пробельных ошибок.
7. Source review: транзакции репозитория, recovery, coordinator, service, условия,
   уведомления, ViewModel/Compose.

Сборка, unit-тесты, instrumentation, эмулятор, ADB и проверки на телефоне не запускались.

## Следующий разрешённый пакет

HML-L3 — экспорт настоящего ZIP, безопасный share и диагностический отчёт.
