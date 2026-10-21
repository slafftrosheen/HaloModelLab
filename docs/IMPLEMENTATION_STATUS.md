# Implementation status — Halo Model Lab

Последнее обновление: 21 октября 2026. Пакет: **HML-L0**.
Базовый commit: отсутствует (репозиторий инициализирован этим пакетом, `main`).

## Итог

Исходники самостоятельного Android-приложения, четыре связанных экрана, честные empty
states, demo-граница с четырьмя фикстурами и полный набор domain-типов.

Приложение **не собрано**, **не протестировано** и **не проверено на устройстве**.
Настоящего inference, моста Halo, Room, очереди, сервиса и экспорта в этом пакете нет.

```text
Build: NOT_RUN
Automated tests: NOT_RUN
Device tests: NOT_RUN
```

## Матрица проверки

| Область | Исходники | Тестовые исходники | Сборка | Тесты | Устройство |
|---|---|---|---|---|---|
| Domain-контракт (`BenchContracts.kt`) | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |
| Qualification guard | есть | `QualificationGuardTest.kt` | NOT_RUN | NOT_RUN | NOT_RUN |
| Каталог: режимы REAL_UNAVAILABLE / DEMO | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |
| Demo-сценарии и поиск | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |
| Навигация и четыре экрана | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |
| Сессия: выбор в памяти, disabled start | есть | — | NOT_RUN | NOT_RUN | NOT_RUN |
| История из Room | отсутствует (L1) | — | NOT_RUN | NOT_RUN | NOT_RUN |
| Очередь, service, coordinator | отсутствует (L2) | — | NOT_RUN | NOT_RUN | NOT_RUN |
| ZIP-экспорт | отсутствует (L3) | — | NOT_RUN | NOT_RUN | NOT_RUN |

## Чек-лист HML-L0

- [x] Рабочий каталог проверен: собственного git-репозитория у `HaloTestApk` нет
      (git находил `C:/Users/slavd` с повреждённым HEAD), поэтому создан отдельный
      репозиторий `HaloModelLab` в `main`. Изменения владельца не затрагивались.
- [x] Gradle-конфигурация: один модуль `:app`, AGP 9.4.0 без `kotlin-android`,
      Compose plugin 2.2.10, Compose BOM 2026.09.00, minSdk 26 / compileSdk 37 /
      targetSdk 37, Java 17. Все версии зафиксированы в `gradle/libs.versions.toml`.
- [x] `.gitignore` исключает build, IDE и signing-файлы.
- [x] Настоящий wrapper: `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`
      из официального тега Gradle `v9.6.0`; SHA-256 в `docs/PROVENANCE.md`.
- [x] `T/domain/QualificationGuardTest.kt`: synthetic PASSED/FAILED, imported evidence,
      CANCELLED/INTERRUPTED/SKIPPED, INVALID и UNKNOWN validity отклоняются;
      валидные device-измерения проходят только guard.
- [x] Контракт и guard без Android-зависимостей.
- [x] Четыре экрана и навигация: каталог, новая сессия, история, настройки.
- [x] Каталог настоящих моделей — честный empty state `BRIDGE_NOT_IMPLEMENTED`.
- [x] Явный переключатель «Симуляция инфраструктуры» и четыре scenario-карточки
      (`demo/success`, `demo/load-failure`, `demo/empty-output`, `demo/timeout`)
      с меткой «СИМУЛЯЦИЯ — не результат проверки AI-модели» на каждой.
- [x] Кнопка запуска disabled с текстом «Исполнитель очереди ещё не подключён».
- [x] Границы, зависимости и NOT_RUN зафиксированы в `docs/`.
- [x] Source review и `git diff --check`; локальный commit в `main`.
- [x] Остановка после L0: HML-L1 не начат.

## Что реально связано в исходниках

1. `MainActivity` → `LabTheme` → `LabApp` → `NavHost` с маршрутами
   `catalog`/`session`/`history`/`settings`; нижняя навигация сохраняет состояние вкладок.
2. `CatalogScreen` → `CatalogViewModel` → `CatalogRepository` (`StateFlow`):
   переключатель режима, поиск, выбор сценариев, сброс выбора.
3. Смена режима меняет содержимое: REAL — empty state и недоступная загрузка,
   DEMO — баннер симуляции, поиск и карточки.
4. `SessionScreen` → `SessionViewModel` → тот же `CatalogRepository`: показывает
   выбранные фикстуры в порядке выбора, профиль `DEMO_INFRASTRUCTURE_V1`, очистку выбора.
5. «Перейти к сессии» / «Открыть каталог» переключают вкладки; кнопка запуска
   постоянно disabled.

## Что недоступно по замыслу

- Настоящие модели, каталог Halo, мост, веса, inference, backend CPU/GPU — отсутствуют.
- Room, план сессии, очередь, попытки, события, recovery — HML-L1.
- Foreground service, coordinator, fake engine, условия запуска — HML-L2.
- ZIP-экспорт, FileProvider, отчёт — HML-L3.
- История и настройки — статические экраны с объяснением, без фиктивных данных.
- Выбор сценариев живёт только в памяти процесса и теряется при его смерти.

## Фактически выполненные статические проверки

Выполнялись только чтение, разбор XML, поиск по исходникам и git-операции.

1. `python -c "xml.dom.minidom.parse(...)"` для `AndroidManifest.xml`,
   `res/values/strings.xml`, `res/values/colors.xml`, `res/values/themes.xml` — 4/4
   разобраны без ошибок.
2. Сверка ссылок на строки: 70 использованных `R.string.*` в Kotlin — все определены;
   `app_name` используется из manifest.
3. Поиск запрещённых элементов: нет `uses-permission`, `INTERNET`, сетевых SDK,
   Analytics, Firebase, OpenAI/OpenRouter; нет `includeBuild`, `../Halo`,
   `com.halo.app`, `aistudio`.
4. Поиск динамических версий (`+`, SNAPSHOT) в build-файлах — не найдено.
5. Чтение POM: Compose BOM 2026.09.00 → material3 1.4.0, ui 1.12.1,
   material-icons-core 1.7.8; material3 1.4.0 не приносит icons транзитивно,
   поэтому зависимость добавлена явно.
6. `git diff --check` — без пробельных ошибок (см. отчёт пакета).
7. Построчный source review всех созданных Kotlin/XML/Gradle-файлов.
8. `java -version` → 1.8.0_503, `gradle` в PATH отсутствует, `kotlinc` отсутствует:
   среда не готова к сборке AGP 9 без установки JDK 17+ (см. PENDING_VERIFICATION).

Сборка, unit-тесты, instrumentation-тесты, эмулятор, ADB и проверки на телефоне
не запускались. Логи сборки и тестов отсутствуют, поэтому фразы «собирается»,
«тесты проходят» и «готово к benchmark» неприменимы.

## Следующий разрешённый пакет

HML-L1 — планы сессий, Room, восстановление после потери процесса.
Начинать только по отдельной инструкции владельца.
