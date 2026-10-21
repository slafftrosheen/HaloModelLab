# Halo Model Lab — спецификация изолированной основы v0

Дата: 2026-10-21. Документ: HML-SPEC-001, редакция 0.1.
Владелец: Slaff. Первый исполнитель: сторонний агент. Интегратор: Codex.

## 1. Назначение

Model Lab — постоянно установленное на телефоне, но вычисляющее только по запросу
Android-приложение. Пользователь выбирает модели, конфигурации и проверки, запускает
очередь, оставляет телефон на зарядке, позже просматривает результаты и экспортирует
один диагностический архив для coding-agent. Компьютер, ADB, облачный LLM и агент,
который непрерывно наблюдает вывод, не являются частью тестовой сессии.

Конечная система должна отличать проблему модели от проблемы адаптера, runtime,
backend, условий запуска и самой тестовой инфраструктуры. Неустановленная причина
остаётся неустановленной. Пригодность оценивается для конкретной конфигурации и роли,
а не по одному имени модели.

### Граница v0

Первый агент реализует инфраструктуру и детерминированный симулятор. Не реализует
AI-inference, интеграцию с Halo, реальные загрузки, IPC, native crash isolation или
автоматическое исключение настоящих моделей.

Рабочий пользовательский путь v0:

`Включить симуляцию → выбрать сценарии → настроить очередь → запустить →
увидеть сохранённую историю → экспортировать настоящий ZIP`.

Это явное ограничение объёма, а не допустимость фальшивой реализации. Кнопки очереди,
база, восстановление, уведомление и экспорт должны быть настоящими. Только модельный
исполнитель — синтетический.

## 2. Репозиторий, сборка, зависимости

Отдельный репозиторий `HaloModelLab`. Один Gradle-модуль `:app`, без KMP и без
подключения исходников Halo. Предлагаемые `namespace` и `applicationId`:
`com.slafftrosheen.halomodellab`. Название в launcher: `Halo Model Lab`.

Kotlin, Jetpack Compose/Material 3, ViewModel/StateFlow, coroutines, Room через KSP,
Moshi для типизированных экспортных DTO, JUnit для исходников unit-тестов. Ручная
constructor injection в небольшом `AppContainer`; никакого глобального service locator,
Hilt/Koin, web frontend или собственного dependency injection framework.

### Снимок технологической базы Halo

Значения ниже прочитаны из файлов Halo 21 октября 2026. Использовать как фиксированную
начальную базу нового проекта, а не утверждение, что её совместимость здесь уже
проверена. При обнаруженном конфликте описать его в `docs/PENDING_VERIFICATION.md`;
не устраивать самостоятельную миграцию Halo или бесконечный подбор версий.

| Параметр | Значение в прочитанном Halo |
|---|---|
| minSdk / compileSdk / targetSdk | 26 / 37 / 37 |
| Java source/target | 17 |
| AGP | 9.4.0 |
| Gradle wrapper distribution | 9.6.0 |
| Kotlin Compose plugin | 2.2.10 |
| KSP | 2.3.6 |
| Compose BOM | 2026.09.00 |
| Room | 2.8.5 |
| Coroutines | 1.10.2 |
| Core KTX | 1.18.0 |
| Lifecycle runtime / ViewModel / Compose | 2.11.0 |
| Activity Compose | 1.10.1 |
| Navigation Compose | 2.10.1 |
| Moshi Kotlin | 1.15.2 |
| JUnit | 4.13.2 |

Не копировать полный build.gradle Halo: Firebase, camera, wearable, ML runtimes,
release signing, secrets plugin и другие посторонние зависимости здесь не нужны.
AGP 9 имеет встроенную поддержку Kotlin: не добавлять автоматически старый
`org.jetbrains.kotlin.android` рядом с ней [A7]. Согласованность фактически разрешённых
Kotlin/Compose/KSP версий должна быть проверена при разрешённой сборке, а не объявлена
по содержимому таблицы. Все версии фиксировать; `+`, SNAPSHOT и динамические версии запрещены.

