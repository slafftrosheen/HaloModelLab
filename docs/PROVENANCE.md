# Provenance — Halo Model Lab, HML-L0

Дата: 21 октября 2026. Статус записи: источниковая основа, сборка не выполнялась.

## Что это за репозиторий

Отдельный Android-проект `HaloModelLab` (`applicationId`/`namespace`
`com.slafftrosheen.halomodellab`, launcher label `Halo Model Lab`). Один Gradle-модуль
`:app`. Проект не является модулем Halo и не подключает его исходники.

## Источник требований

`SPEC.md` (HML-SPEC-001, редакция 0.1) и `batches/L0.md` из пакета
`HaloModelLab_Agent_Pack` версии 0.1 — внешние документы, не входящие в этот репозиторий.
Domain-контракт в `app/src/main/java/.../domain/BenchContracts.kt` и guard в
`QualificationGuard.kt` перенесены из задания как есть по смыслу и сигнатурам.
Отклонений от согласованного контракта нет.

## Что скопировано, а что нет

- Исходники Halo не копировались, `includeBuild` и абсолютные пути к Halo отсутствуют.
- Inference runtime, provider adapters, model store и веса моделей не копировались
  и не скачивались.
- Реальные model IDs не используются: demo-идентификаторы имеют префикс `demo/`.
- Подписи, keystore, API-ключи и секреты не запрашивались и не добавлялись.
- Сетевые SDK, Analytics, crash-reporting и разрешение `INTERNET` отсутствуют.

## Снимок технологической базы

Версии зафиксированы по таблице задания (сверка Halo 21.10.2026): AGP 9.4.0,
Gradle wrapper 9.6.0, Compose compiler plugin 2.2.10, Compose BOM 2026.09.00,
minSdk 26 / compileSdk 37 / targetSdk 37, Java 17. Room 2.8.5, KSP 2.3.6 и Moshi 1.15.2
объявлены в `gradle/libs.versions.toml` для последующих пакетов, но в HML-L0 не подключены.

Согласованность этих версий не проверялась сборкой. Риски и способ проверки перечислены
в `docs/PENDING_VERIFICATION.md`.

## Gradle wrapper

Файлы wrapper взяты из официального репозитория Gradle, тег `v9.6.0`:

```text
https://raw.githubusercontent.com/gradle/gradle/v9.6.0/gradlew
https://raw.githubusercontent.com/gradle/gradle/v9.6.0/gradlew.bat
https://raw.githubusercontent.com/gradle/gradle/v9.6.0/gradle/wrapper/gradle-wrapper.jar
```

SHA-256 на момент добавления:

```text
497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7  gradle/wrapper/gradle-wrapper.jar
e01b5c97892572c82405c02b96a3382379100e7d825ce7c48883d95c26928750  gradlew
4e193538b4ddccbee0c05d6383731b647945c146d16d8fe243b4ff716867896f  gradlew.bat
```

Jar является настоящим zip-архивом wrapper, а не заглушкой. Wrapper не запускался:
`./gradlew` в этом пакете не выполнялся, дистрибутив Gradle 9.6.0 не скачивался.

## Лицензии зависимостей (декларативно, без сборки)

AndroidX/Compose — Apache 2.0, Kotlin и kotlinx.coroutines — Apache 2.0,
Gradle wrapper — Apache 2.0, JUnit 4 — EPL 1.0. Никакие файлы лицензий не изменялись
и не добавлялись в APK-ресурсы.

## Авторство и ограничение проверки

Файлы созданы coding-agent по заданию владельца. Ни один шаг сборки, запуска тестов,
эмулятора или проверки на телефоне не выполнялся, поэтому наличие исходников не является
доказательством работоспособности APK.
