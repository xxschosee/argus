# Задачи: Argus

Формат: описание → acceptance → verify → files. Задачи идут в порядке зависимостей.
Отметка `[x]` означает выполненную и проверенную задачу.

## Итерация 1 — фундамент

- [x] **Задача: Скелет Gradle-проекта под Paper API**
  - Acceptance: проект собирается, JAR появляется в `build/libs/`, `plugin.yml` с подставленной версией
  - Verify: `./gradlew build`, распаковать `plugin.yml` из JAR
  - Files: `settings.gradle.kts`, `build.gradle.kts`, `gradle/wrapper/*`, `gradlew`

- [x] **Задача: Чистое ядро — снимок метрик и пороги**
  - Acceptance: `MetricsSnapshot` и `Thresholds` без единого импорта `org.bukkit`; некорректные пороги падают с `IllegalArgumentException`
  - Verify: `grep -r "org.bukkit" src/main/java/dev/argus/core` → пусто; `ThresholdsTest`
  - Files: `core/MetricsSnapshot.java`, `core/Thresholds.java`, `core/Cause.java`, `core/Severity.java`

- [x] **Задача: Детектор диагнозов с edge-trigger**
  - Acceptance: 5 причин распознаются; тревога ровно одна на эпизод; при восстановлении приходит отдельное событие
  - Verify: `DiagnosisEngineTest` — 15 тестов, включая «одиночный всплеск игнорируется»
  - Files: `core/DiagnosisEngine.java`, `core/Diagnosis.java`, `test/.../DiagnosisEngineTest.java`

- [x] **Задача: Планировщик лечения**
  - Acceptance: план строится из диагноза; разрушительных действий в политике по умолчанию нет; чанковые действия получают цель, глобальные — нет
  - Verify: `HealEngineTest` — 9 тестов
  - Files: `core/HealEngine.java`, `core/HealKind.java`, `core/HealAction.java`, `test/.../HealEngineTest.java`

## Итерация 2 — мост к серверу

- [x] **Задача: Адаптер сбора метрик**
  - Acceptance: снимаются MSPT, TPS 1/5/15, heap, игроки, сущности, чанки
  - Verify: ручной прогон на сервере, сверка с `/tps` и `spark`
  - Files: `platform/MetricsCollector.java`, `platform/BukkitMetricsCollector.java`

- [x] **Задача: Инкрементальный поиск виновника**
  - Acceptance: за один вызов обрабатывается не больше заданного бюджета чанков; список пересобирается один раз за цикл; хранится top-N
  - Verify: `HotSpotScannerTest` — 11 тестов
  - Files: `core/HotSpotScanner.java`, `core/HotChunk.java`, `platform/BukkitChunkSource.java`

- [x] **Задача: Исполнитель лечебных действий**
  - Acceptance: `unload_chunk` выгружает чанк через `unloadChunkRequest`; `trim_memory` пишет цифры до/после; `remove_entities` пропускает игроков; неподдерживаемые действия честно логируются
  - Verify: ручной прогон: создать лаг-ферму, поймать алерт, убедиться, что чанк выгружен
  - Files: `platform/HealExecutor.java`, `platform/BukkitHealExecutor.java`

## Итерация 3 — доставка и управление

- [x] **Задача: Telegram-уведомления без зависимостей**
  - Acceptance: сообщение содержит причину, доказательства, имя сервера и список выполненных действий; HTML экранируется; при пустом токене плагин молчит и не падает
  - Verify: `MessageRendererTest` — 7 тестов; ручная отправка в чат
  - Files: `notify/Notifier.java`, `notify/TelegramClient.java`, `notify/TelegramNotifier.java`, `notify/MessageRenderer.java`

- [x] **Задача: Журнал инцидентов и команда `/argus`**
  - Acceptance: `/argus status|history|scan|reload`; журнал ограничен по размеру; `reload` применяет новые пороги
  - Verify: `IncidentLogTest` — 5 тестов; ручной прогон команд
  - Files: `core/IncidentLog.java`, `command/ArgusCommand.java`

