# AGENTS.md

## Обзор проекта

Каталог `autotests` — набор API-автотестов для сервиса **prior-ob-svc-api-listpassportsconsent** (создание платёжных согласий listPassports, Spring Boot 3, стек: TestNG + RestAssured + Allure + Spring Test + JDBC/PostgreSQL).

Тесты ходят в реально запущенный сервис по HTTP и для ряда сценариев проверяют/чистят реальную БД сервиса. Восстановлен по скриншотам из реального проекта (каталог `skreens/`), поэтому часть конфигурации и имён — допущения (см. раздел «Допущения» ниже).

## Сборка и запуск

```bash
# компиляция тестов
mvn clean test-compile

# запуск всех тестов (suite: src/test/resources/testng.xml)
mvn test

# запуск одного класса
mvn test -Dtest=CreatePaymentConsentTest

# отчёт Allure (результаты в target/allure-results)
```

Перед запуском нужно заполнить `src/test/resources/application.properties`: URL сервиса, подключение к БД, токен/эндпоинт аутентификации, claims JWT.

## Структура

```
src/test/java/tests/
├── Tests.java                                    # getHealth + универсальный прогон простых кейсов
├── prior_ob_svc_api_listpassportsconsent/
│   └── CreatePaymentConsentTest.java             # сценарии с состоянием (идемпотентность)
├── common/
│   ├── AbstractApiTest.java                      # базовый класс: Spring wiring, общие хелперы
│   ├── config/TestConfig.java                    # Spring-конфигурация тестов
│   ├── request/BaseRequest.java                  # RestAssured-обёртка: auth, jwt, sendRequest
│   ├── request/TemplateRequest.java              # GET /actuator/health, POST /api/paymentConsents/listPassports
│   ├── model/ (TestData, Input, Expected)        # модель тест-кейса
│   ├── dataprovider/DataProviders.java           # MainDP (файл по имени метода) и AllFilesDP (все файлы из testdata/api/)
│   ├── assertions/Assertions.java                # verifyStatusCode / verifyResponseSchema / verifyResponseParam
│   ├── db/DbClient.java                          # JDBC-доступ к БД сервиса (проверки + очистка)
│   ├── utils/JwtAssertionUtil.java               # сборка x-jwt-assertion
│   └── listener/                                 # ResultReporter, LifecycleListener, AllureTestCaseListener
src/test/resources/
├── application.properties                        # URL сервиса, БД, auth, jwt (ЗАПОЛНИТЬ)
├── testng.xml                                    # suite
├── schemas/                                      # JSON-схемы ответов (draft-04)
└── testdata/
    ├── getHealth.json                            # данные для getHealth (MainDP: файл по имени метода)
    ├── create_consent_idempotent_*.json          # данные для сценариев с состоянием (MainDP)
    └── api/                                      # простые кейсы для универсального метода Tests.createConsent (AllFilesDP)
```

## Два режима прогона кейсов

1. **Универсальный раннер** — `Tests.createConsent` + `AllFilesDP`: читает ВСЕ json-файлы из `testdata/api/`
   (каждый файл — один кейс или массив параметризаций) и прогоняет их одним методом. Логика общая:
   POST → проверка статуса → (опционально) схема → параметры → проверка БД
   (для 2xx созданные записи удаляются, для 4xx/5xx — проверка «записей не создано»).
   Флаг `expected.verifySchema: true` включает проверку JSON-схемы.
   Подходит для большинства простых позитивных/негативных кейсов — добавление кейса = новый файл в `testdata/api/`, код не трогается.
2. **Сценарии с состоянием** — отдельные методы в `CreatePaymentConsentTest` + `MainDP` (файл `testdata/<имя метода>.json`):
   идемпотентность (409 при несовпадении тела, 201 при совпадении) — несколько зависимых запросов
   с проверками БД между ними, универсальной логикой не выражаются.

## Допущения (помечены в коде маркером `AGENTS.md:`)

Требуют подтверждения по реальному проекту; при расхождении править в указанных местах:

1. **БД** — предположен PostgreSQL (`db.url/db.user/db.password` в `application.properties`).
2. **Имена таблиц/колонок** в `DbClient` — snake_case от имён JPA-сущностей сервиса (`idempotency_key`, `list_passports_payment_consent`, `list_passports_payment_consent_event`, `list_passports_payment_consent_signed`, `multi_authorisation`). Имена вынесены в константы вверху класса.
3. **Аутентификация** — `BaseRequest.setAuth`: статический токен (`auth.static-token`) или client_credentials на `auth.url`; реальный механизм в закрытом контуре может отличаться.
4. **x-jwt-assertion** — собирается как unsigned JWT с claims из скриншота сервиса; реальные claim-ы/подпись могут отличаться (значения в `application.properties`).
5. **JSON-схема ответа** (`listPassportsConsent-schema.json`) восстановлена со скриншотов; статус `AwaitingFurtherAuthorisation` прочитан частично закрытым пунктом enum.
6. **Тело запроса** в testdata дополнено до полной схемы — часть полей на скриншоте была обрезана.
7. ID `24188` в `create_consent_idempotent_body_mismatch_conflict_409.json` взят со скриншота Allure как пример; у остальных кейсов `id` пока не заполнен (листенер его просто пропускает).
8. **Граничные значения amount** в `create_consent_negative_amount_boundaries.json` (0 и -1) — допущение: в описании кейса значения заданы плейсхолдером `{0} ({1})`; прислать точные значения — поправить в JSON.
9. **Переопределение заголовка Accept** в негативном кейсе 406 работает за счёт того, что `input.headers` применяются в `TemplateRequest` после дефолтной спеки `BaseRequest` (последнее значение заголовка выигрывает).
10. CI-обвязка реального проекта (TestRunner/PodSupport/GitLabSupport/ReportSupport — запуск пода, заливка отчёта, уведомления) в этот проект не переносилась.

## Реализованные тест-кейсы

Универсальный раннер `Tests.createConsent` (файлы в `testdata/api/`):

- позитив: полный валидный запрос → 201, проверка схемы и параметров, очистка БД;
- `create_consent_negative_accept_not_acceptable` — `Accept: application/xml` → 406, записей в БД нет;
- `create_consent_negative_amount_boundaries` — массив: amount=0, amount=-1 → 400, `BY.NBRB.Field.Invalid`, `path=data.initiation.amount`;
- `create_consent_negative_amount_more_than_2_decimals` — amount=100.123 → 400.

Сценарии с состоянием (`CreatePaymentConsentTest`, файлы в `testdata/`):

- `create_consent_idempotent_body_mismatch_conflict_409` — повтор с тем же ключом и другим телом → 409 Conflict, `BY.PRIORBANK.Rules.IllegalAttemptOfCreation`.
- `create_consent_idempotent_same_body_returns_201` — повтор с тем же ключом и совпадающим телом → 201, тот же `listPassportsConsentId`, дубль в БД не создаётся.

Идемпотентные кейсы в конце удаляют созданные записи из БД (`DbClient.deleteAllDataConsentById`); негативные проверяют, что записей не появилось.
