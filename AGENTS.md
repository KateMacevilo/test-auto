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
├── Tests.java                                    # health check + позитив createPaymentConsent
├── prior_ob_svc_api_listpassportsconsent/
│   └── CreatePaymentConsentTest.java             # create_consent_* сценарии (идемпотентность)
├── common/
│   ├── AbstractApiTest.java                      # базовый класс: Spring wiring, общие хелперы
│   ├── config/TestConfig.java                    # Spring-конфигурация тестов
│   ├── request/BaseRequest.java                  # RestAssured-обёртка: auth, jwt, sendRequest
│   ├── request/TemplateRequest.java              # GET /actuator/health, POST /api/paymentConsents/listPassports
│   ├── model/ (TestData, Input, Expected)        # модель тест-кейса
│   ├── dataprovider/DataProviders.java           # MainDP: читает testdata/<имя метода>.json
│   ├── assertions/Assertions.java                # verifyStatusCode / verifyResponseSchema / verifyResponseParam
│   ├── db/DbClient.java                          # JDBC-доступ к БД сервиса (проверки + очистка)
│   ├── utils/JwtAssertionUtil.java               # сборка x-jwt-assertion
│   └── listener/                                 # ResultReporter, LifecycleListener, AllureTestCaseListener
src/test/resources/
├── application.properties                        # URL сервиса, БД, auth, jwt (ЗАПОЛНИТЬ)
├── testng.xml                                    # suite
├── schemas/                                      # JSON-схемы ответов (draft-04)
└── testdata/                                     # JSON-файлы с данными кейсов (по одному на тест-метод)
```

## Рецепт добавления нового тест-кейса

1. Создать `src/test/resources/testdata/<имя метода>.json` с полями `id` (ID кейса в Allure), `name`, `input`, `expected`.
2. Добавить `@Test(dataProvider = "MainDP", dataProviderClass = DataProviders.class)`-метод с тем же именем в класс соответствующей операции.
3. Allure-метаданные (имя, `ALLURE_ID`, описание) проставляет `AllureTestCaseListener` автоматически из `TestData`.
4. Зарегистрировать класс в `testng.xml`.

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

## Реализованные тест-кейсы (CreatePaymentConsentTest)

- `create_consent_idempotent_body_mismatch_conflict_409` — повтор с тем же ключом и другим телом → 409 Conflict, `BY.PRIORBANK.Rules.IllegalAttemptOfCreation`.
- `create_consent_idempotent_same_body_returns_201` — повтор с тем же ключом и совпадающим телом → 201, тот же `listPassportsConsentId`, дубль в БД не создаётся.
- `create_consent_negative_accept_not_acceptable` — `Accept: application/xml` → 406, записей в БД нет.
- `create_consent_negative_amount_boundaries` — параметризован (массив в JSON): amount=0, amount=-1 → 400, `BY.NBRB.Field.Invalid`, `path=data.initiation.amount`.
- `create_consent_negative_amount_more_than_2_decimals` — amount=100.123 → 400.

Идемпотентные кейсы в конце удаляют созданные записи из БД (`DbClient.deleteAllDataConsentById`); негативные проверяют, что записей не появилось.
