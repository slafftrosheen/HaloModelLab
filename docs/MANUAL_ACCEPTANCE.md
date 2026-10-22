# Manual acceptance — Halo Model Lab v0

Полный набор сценариев ниже не выполнялся. После отдельного разрешения V1 пройден
ограниченный smoke `demo/success` и создан/проверен synthetic schema-v2 ZIP;
частичные результаты отмечены в таблице и не означают приёмку реальных моделей.

Сборка, установка, unit/instrumentation-тесты и ADB на этапе HML-L0…L4 запрещены,
поэтому статус остаётся `NOT_RUN` до отдельного разрешения владельца.

## Предусловия

- Устройство или эмулятор с API 26+ (для thermal-статуса нужен API 29+).
- Приложение собрано из этого репозитория; Halo и его runtime **не** подключены.
- На устройстве нет сохранённых сессий (чистая установка) либо известен текущий набор.

## Сценарии

| № | Сценарий | Ожидаемый результат | Статус |
|---|---|---|---|
| 1 | Чистая установка без Halo | Приложение открывается; real catalog честно недоступен (кнопка скачивания отключена, текста «найдено» нет) | NOT_RUN |
| 2 | Demo success + failure + timeout | Все три исхода сохранены в истории с разными outcome; очередь проходит без компьютера и без сети | PARTIAL — `demo/success` завершился как SYNTHETIC; failure/timeout не прогонялись |
| 3 | Двойное нажатие Start | Одна сессия и одна активная попытка; второй запуск отклоняется, счётчик попыток не растёт | NOT_RUN |
| 4 | Rotation / фон / закрытие Activity | Нет повторного старта; после возврата история и состояние читаются из Room без потерь | NOT_RUN |
| 5 | Экран выключен | Сервис продолжает работу в рамках бюджета; поведение проверяется отдельно на телефоне (Doze/вендорские ограничения) | NOT_RUN |
| 6 | Pause | Текущая ограниченная попытка заканчивается, следующая не начинается; сессия PAUSED | NOT_RUN |
| 7 | Stop | Cooperative-попытка отменена (CANCELLED/INVALID), новых запусков нет; сессия CANCELLED | NOT_RUN |
| 8 | Force-stop в активной попытке | После ручного открытия приложения сессия INTERRUPTED, открытая попытка INTERRUPTED/UNKNOWN; ранее сохранённые PASSED не переписаны | NOT_RUN |
| 9 | Продолжить прерванный пункт | Retry создаёт новый attemptId; старая попытка остаётся в истории неизменной | NOT_RUN |
| 10 | Отключить зарядку во время попытки (при requireCharging) | Попытка завершается INVALID с причиной POWER_CONSTRAINT, сессия PAUSED; вердикта «модель несовместима» нет | NOT_RUN |
| 11 | Нет thermal sensor / мало места | Понятный статус условия; отсутствующее значение не подменяется нулём и не выдаётся за измерение модели | NOT_RUN |
| 12 | Export | Создан настоящий ZIP: manifest.json, report.md (≤ 8 KiB), results.json, events.jsonl, telemetry.csv, reproduction/<attemptId>.json, artifacts/index.json, failures/<attemptId>.json, checksums.sha256; SYNTHETIC не переименован; `sha256sum -c` проходит на компьютере | NOT_RUN for C10 schema v2 source. Older synthetic ZIP/checksum evidence is historical; metric delta, reproduction entries, targeted failure ZIP, and real artifact payload behavior are unverified. |
| 13 | Отказать в notifications | Сессия всё равно запускается и завершается; отказ не трактуется как ошибка модели | NOT_RUN |
| 14 | Отменить create-document picker | Ничего не записано, ошибки нет, состояние экспорта остаётся готовым к повторному сохранению | NOT_RUN |
| 15 | Обновить приложение с сохранением данных | История и сессии сохраняются; destructive migration не используется, схема Room совместима | NOT_RUN |

## Дополнительно проверить руками при экспорте

1. Открыть ZIP на компьютере, убедиться, что entry не начинаются с `/` и не содержат `..`.
2. Сверить `checksums.sha256` со всеми файлами, кроме самого файла контрольных сумм.
3. Проверить, что `manifest.json` содержит `sessionOrigin`, `snapshotEventSeq`,
   `bridgeStatus=NOT_IMPLEMENTED`, `inferenceRuntime: null`, `omissions`.
4. Проверить, что в архиве нет абсолютных путей, серийников, Android ID, IMEI,
   ключей и приватных путей Halo.
5. Для сессии с несколькими отказами убедиться, что `report.md` группирует их по
   reasonCode/stage и ссылается на attemptId, а не пересказывает время событий.

## Команды для владельца или отдельного верификационного этапа

**Не выполнять на этапе HML-L0…L4.** Точная исполнимость команд, JDK, SDK 37 и
Gradle wrapper пока `NOT_RUN`.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Если сборка впервые падает, начинать с `docs/PENDING_VERIFICATION.md`: там перечислены
ожидаемые точки отказа (Room/KSP, Moshi `JsonWriter`, FileProvider, foreground service).