Gradle wrapper должен быть настоящим стандартным wrapper соответствующей версии,
со всеми необходимыми файлами. Не создавать фиктивный `.jar` и не подменять wrapper
скриптом загрузки неизвестного происхождения. Отсутствующий инструмент записать как
блокер; не утверждать, что проект собирается.

## 3. Жёсткие границы первого исполнителя

Работать только внутри HaloModelLab. Не редактировать Halo, его submodules, keys,
permissions, manifest, runtime, настройки Gradle или каталог моделей. Не использовать
`includeBuild("../Halo")`, абсолютные пути к его исходникам и копию всего репозитория.

Не добавлять OpenAI/OpenRouter API, фонового агента, удалённый backend, аккаунты,
телеметрию, crash-reporting SaaS, Analytics SDK или `INTERNET` permission.
Не добавлять root, Shizuku, shell/ADB execution, Accessibility, `MANAGE_EXTERNAL_STORAGE`,
`QUERY_ALL_PACKAGES`, `sharedUserId`, чтение чужого logcat или обход sandbox.

Не скачивать веса моделей и не писать сетевой downloader. Не имитировать реальные
проценты загрузки. Не получать и не коммитить signing keystore/пароли. Не создавать
GitHub Actions. Не запускать сборки, тесты, эмуляторы, ADB и длительные benchmark-сессии
во время coding-прохода. Исходники тестов добавлять, но честно помечать `NOT_RUN`.

Разрешены чтение файлов и официальной документации, source-level review, проверка
структуры JSON/XML, `git diff --check`, обычные локальные git-операции в целевом
репозитории. Не удалять чужие изменения и не делать reset/force push. Коммиты — в
`main` нового репозитория, если владелец выдал право коммитить; push — только при
явно предоставленных полномочиях. Автоматически создавать remote не нужно.

## 4. Архитектура v0

```text
Compose UI / ViewModels
           |
SessionRepository (наблюдение за Room)
           |
SessionCoordinator ← BenchForegroundService
           |
BenchmarkEngine
    ├── FakeBenchmarkEngine          [v0, SYNTHETIC]
    └── HaloBenchmarkEngine          [позже, сейчас отсутствует]
           |
Room: sessions / queue_items / attempts / events / telemetry
           |
ReportExporter → локальный ZIP → Android share / Save document
```

В v0 UI, Room, service и fake engine находятся в одном процессе. Никаких native
библиотек и рискованных вычислений в этом процессе нет. Это не обещание устойчивости
к нативному падению будущего настоящего inference — отдельный процесс и supervisor
являются задачей Codex.

Service владеет выполнением; ViewModel только посылает команды и наблюдает базу.
Поворот экрана, смена вкладки и пересоздание Activity не создают новую очередь.
`AppContainer` принадлежит Application; сервис и экраны используют один repository.

В каждый момент существует максимум одна незавершённая пользовательская сессия и
одна активная попытка. DRAFT, PAUSED и INTERRUPTED тоже занимают место: прежде создания
новой сессии пользователь завершает/отменяет прежнюю. createSession проверяет это в
транзакции, а не только выключает кнопку UI. Использовать singleton-строку `runner_slot`
для владельца исполнения и условные обновления состояния. При PAUSED/terminal slot
освобождается; незавершённая session row продолжает запрещать создание второй сессии.
Повторный Start идемпотентен. Session/queue/attempt IDs генерировать внутри repository
как UUID; не использовать введённый пользователем текст как идентификатор или путь.

Состояния и события сохраняются до начала следующего потенциально проблемного шага.
Room — единственный источник истины для управляющих событий. JSONL формируется из
него при экспорте; не поддерживать две независимые «главные» копии журнала.

## 5. Экранные потоки

### 5.1 Каталог

Начальный режим — «Реальные модели». Пока интеграция не подключена, показать:
«Подключение к Halo ещё не реализовано. Реальные модели недоступны».
Не выводить фиктивные Qwen/Gemma/Kokoro как обнаруженные на телефоне.