- [x] **Задача: Главный класс и конфиг**
  - Acceptance: плагин стартует, логирует пороги, снимает метрики по расписанию, корректно выключается; при некорректных порогах отключается сам, а не падает
  - Verify: ручной прогон на сервере
  - Files: `ArgusPlugin.java`, `resources/plugin.yml`, `resources/config.yml`

## Итерация 4 — качество и публикация

- [x] **Задача: Валидатор вёрстки плагина**
  - Acceptance: скрипт проверяет обязательные ключи `plugin.yml`, существование главного класса, отсутствие `Bukkit.reload()`, согласованность команд
  - Verify: `./scripts/validate-plugin-layout.sh`
  - Files: `scripts/validate-plugin-layout.sh`

- [x] **Задача: Спецификация, карта модулей, план**
  - Acceptance: `SPEC.md` описывает цель, команды, структуру, стиль, тестирование, границы и критерии готовности; `CAPABILITY-MAP.md` задаёт модули и порядок сборки
  - Verify: ревью владельца
  - Files: `SPEC.md`, `CAPABILITY-MAP.md`, `tasks/plan.md`, `tasks/todo.md`

- [x] **Задача: README, лицензия, contributing, changelog**
  - Acceptance: README объясняет, что делает плагин и зачем; лицензия MIT; есть правила для контрибьюторов
  - Verify: ревью
  - Files: `README.md`, `LICENSE`, `CONTRIBUTING.md`, `CHANGELOG.md`

- [x] **Задача: Проверка на живом Paper-сервере**
  - Acceptance: плагин грузится без ошибок; `/argus status` показывает реальные цифры; искусственная лаг-ферма вызывает алерт и лечение; в логе нет исключений
  - Verify: `scripts/smoke-test.sh` — 9 проверок, все зелёные. Сервер Paper 1.21.11 локально, нагрузка через `/forceload` + 45 свиней
  - Files: `scripts/smoke-test.sh`, `tools/rcon.py`, `tools/loggrep.py`

- [ ] **Задача: CI на сборку и тесты**
  - Acceptance: GitHub Actions собирает JAR и гоняет тесты на каждом PR; артефакт доступен для скачивания
  - Verify: зелёный статус на первом PR
  - Files: `.github/workflows/build.yml`

- [ ] **Задача: Страница Modrinth + GitHub-репозиторий**
  - Acceptance: репозиторий создан, README с GIF-превью, страница Modrinth с описанием, галереей и категориями `performance` / `utility` / `moderation`
  - Verify: плагин скачивается с Modrinth и ставится на сервер
  - Files: `README.md`, скриншоты/гифки

- [ ] **Задача: Интеграция с Pterodactyl (модуль `panel`)**
  - Acceptance: по кнопке или по команде плагин перезапускает зависший сервер через API панели; без настроенного ключа модуль молчит
  - Verify: ручной прогон: убить сервер, убедиться, что он поднялся
  - Files: `panel/*`, `platform/PanelHealExecutor.java`

- [ ] **Задача: Кнопки в Telegram (callback)**
  - Acceptance: в сообщении есть кнопки «Выгрузить чанк» / «Перезапустить» / «Игнорировать»; нажатие выполняет действие; используется отдельный токен бота
  - Verify: ручной прогон нажатий
  - Files: `notify/TelegramClient.java` (+ `getUpdates`), `notify/CallbackHandler.java`

## Из бэклога (решено)

- [x] `Bukkit.setAutoSave` отсутствует в Paper API → используем `World#setAutoSave`
- [x] Валидация порогов кучи была перевёрнута → исправлено, тест ловит регрессию
- [x] `Chunk#setMaxEntities` отсутствует → действие помечено неподдерживаемым
