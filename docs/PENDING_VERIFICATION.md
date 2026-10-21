# Pending verification — HML-L0

Ни одна сборка, тест или проверка на устройстве в этом пакете не выполнялась.
Ниже перечислено, что именно осталось непроверенным, чем это подтверждать и какие
действия запрещены при проверке.

## Блокирующие для первой сборки

1. **AGP 9.4.0 + Compose compiler plugin 2.2.10.**
   AGP 9 включает встроенную поддержку Kotlin, и плагин `org.jetbrains.kotlin.android`
   здесь намеренно не подключён. Compose compiler plugin версионируется вместе с Kotlin,
   поэтому его версия должна совпасть с Kotlin, встроенным в AGP 9.4.0.
   Проверка: первая разрешённая сборка. Если возникнет несовпадение версий — зафиксировать
   фактические версии и выбрать согласованную пару. **Не добавлять** старый
   `org.jetbrains.kotlin.android` рядом со встроенным Kotlin и не отключать
   `android.builtInKotlin` как «быстрый обход».
2. **compileSdk 37 / targetSdk 37.** Требуется установленная платформа API 37 и
   поддерживающий её AGP. Compose BOM 2026.09.00 (material3 1.4.0, ui 1.12.1) по
   документации требует compileSdk 37 и AGP 9. Наличие платформы в локальном SDK
   не проверялось.
3. **JDK для сборки.** На машине, где создавался пакет, `java -version` показал
   1.8.0_503, а `gradle` отсутствует в PATH. Для AGP 9 нужен JDK 17+. Это наблюдение
   о среде, а не вывод о несовместимости проекта.
4. **Gradle wrapper.** Файлы wrapper взяты из официального тега Gradle `v9.6.0`
   (SHA-256 в `docs/PROVENANCE.md`), но `./gradlew` не запускался: дистрибутив
   `gradle-9.6.0-bin.zip` не скачивался, `validateDistributionUrl` не проверялся.
   Отсутствие сети при первой сборке — блокер, а не ошибка кода.

## Требует подтверждения при разрешённой сборке

5. **AGP 9 DSL.** Использованы `android { namespace, compileSdk, defaultConfig,
   buildTypes, compileOptions, buildFeatures, packaging }`. Для AGP 9 заявлен новый DSL;
   если имена блоков отличаются, исправить конфигурацию, не меняя структуру модуля.
6. **Compose-зависимости.** `material3` 1.4.0 не приносит `material-icons-core`
   транзитивно (проверено чтением POM), поэтому добавлена явная зависимость
   `androidx.compose.material:material-icons-core` из BOM (1.7.8). Версия заморожена;
   при конфликте метаданных с ui 1.12.1 заменить иконки на локальные векторы,
   а не поднимать версию вручную.
7. **Navigation Compose 2.10.1.** Использованы `NavHost`, `composable`,
   `currentBackStackEntryAsState`, `findStartDestination` и строковые маршруты.
   Совместимость API не проверялась компилятором.
8. **Lifecycle 2.11.0.** `viewModelFactory`/`initializer` и
   `collectAsStateWithLifecycle` использованы по текущему API; версия не компилировалась.
9. **Тема.** `android:windowLightNavigationBar` — атрибут API 27 при minSdk 26;
   на API 26 он игнорируется, возможен lint-warning. Это не функциональный дефект.
10. **Иконка launcher.** `android:icon` не задан, используется системная заглушка.
    Это осознанно: брендинг Halo вне scope.
11. **Исходники тестов.** `QualificationGuardTest` не компилировался и не запускался.
    Запуск: `./gradlew :app:testDebugUnitTest` — только на разрешённом verification-pass.

## Не входит в HML-L0 и потому не проверялось

12. Room 2.8.5, KSP 2.3.6 и Moshi 1.15.2 объявлены в каталоге версий, но не подключены:
    их совместимость проверяется в HML-L1 и HML-L3.
13. `app/schemas` и экспорт схемы Room появятся в HML-L1.
14. FileProvider, foreground service, `POST_NOTIFICATIONS`, wake lock и thermal-политика
    отсутствуют до HML-L2/HML-L3 и в manifest не заявлены.

## Что нельзя делать при проверке

- Не добавлять network/analytics SDK и разрешение `INTERNET` ради «удобной» диагностики.
- Не подключать Halo как source dependency и не копировать его runtime.
- Не выдавать успешный `assembleDebug` за подтверждение реального inference:
  в v0 настоящих моделей нет.