Отдельный явный переключатель «Симуляция инфраструктуры». В этом режиме появляются
сценарии `demo/success`, `demo/load-failure`, `demo/empty-output`, `demo/timeout`.
Идентификаторы не совпадают с настоящими model IDs. У каждой карточки заметная метка
«СИМУЛЯЦИЯ — не результат проверки AI-модели». Поиск по имени сценария работает.

Загрузка настоящих моделей недоступна, с объяснением; никаких работающих на вид
пустых кнопок. Переключатель «Показать исключённые» зарезервирован для следующего этапа
и пока не скрывает настоящие модели по synthetic данным.

### 5.2 Новая сессия

Выбрать один или несколько demo-сценариев, порядок, число повторений 1–20,
таймаут попытки 1–120 секунд, общий активный бюджет 1–60 минут,
«Только на зарядке» по умолчанию включено. Значения валидируются до сохранения.

Снимок плана сохраняется неизменяемым до старта. Один пункт очереди соответствует
одному сценарию и одному плановому повторению. Повтор прерванного пункта — новая
попытка того же пункта; плановое повторение — отдельный пункт.

У v0 один профиль `DEMO_INFRASTRUCTURE_V1`. Не предлагать настоящие SMOKE/SOAK как
доступные inference-проверки. Их место предусмотрено в снимке конфигурации, но профили
содержательных тестов добавляет Codex.

### 5.3 Активная сессия

Показывать текущий пункт, стадию, время попытки, число законченных/всех пунктов,
ошибки, состояние зарядки и доступные датчики. Не показывать выдуманный ETA.
Прогресс очереди — по числу завершённых пунктов, не по произвольному таймеру.

«Пауза» означает закончить текущую ограниченную таймаутом попытку, затем не запускать
следующую. Текст кнопки/состояния: «Пауза после текущей проверки». «Остановить» отменяет
активную cooperative-попытку и прекращает очередь. Экстренное условие безопасности
останавливает текущую попытку немедленно через cancellation.

Разрешить продолжить paused-сессию по явному действию пользователя. После process
death требовать явного решения: повторить прерванный пункт или пропустить его.
Не замораживать и не «восстанавливать» состояние inference внутри попытки.

### 5.4 История, подробности, экспорт

История читается из БД. Фильтры: все, завершённые, прерванные, synthetic.
В деталях — план, каждая попытка, события и достоверность причин. Одна неуспешная
попытка не делает весь сеанс технически незавершённым: `COMPLETED` означает, что очередь
обработана, а не что все проверки успешны.

Экспорт v0 доступен в состояниях PAUSED, INTERRUPTED, COMPLETED, CANCELLED, FAILED.
Для RUNNING/PAUSING/STOPPING предложить сначала остановить или поставить на паузу;
не держать долгую транзакцию SQLite, пока ZIP записывается на диск.

### 5.5 Настройки

Настройки условий запуска, лимиты хранения/экспорта, версия приложения и протокола,
описание ограничений v0. Удаление истории — только отдельным подтверждённым действием.
Не удалять историю автоматически и не удалять веса моделей.

UI: простой тёмный Material 3, системные шрифты, читаемые подписи и большие кнопки,
portrait/landscape без перекрытий. Русский интерфейс; все строки в resources,
подготовленные к локализации. Брендбук Halo, Lottie и pixel-perfect перенос не входят
в этот эксперимент. Скрытых заглушек и ложных статусов быть не должно.

## 6. Состояния и восстановление

### Сессия

`DRAFT → RUNNING → COMPLETED`.
Дополнительно:

- `RUNNING → PAUSING → PAUSED → RUNNING`.
- `RUNNING/PAUSING → STOPPING → CANCELLED` по явному Stop.
- `RUNNING/PAUSING/STOPPING → INTERRUPTED` при восстановлении после потери процесса.
- `DRAFT/PAUSED/INTERRUPTED → CANCELLED` при отказе продолжать.
- `INTERRUPTED → RUNNING` только после явного решения пользователя и успешных проверок условий.
- Ошибка собственной инфраструктуры, не позволяющая продолжать, ведёт к `FAILED`,
  если это ещё удаётся сохранить. Если БД недоступна — не заявлять сохранение статуса.

