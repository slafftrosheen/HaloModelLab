# Halo Model Lab

## Continuation stage

This checkout has advanced from the starter's simulation-only phase into the v1 real-model-lab continuation. This transition replaces only the starter's L0–L4 scope limit and stop-after-one-starter-batch rule. The original privacy, signing, repository authority, and verification restrictions remain in force. The synthetic engine stays explicitly synthetic. Lab source includes an opt-in Halo bridge and a fixed FAST/CPU smoke path, but the paired application has not passed current verification. Acquisition is unsupported; Lab has no downloader, internet permission, or weight cache.

The resolved continuation handoff and C0 takeover record are tracked in `docs/TAKEOVER_AUDIT.md` and `docs/CONTINUATION_STATE.json`. See `docs/IMPLEMENTATION_STATUS.md` and `docs/PENDING_VERIFICATION.md` for current source and verification status.

Изолированная Android-лаборатория для планирования и разбора сессий. Synthetic-сценарии
остаются отдельными от Lab-owned real plans, а реальные запуски доступны только через
опт-ин Halo bridge и фиксированный FAST/CPU smoke contract. Запущенный Halo inference,
пригодный APK и устойчивость к native crash этим исходником пока не подтверждены.

> **Историческая проверка предыдущей ревизии (22.10.2026):** прошли 91 unit-тест,
> 21 connected instrumentation test и synthetic `demo/success` smoke. Эти результаты
> не проверяли более поздние C5–C10 изменения. Для текущего source head Build,
> Automated tests и Phone tests: **NOT_RUN**.

## Что можно увидеть после сборки

| Экран | Назначение |
|---|---|
| Каталог | Разделён на demo и opt-in real inventory; есть только фиксированный FAST/CPU plan; acquisition unsupported |
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

`cache/exports/halo-model-lab_<sessionId>.zip` (export schema v2):

```text
manifest.json      schemaVersion=2, origin, план, omissions, files
report.md          ≤ 8 KiB, warning based on evidence origin, targeted failure links
results.json       schemaVersion=2: очередь, попытки, validity, reasonCode, метрики
events.jsonl       сохранённые события по seq
telemetry.csv      телеметрия контроллера (scope device/lab_process)
coverage.json      bounded queue coverage; real execution stays unknown until reconciled
qualification.json exact local qualification records or explicit absence
comparison.json   explicit unavailable reason when no compatible comparison exists
failures/index.json links structured failure rows to bounded per-attempt evidence
real-plan.json     exact plan/configuration snapshot when a real plan exists
remote-events.jsonl bounded imported Halo facts when present; never proof by itself
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
app/src/test/         unit-тесты (91 проходят на проверенном V0 checkout)
app/src/androidTest/  Room-тесты (21 прошли на подключённом устройстве)
docs/                 статус, ожидающие проверки, ручная приёмка, handoff
```

## Сборка

Нужны JDK 17+, Android SDK platform 37 и Gradle wrapper 9.6.0 из репозитория.

```bash
./gradlew :app:assembleDebug            # PASS; APK signing debug-ключом
./gradlew :app:testDebugUnitTest        # PASS; 91 tests
./gradlew :app:connectedDebugAndroidTest # PASS; 21 tests на Android 16 / API 36
```

Устройство проверило только synthetic app flow и экспорт, не настоящую модель.
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

## Переход к v1 real-model laboratory — 2026-10-22

Этот этап продолжает starter simulation-only v0 и снимает только прежний scope-limit L0–L4 и правило остановиться после одного starter batch. В v1 работу продолжаем по C1–C10 в порядке зависимостей. Signing/privacy, авторитет владельца каждого репозитория и ограничения проверки остаются активны. Halo владеет model artifacts/acquisition; Lab хранит планы, результаты и отчёты, не имеет INTERNET permission и не кеширует веса. Код моста и worker пока source-only: в этой continuation Build, Automated tests и Phone tests не запускались; real-model acceptance остаётся NOT_RUN.
