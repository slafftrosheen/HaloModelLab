# Halo Model Lab

Изолированный Android-эксперимент: четыре экрана для планирования, запуска и разбора
локальных benchmark-сессий. В этой версии исполняется **только управляемая симуляция**;
настоящих моделей, весов, моста Halo и inference здесь нет.

> **Статус:** исходники HML-L0…HML-L4 собраны и покрыты unit-тестами (C0, 21.10.2026).
> `Build: PASSED (assembleDebug)`, `Automated tests: PASSED (55/55 testDebugUnitTest)`,
> `Device tests: NOT_RUN` — instrumentation-тесты компилируются, но на устройстве
> не запускались.

## Что можно увидеть после сборки

| Экран | Назначение |
|---|---|
| Каталог | Честно разделён на real (недоступен) и demo; выбор demo-сценариев |
| Сессия | План (повторы, таймаут, бюджет, зарядка), Start/Pause/Stop, прогресс очереди |
| История | Сессии из Room, фильтры all/completed/interrupted/synthetic |
| Детали | Очередь, попытки, события, телеметрия, экспорт ZIP и share/save |
| Настройки | Условия запуска, версии, границы v0 |

Demo-сценарии: `demo/success`, `demo/load-failure`, `demo/empty-output`, `demo/timeout`.
Каждый delay в demo engine — фикстура, а не latency модели.

## Что происходит при запуске

1. План и очередь пишутся в Room одной транзакцией.
2. Пользователь запускает foreground service; сервис создаёт `SessionCoordinator`.
3. Координатор проверяет зарядку/thermal/место, коммитит `BEFORE_EXECUTION` и вызывает
   `FakeBenchmarkEngine`.
4. Pause даёт текущей ограниченной попытке закончиться; Stop отменяет её; бюджет
   исчерпывается явным `SESSION_BUDGET_EXHAUSTED`.
5. Потеря процесса оставляет сессию в INTERRUPTED/NEEDS_DECISION; завершённые попытки
   не переписываются.

## Экспорт

`cache/exports/halo-model-lab_<sessionId>.zip`:

```text
manifest.json      schemaVersion=1, origin, план, omissions, files
report.md          ≤ 8 KiB, предупреждение о симуляции, уникальные ошибки
results.json       очередь, попытки, validity, reasonCode, метрики
events.jsonl       сохранённые события по seq
telemetry.csv      телеметрия контроллера (scope device/lab_process)
failures/          <attemptId>.json
checksums.sha256   SHA-256 всех payload-файлов и manifest
```

Архив строится из короткого снимка БД, а не из копии живой SQLite. Share идёт через
неэкспортируемый FileProvider с временным read grant; «Сохранить» использует системный
create-document picker. Сеть не запрашивается: разрешения `INTERNET` нет.

## Структура

```text
app/src/main/java/com/slafftrosheen/halomodellab/
  catalog/    demo-каталог и состояние выбора
  data/       Room: сущности, DAO, репозиторий, recovery
  domain/     контракты, состояния, план, read-модели
  export/     DTO, JsonWriter-сериализация, отчёт, ZIP, share
  runner/     fake engine, координатор, foreground service, условия, уведомления
  ui/         Compose-экраны и ViewModel
app/src/test/         unit-тесты (55/55 проходят)
app/src/androidTest/  Room-тесты (компилируются; запуск только на устройстве)
docs/                 статус, ожидающие проверки, ручная приёмка, handoff
```

## Сборка

Нужны JDK 17+, Android SDK platform 37 и Gradle wrapper 9.6.0 из репозитория.

```bash
./gradlew :app:assembleDebug            # проходит; APK signing debug-ключом
./gradlew :app:testDebugUnitTest        # проходит; 55 тестов
./gradlew :app:connectedDebugAndroidTest # НЕ запускался: нужен устройство/emulator
```

Сборка и unit-тесты выполнимы и зелёны; поведение на устройстве не проверялось.
Оставшиеся непроверенные места — в [docs/PENDING_VERIFICATION.md](docs/PENDING_VERIFICATION.md).

## Документы

- [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md) — что реализовано и что нет
- [docs/PENDING_VERIFICATION.md](docs/PENDING_VERIFICATION.md) — непроверенные места
- [docs/MANUAL_ACCEPTANCE.md](docs/MANUAL_ACCEPTANCE.md) — сценарии приёмки на устройстве
- [docs/HANDOFF.md](docs/HANDOFF.md) — передача следующему этапу (Codex)

## Границы

Нет облачных API и аналитики, нет загрузки весов, нет ADB/root, нет CI и signing keys.
Результаты симуляции нельзя считать пригодностью Qwen, Gemma, Kokoro или любой другой
модели. Настоящая интеграция с Halo — отдельный этап; см. handoff.