При pause после последней попытки приоритет имеет COMPLETED. Терминальные сессии
не перезаписываются. Новый запуск того же плана создаёт новую sessionId.

### Попытка

Активные состояния: PREPARING, RUNNING, FINALIZING. Терминальные результаты:
PASSED, FAILED, TIMED_OUT, CANCELLED, INTERRUPTED, SKIPPED. Терминальный результат
может быть записан лишь один раз, через compare-and-set в транзакции.

Состояние пункта очереди: PENDING, ACTIVE, DONE, SKIPPED, NEEDS_DECISION.
События имеют уникальную последовательность `seq` внутри сессии; выдавать её в БД,
не вычислять как `count()+1` без транзакции. `attemptId` новый для каждого повтора;
`queueItemId` стабильный; `(queueItemId, attemptNumber)` уникален.

После нового старта процесса repository сначала выполняет recovery транзакцию:
незавершённая попытка старого владельца становится INTERRUPTED, её пункт —
NEEDS_DECISION, сессия — INTERRUPTED, старый runner slot освобождается. Уже сохранённые
терминальные результаты неизменны. Ничего автоматически не вычисляется.

Recovery не запускается при каждом открытии Activity: живой service нельзя принять
за погибший. В v0 достаточно ownerInstanceId процесса и восстановления один раз
при создании нового AppContainer, до допуска команд запуска. PAUSED сохраняется
как PAUSED, но не возобновляется автоматически.

Force-stop, перезагрузка и убийство ОС не гарантируют вызов finally/onDestroy.
Доказательства восстановления — сохранённые транзакции, не надежда на shutdown hook.
При невозможности узнать причину писать `PROCESS_DISAPPEARED / UNKNOWN`, а не OOM.

## 7. Хранение и транзакционные инварианты

Минимальные таблицы:

| Таблица | Основные данные |
|---|---|
| sessions | id, state, planJson, origin, timestamps, persistedActiveMs, lastSeq |
| queue_items | id, sessionId, ordinal, subjectId, scenarioId, state, repeatIndex |
| attempts | id, queueItemId, number, ownerInstanceId, stage, outcome, validity, reason, times |
| events | sessionId, seq, attemptId?, stage?, code, boundedPayloadJson, wallTimeMs, elapsedTimeMs, ownerInstanceId |
| telemetry | sessionId, attemptId?, name, value?, unit, scope, origin, unavailableReason?, wallTimeMs |
| runner_slot | singletonId=1, sessionId?, ownerInstanceId? |

Фактический результат, его origin, failure domain и validity хранятся в попытке,
а не вычисляются заново из локализованной строки UI. Внешние ключи, уникальные индексы
и Room schema export обязательны. Не использовать destructive migration.
Для новой БД версия 1; будущие изменения — явные миграции.

Операции repository:

`createSession(plan)`: проверить отсутствие другой нетерминальной сессии;
план и все queue_items создать в одной транзакции. При конфликте вернуть ссылку
на существующую сессию, не создавать вторую.
`claimSession(id, owner)`: проверить состояние/slot и занять slot атомарно.
`beginAttempt(itemId, owner)`: claim пункта, новая attempt, BEFORE_EXECUTION event
в одной транзакции; возврат только после завершения записи.
`recordStage(attemptId, stage, code)`: stage и event атомарно.
`finishAttempt(attemptId, result)`: guarded terminal update, event и queue item state
в одной транзакции. Late callbacks после terminal игнорируются с ограниченным
диагностическим событием, но не меняют итог.
`recoverInterruptedOwner(newOwner)`: описанное выше восстановление.
`readExportSnapshot(sessionId)`: согласованный DTO snapshot для неактивной сессии.

Перед опасным шагом await записи обязателен. Unbounded Channel, SharedFlow без
подписчика или logcat не являются надёжным журналом. При отказе хранилища не начинать
следующую попытку и не показывать «всё сохранено». Ошибка записывается настолько,
насколько позволяет носитель; последнее неперсистентное сообщение явно помечается.

