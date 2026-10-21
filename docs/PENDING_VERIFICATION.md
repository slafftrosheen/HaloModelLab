# Pending verification — HML-L0…L2

Ни одна сборка, тест или проверка на устройстве не выполнялась.
Ниже перечислено, что осталось непроверенным, чем это подтверждать и какие действия
запрещены при проверке.

## Уточнено без сборки (чтение официальных POM/Maven metadata)

- **Версия Compose compiler plugin согласована с AGP.** POM `com.android.tools.build:gradle:9.4.0`
  объявляет `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10`; Compose plugin тоже 2.2.10.
- **Артефакты существуют:** AGP 9.4.0, Room 2.8.5, KSP 2.3.6, lifecycle 2.11.0,
  activity-compose 1.10.1, navigation-compose 2.10.1, core-ktx 1.18.0,
  Compose BOM 2026.09.00 (material3 1.4.0, ui 1.12.1), androidx.test 1.7.0/1.3.0.
- **material3 1.4.0 не приносит `material-icons-core`** транзитивно, поэтому зависимость
  добавлена явно (BOM 1.7.8).

## Блокирующие для первой сборки

1. **Room + KSP.** `room-runtime/ktx 2.8.5` и Room compiler через KSP 2.3.6 подключены
   впервые в L1. Ожидаемые точки отказа: `@Query` с `IN (:list)`, подзапросы в
   `SessionSummaryRow`, `Flow<SessionDetail?>` через `combine`, `RoomDatabase.JournalMode`,
   генерация схемы. Исправлять запросы и типы, а не отключать schema export и не включать
   destructive migration.
2. **Экспорт схемы Room.** `app/schemas` содержит только README; JSON версии 1 появится
   при первой успешной сборке и должен быть закоммичен.
3. **compileSdk 37 / targetSdk 37.** Нужна установленная платформа API 37.
4. **JDK для сборки.** На машине создания пакета `java` = 1.8.0_503, `gradle` и `kotlinc`
   отсутствуют. Для AGP 9 нужен JDK 17+.
5. **Gradle wrapper.** Файлы из официального тега Gradle `v9.6.0` (SHA-256 в PROVENANCE),
   но wrapper не запускался.

## Требует подтверждения при разрешённой сборке

6. **AGP 9 DSL и KSP-блок.** `namespace`, `compileSdk`, `defaultConfig`, `buildTypes`,
   `buildFeatures`, `packaging`, `ksp { arg("room.schemaLocation", ...) }` не компилировались.
7. **Room-инварианты.** Уникальность `(sessionId, seq)` и `(queueItemId, number)`;
   compare-and-set терминального результата; блокировка второй сессии в транзакции;
   recovery не меняет завершённые попытки; освобождение runner slot на PAUSED/CANCELLED.
8. **Instrumentation-тесты.** `SessionRepositoryTest` (`runTest` + Room + `Dispatchers.IO`,
   `allowMainThreadQueries`) не компилировался. Запуск:
   `./gradlew :app:connectedDebugAndroidTest`.
9. **Source-тесты.** `QualificationGuardTest`, `SessionStateMachineTest`,
   `FakeBenchmarkEngineTest`, `SessionCoordinatorTest` не компилировались и не запускались.
   Запуск: `./gradlew :app:testDebugUnitTest`.
10. **Foreground service.** `ServiceCompat.startForeground` с типом `specialUse` (API 34+,
    0 ниже), `ServiceCompat.stopForeground`, manifest `<property>` subtype и
    `FOREGROUND_SERVICE_SPECIAL_USE` не проверялись. На API 34+ сервис обязан стартовать
    из видимого UI; при нарушении ожидается `ForegroundServiceStartNotAllowedException`.
11. **Уведомления.** Канал, `POST_NOTIFICATIONS` на API 33+, Stop `PendingIntent`
    с `FLAG_IMMUTABLE`, поведение при отказе в разрешении (уведомление подавляется,
    сессия продолжается) не проверялись на устройстве.
12. **Условия запуска.** `BatteryManager.BATTERY_PROPERTY_STATUS`,
    `PowerManager.currentThermalStatus` (API 29+), `StatFs` и `PARTIAL_WAKE_LOCK` с timeout
    не проверялись. v0 трактует `MODERATE` как блокирующий: ручного подтверждения для
    умеренного нагрева нет — это осознанное ограничение, а не поведение «как в SPEC».
13. **UI.** Навигация с аргументом, `FilterChip`, `LazyColumn`, степперы, запрос
    разрешения через `rememberLauncherForActivityResult`, `viewModelFactory` не
    компилировались. Проверить: поворот не создаёт сессию/попытку; повторный Start не
    запускает второй run; Stop во время `demo/timeout` даёт CANCELLED.
14. **Иконка launcher** не задана (системная заглушка). `windowLightNavigationBar` —
    атрибут API 27 при minSdk 26.
15. **Boundary.** В v0 UI, Room, service и fake engine — один процесс. Это не защита от
    native-падения будущего inference; отдельный процесс и supervisor — этап Codex.

## Не входит в текущий scope

16. ZIP-экспорт, FileProvider, share/save — HML-L3.
17. Настоящий inference, мост Halo, веса, IPC — следующий этап Codex.

## Что нельзя делать при проверке

- Не добавлять network/analytics SDK и разрешение `INTERNET`.
- Не подключать Halo как source dependency и не копировать его runtime.
- Не включать destructive migration «чтобы собралось».
- Не выдавать успешный `assembleDebug` за подтверждение реального inference.
- Не подменять отсутствующий thermal/battery-датчик нулём или «всё хорошо».
