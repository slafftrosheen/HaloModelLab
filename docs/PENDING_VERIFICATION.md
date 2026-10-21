# Pending verification — HML-L0…L1

Ни одна сборка, тест или проверка на устройстве не выполнялась.
Ниже перечислено, что осталось непроверенным, чем это подтверждать и какие действия
запрещены при проверке.

## Уточнено без сборки (чтение официальных POM/Maven metadata)

- **Версия Compose compiler plugin согласована с AGP.** POM `com.android.tools.build:gradle:9.4.0`
  объявляет зависимость `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10`. Поэтому связка
  встроенного Kotlin (2.2.10) и `org.jetbrains.kotlin.plugin.compose:2.2.10` согласована
  по метаданным. Это не замена реальной сборке.
- **Артефакты существуют:** AGP 9.4.0, Room 2.8.5, KSP 2.3.6, lifecycle 2.11.0,
  activity-compose 1.10.1, navigation-compose 2.10.1, Compose BOM 2026.09.00
  (material3 1.4.0, ui 1.12.1), androidx.test core 1.7.0 / ext:junit 1.3.0 / runner 1.7.0.
- **material3 1.4.0 не приносит `material-icons-core`** транзитивно (проверено POM),
  поэтому `androidx.compose.material:material-icons-core` добавлен явно из BOM (1.7.8).
  Версия заморожена; при конфликте метаданных заменить иконки локальными векторами.

## Блокирующие для первой сборки

1. **Room + KSP.** `room-runtime/ktx 2.8.5` и `room-compiler` через KSP 2.3.6 подключены
   впервые в L1. Совместимость KSP2 с Kotlin 2.2.10 и корректность генерации DAO не
   проверены. Ожидаемые точки отказа: `@Query` с `IN (:list)`, подзапросы в
   `SessionSummaryRow`, `Flow<SessionDetail?>` через `combine`, `RoomDatabase.JournalMode`.
   При ошибке исправлять запросы и типы, а не отключать schema export и не включать
   destructive migration.
2. **Экспорт схемы Room.** `app/schemas` содержит только README: JSON версии 1 появится
   при первой успешной сборке и должен быть закоммичен. Отсутствие JSON сейчас —
   ожидаемое состояние, а не доказательство корректной схемы.
3. **compileSdk 37 / targetSdk 37.** Требуется установленная платформа API 37. Compose BOM
   2026.09.00 требует compileSdk 37 и AGP 9.
4. **JDK для сборки.** На машине, где создавался пакет, `java -version` показал 1.8.0_503;
   `gradle` и `kotlinc` отсутствуют в PATH. Для AGP 9 нужен JDK 17+.
5. **Gradle wrapper.** Файлы из официального тега Gradle `v9.6.0` (SHA-256 в
   `docs/PROVENANCE.md`), но `./gradlew` не запускался и дистрибутив не скачивался.

## Требует подтверждения при разрешённой сборке

6. **AGP 9 DSL.** `namespace`, `compileSdk`, `defaultConfig`, `buildTypes`,
   `compileOptions`, `buildFeatures`, `packaging`, блок `ksp { arg(...) }` — синтаксис
   не компилировался. При расхождении исправить конфигурацию, не меняя структуру модуля.
7. **Room-запросы и инварианты.** Проверить: уникальность `(sessionId, seq)` и
   `(queueItemId, number)`; compare-and-set терминального результата; атомарность
   `createSession` (вторая сессия блокируется в транзакции); recovery не меняет
   завершённые попытки; очистку runner slot при PAUSED/CANCELLED.
8. **Instrumentation-тесты.** `SessionRepositoryTest` использует `runTest` вместе с
   Room/`Dispatchers.IO` и `allowMainThreadQueries`; компиляция и поведение не проверены.
   Запуск: `./gradlew :app:connectedDebugAndroidTest` только на verification-pass.
9. **Source-тесты.** `SessionStateMachineTest` не компилировался и не запускался.
   Запуск: `./gradlew :app:testDebugUnitTest`.
10. **UI.** Навигация с аргументом `sessionId`, `FilterChip`, `LazyColumn`, степперы и
    `viewModelFactory` не компилировались. Проверить также поворот экрана: пересоздание
    Activity не должно вызывать `createSession` или `claimSession`.
11. **Иконка launcher.** `android:icon` не задан, используется системная заглушка.
12. **Тема.** `android:windowLightNavigationBar` — атрибут API 27 при minSdk 26;
    на API 26 игнорируется, возможен lint-warning.

## Не входит в текущий scope и потому не проверялось

13. Foreground service, `POST_NOTIFICATIONS`, wake lock, thermal/charging политика — HML-L2.
14. ZIP-экспорт, FileProvider, share/save — HML-L3.
15. Настоящий inference, мост Halo, веса, IPC — следующий этап Codex.

## Что нельзя делать при проверке

- Не добавлять network/analytics SDK и разрешение `INTERNET` ради «удобной» диагностики.
- Не подключать Halo как source dependency и не копировать его runtime.
- Не включать destructive migration или `fallbackToDestructiveMigration` «чтобы собралось».
- Не выдавать успешный `assembleDebug` за подтверждение реального inference:
  в v0 настоящих моделей нет.