Гарантия v0 — сохранение подтверждённых транзакций при обычном завершении процесса
в пределах гарантий SQLite/ОС. Абсолютную сохранность при потере питания/поломке
накопителя не обещать. Отдельно документировать выбранные journal/synchronous settings.
Не добавлять fsync на каждый токен: token stream в v0 отсутствует, а в реальном режиме
он требует отдельной политики измерительных накладных расходов.

## 8. Долгая работа и условия запуска

Service запускается только явным действием из видимого UI. `START_NOT_STICKY`;
нет автозапуска из BOOT_COMPLETED, periodic WorkManager и тайного перезапуска после Stop.
Foreground notification с открытием активной сессии и Stop. Notification permission
запрашивается в подходящем UI; отказ не трактуется как сбой модели.

Для исследовательского APK — service type `specialUse` с правдивым subtype description
«User-started local model test orchestration; synthetic runner in this development build».
Тип не гарантирует одобрение Google Play и не снимает ограничений платформы [A3].
Продвижение в foreground выполняется немедленно, тяжёлая инициализация — потом.
При ошибке запуска сервиса сессию не оставлять ложно RUNNING.

Не выполнять таймеры/очередь в Activity coroutine scope. WorkManager не использовать
как бесконечный runner: long-running workers имеют зависимость от job quotas,
в том числе изменения Android 16 [A4]. Foreground service — не бессмертие [A8].

Во время активной попытки допускается ограниченный PARTIAL_WAKE_LOCK с таймаутом
не больше оставшегося бюджета плюс 10 секунд; освобождать в finally. В paused/terminal
состояниях lock не удерживать. Не включать keep-screen-on и не просить автоматическое
исключение из battery optimization. Wake lock не даёт права обходить пользовательский Stop.

По умолчанию requireCharging=true. Если питание отключено: не запускать следующую
попытку; cooperative текущую отменить, validity=INVALID, причина POWER_CONSTRAINT,
сессия PAUSED. Возобновление только вручную после восстановления условия.

При thermal status SEVERE и выше: отменить текущую, validity=INVALID, PAUSED.
Новый старт разрешать при MODERATE или ниже, с ручным подтверждением; реальные soak
и политика cooldown — следующий этап. На API ниже 29 thermal status недоступен;
на прочих устройствах качество/доступность датчиков различаются [A9]. Unknown —
не значение ноль. Для v0 допускается synthetic-run с явным предупреждением;
допуск настоящего soak при отсутствии датчиков решает Codex отдельной политикой.

Проверять свободное место перед запуском/экспортом. Начальный резерв — 128 MiB,
проектная настройка, а не аппаратная гарантия. При невозможности записать результаты
остановиться. Сырые diagnostic payload ограничить 16 KiB на событие; при усечении
сохранять truncated=true. Telemetry не чаще одного семпла за 2 секунды.
Порог БД 64 MiB для v0: при достижении прекратить новые попытки, предложить экспорт
и ручную очистку. Проверку места повторять перед новой попыткой.

Для длительности использовать monotonic clock. Wall clock нужен для даты отчёта,
но не для таймаутов. Не вычитать elapsedRealtime из разных загрузок устройства;
обязательно хранить ownerInstanceId и доступную информацию о границах измерения.
После recovery известную накопленную длительность сохранять; неизвестный хвост
отмечать как unknown и консервативно списывать из бюджета до таймаута прерванной
попытки. Не получать новый полный бюджет просто перезапуском процесса.

Эффективный deadline попытки — минимум её case timeout и остатка активного
бюджета сессии. Причину deadline хранить отдельно: CASE_TIMEOUT или SESSION_BUDGET.
При исчерпании общего бюджета активная попытка CANCELLED/INVALID с причиной
SESSION_BUDGET_EXHAUSTED, оставшиеся пункты SKIPPED с той же причиной, сессия COMPLETED
с явным указанием неполного покрытия. Это не успешное прохождение пропущенных тестов.
Пауза не расходует активный бюджет; новая попытка после recovery не обнуляет его.
Продлить неизменяемый план нельзя: нужен новый запуск с новым планом.

`withTimeout` достаточен только для cooperative fake engine. Он не доказывает, что
можно остановить зависший JNI/GPU-вызов [A5]. Native isolation, аппаратный watchdog и
подтверждённое завершение процесса являются задачами следующего этапа.

## 9. Результаты, причины и недопустимые выводы

Разделять outcome, validity, origin и failure domain. Например:

```text
outcome: FAILED
validity: VALID
origin: SYNTHETIC
reasonCode: SIMULATED_ADAPTER_LOAD_FAILURE
failureDomain: ADAPTER
confidence: OBSERVED
```

Это наблюдённый результат синтетического сценария, не доказательство ошибки
реального адаптера. Simulation нельзя переименовать в DEVICE_MEASUREMENT в exporter. Origin фиксируется
в плане и попытке до запуска. Coordinator проверяет совпадение request.origin,
engine.origin и result.origin; при несовпадении — HARNESS/INVALID, остановка очереди,
не импорт результата как настоящего измерения.

Обязательные origins: SYNTHETIC, DEVICE_MEASUREMENT, IMPORTED_UNVERIFIED.
Показания памяти/температуры телефона во время demo — настоящая telemetry контроллера,
но не потребление ресурсов Qwen или другого inference runtime. В metric хранить scope
`lab_process` / `device`; не подписывать его `model_process` без реального измерения.

Не угадывать причину по одному слову exception. OOM kill, Java OutOfMemoryError,
Binder disconnect, GPU failure и некорректный контракт адаптера — разные наблюдения.
Нет доказательств — UNKNOWN. User cancel/thermal/storage/доступ к bridge не являются
основанием признать модель непригодной.

В v0 настоящий каталог никогда не получает отрицательных qualification verdicts.

Будущий scope вердикта должен включать: локальный device profile, OS/build, app и
adapter revision, runtime/version, artifact manifest hash, роль, requested и effective
backend, параметры контекста/threads/precision, test-suite revision, dataset hash,
acceptance-policy revision. Использовать стабильную canonical serialization, не
конкатенацию отображаемых имён и не случайный порядок Map.

В будущем скрывается сначала неподходящая КОНФИГУРАЦИЯ. Всю модель скрывать из
обычной выдачи лишь когда исключены все допустимые конфигурации выбранного профиля,
а непроверенных альтернатив не осталось. Повторяемость — необходимое, но не достаточное
условие: два одинаковых падения не доказывают, что виновата модель. Требуются корректные
условия, достаточное покрытие и обоснованная причина. Ручной override и «Показать
исключённые» сохраняются. Обновление scope делает старый verdict историческим.
Никакое скрытие не удаляет файлы.

## 10. Экспорт для человека и coding-agent

Реальный ZIP, не просто share текста:

```text
halo-model-lab_<sessionId>.zip
  manifest.json
  report.md
  results.json
  events.jsonl
  telemetry.csv
  failures/
    <attemptId>.json
  checksums.sha256
```

Manifest: schemaVersion=1, exportVersion=1, application/version/sourceRevision,
sessionId, sessionOrigin, createdAt, snapshotEventSeq, runtime/bridge status,
план сессии, provenance, omissions/truncation, privacy mode, files.
Для v0 bridgeStatus=NOT_IMPLEMENTED, inferenceRuntime=null.
Не выдумывать модельные hash и версии runtime.

`report.md`: заметное предупреждение о simulation; итог очереди; уникальные ошибки
со ссылками на attemptId; сохранённые стадии; что наблюдалось, что предположено,
чего не удалось установить; инструкции воспроизведения по demo scenario и параметрам.
Лимит summary 8 KiB UTF-8. Дополнительные подробности не терять: ссылки на JSON/events.
Группировать ошибки по стабильным reasonCode/stage/engine/scenario, не по времени.
Отдельно показывать число повторений. «Изменения относительно прошлого прогона» —
только при совпадении всех ключевых параметров; иначе «сопоставимого baseline нет».
Автоматическое сравнение baseline можно отложить в Codex, не выдумывать его в v0.

Лимит архива v0 — 32 MiB. Сохранять manifest, summary и results; ограничивать сырые
payload/telemetry с отражением omissions и диапазонов пропущенных событий. Если
обязательные данные не помещаются — явная ошибка экспорта, не тихое неполное сохранение.
Экспорт выполняется с IO dispatcher, не на главном потоке.

Snapshot читать короткой транзакцией, ZIP строить после её завершения. Не копировать
один `.db` из живой WAL-базы. `checksums.sha256` перечисляет все payload-файлы кроме
самого себя; manifest не содержит циклический hash от самого себя.

Имена entry генерировать из доверенных UUID/констант. Запретить произвольные `../`,
абсолютные пути, user filename как ZIP entry. CSV экранировать; потенциальные spreadsheet
формулы в текстовых полях нейтрализовать. Не экспортировать ключи, serial/IMEI,
Android ID, приватные пути Halo, личные диалоги или произвольные документы.

Архив сначала строится в private cache как `.part`, при успехе закрывается и становится
готовым ZIP. Делиться через неэкспортируемый FileProvider с временным read grant
только к `cache/exports/`; не открывать `root-path` или весь filesDir.
«Сохранить» использует системный create-document picker. Отказ picker не является
ошибкой тестов. Частичный результат записи внешнему provider помечать failed;
не обещать атомарность произвольного внешнего носителя.

## 11. Что специально оставляется Codex

Защищённый handshake/IPC, модельный каталог Halo, единый downloader, artifact leases,
запуск настоящих моделей в отдельном процессе, hard timeout, native crash diagnostics,
реальные тестовые наборы STT/TTS/LLM/embeddings/vision/intent, performance metrics,
автоматические scoped-verdicts, длительные hardware sessions, EN/LV и финальный дизайн.

Нельзя объявлять эти функции выполненными наличием интерфейса, пустого метода или
симулятора. `HALO_BRIDGE_NOT_IMPLEMENTED` — корректное состояние v0, не маскируемый баг.

## 12. Технические основания и проверенные источники

[A1] Android app-specific storage: приватные файлы одного приложения недоступны
другому напрямую. Один лишь одинаковый сертификат не объединяет файловые sandbox.
[A2] Custom permissions: signature/known signer и явное разрешение межприложенческого API.
[A3] Foreground service types: specialUse, декларация purpose и проверка при публикации.
[A4] Long-running WorkManager: зависимость от JobScheduler, квоты Android 16.
[A5] Kotlin withTimeout: cooperative cancellation, не принудительное завершение native-кода.
[A6] ApplicationExitInfo: причины смерти процесса; native tombstones с API 31, traces
могут отсутствовать. Это не гарантированный full-system logcat.
[A7] AGP built-in Kotlin: не смешивать несовместимые плагины.
[A8] Android Service: foreground lifecycle, ручной запуск/остановка, ограничения.
[A9] PowerManager: thermal status и wake locks; учитывать API levels.

```text
[A1] https://developer.android.com/training/data-storage/app-specific
[A2] https://developer.android.com/guide/topics/permissions/defining
[A3] https://developer.android.com/develop/background-work/services/fgs/service-types
[A4] https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
[A5] https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-timeout.html
[A6] https://developer.android.com/reference/android/app/ApplicationExitInfo
[A7] https://developer.android.com/build/migrate-to-built-in-kotlin
[A8] https://developer.android.com/reference/android/app/Service
[A9] https://developer.android.com/reference/android/os/PowerManager
```

Прочитанные исходные файлы Halo (blob SHA, не commit SHA):

```text
repository: slafftrosheen/Halo
app/build.gradle.kts
  blob: d747084809af6d36659a4be6f25577d480f6188e
gradle/libs.versions.toml
  blob: 67d670dd94b957dc48f3d608ac9f543906299ce4
gradle/wrapper/gradle-wrapper.properties
  blob: 7e7d24f6f29f0b86e10a92b8660106d628b9c3aa
```

В прочитанном app/build.gradle namespace Halo = `com.halo.app`, а applicationId =
`com.aistudio.braintool.xkqzps`. Не подменять одно другим при будущей интеграции;
Codex должен повторно проверить фактически установленный вариант APK.

Исходники существующих provider adapters и model store здесь не аудировались.
Никакие названия будущих bridge-компонентов ниже не утверждают их наличие в Halo.
