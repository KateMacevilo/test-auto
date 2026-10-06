# AGENTS.md

## Обзор проекта

Каталог `autotests` — набор API-автотестов для сервиса **api-listpassportsconsent** (создание платёжных согласий listPassports, Spring Boot 3, стек: TestNG + RestAssured + Allure + Spring Test + JDBC/PostgreSQL).

Тесты ходят в реально запущенный сервис по HTTP и для ряда сценариев проверяют/чистят реальную БД сервиса. Восстановлен по скриншотам из реального проекта (каталог `skreens/`), поэтому часть конфигурации и имён — допущения (см. раздел «Допущения» ниже).

## Сборка и запуск

```bash
# компиляция тестов
mvn clean test-compile

# запуск всех тестов (suite: src/test/resources/testng.xml)
mvn test

# запуск одного класса / метода
mvn test -Dtest=Tests
mvn test -Dtest=Tests#createConsent

# отчёт Allure (результаты в target/allure-results)
```

Перед запуском нужно заполнить `src/test/resources/application.properties`: URL сервиса, подключение к БД, токен/эндпоинт аутентификации, claims JWT.

## Структура

```
src/test/java/tests/
├── Tests.java                                    # ЕДИНСТВЕННЫЙ тестовый класс: getHealth + createConsent + сценарии с состоянием
├── common/
│   ├── AbstractApiTest.java                      # базовый класс: Spring wiring, общие хелперы
│   ├── config/TestConfig.java                    # Spring-конфигурация тестов
│   ├── request/BaseRequest.java                  # RestAssured-обёртка: auth, jwt, sendRequest
│   ├── request/TemplateRequest.java              # GET /actuator/health, POST /api/paymentConsents/listPassports
│   ├── model/ (TestData, Input, Expected, DbState)  # модель тест-кейса
│   ├── dataprovider/                             # DataProviders (MainDP / FileDP / AllFilesDP), JSONReader, DateFormatter
│   ├── assertions/Assertions.java                # verifyStatusCode / verifyResponseSchema / verifyResponseParam
│   ├── db/DbClient.java                          # JDBC-доступ к БД сервиса (проверки + очистка)
│   ├── utils/JwtAssertionUtil.java               # сборка x-jwt-assertion
│   └── listener/                                 # ResultReporter, LifecycleListener, AllureTestCaseListener
src/test/resources/
├── application.properties                        # URL сервиса, БД, auth, jwt (ЗАПОЛНИТЬ)
├── testng.xml                                    # suite
├── schemas/                                      # JSON-схемы ответов (draft-04)
├── getHealth                                     # данные getHealth (FileDP: имя = имя метода, без расширения)
├── create_consent_idempotent_body_mismatch_conflict_409   # данные сценария 409 (FileDP)
├── create_consent_idempotent_same_body_returns_201        # данные сценария same-body (FileDP)
└── testdata/api/                                 # простые кейсы для Tests.createConsent (AllFilesDP)
```

## Два режима прогона кейсов

1. **Универсальный раннер** — `Tests.createConsent` + `AllFilesDP`: читает ВСЕ json-файлы из `testdata/api/`
   (каждый файл — один кейс или массив параметризаций) и прогоняет их одним методом. Логика общая:
   POST → проверка статуса → (опционально) схема → параметры → проверка БД.
   Флаг `expected.verifySchema: true` включает проверку JSON-схемы.
   Подходит для большинства простых позитивных/негативных кейсов — добавление кейса = новый файл в `testdata/api/`, код не трогается.
2. **Проверка БД — data-driven, из json кейса** (поле `expected.dbState`, enum `DbState`):
   - `CLEANUP` — запись по `x-idempotency-key` должна появиться, сверяется, затем удаляется (очистка);
   - `ABSENT` — записей в БД быть не должно;
   - `EXISTS` — запись должна появиться (без удаления — для кейсов, где запись нужна дальше);
   - `SKIP` — БД не проверять.
   Если `dbState` не задан, выводится из `statusCode`: 2xx → CLEANUP, 4xx/5xx → ABSENT.
   Ожидаемые сохранённые значения — `expected.dbParams`: список объектов `{table, column, value}`.
   `Tests.verifyDbParams` группирует параметры по таблицам и делает ОДИН запрос на таблицу
   (`DbClient.findColumnValues` — все нужные колонки разом как `::text`, значения сравниваются
   со строками из кейса). Пример:
   `{"table": "list_passports_payment_consent", "column": "amount", "value": "150.00"}`.
   В основной таблице поиск по `uuid`, в дочерних (event/signed/multi_authorisation) — по
   `list_passports_payment_consent_uuid`. Таблица без строк по ключу = падение кейса.
3. **Сценарии с состоянием** — отдельные методы в `Tests` + `FileDP` (файл в корне ресурсов = имя метода, без расширения):
   идемпотентность (409 при несовпадении тела, 201 при совпадении) — несколько зависимых запросов
   с проверками БД между ними, универсальной логикой не выражаются. Метод на сценарий, не на кейс.

## WireMock (заглушки даунстримов)

- WireMock-кейсы — отдельный пакет `testdata/wiremock/` и отдельный метод `Tests.createConsentWithWireMock`
  (DataProvider `WireMockDP`). Основной `Tests.createConsent` (AllFilesDP) и идемпотентные сценарии
  WireMock не знают — лишней логики в них нет. Общее тело прогона вынесено в
  `Tests.sendAndVerifyConsent` (используется обоими методами).
- Вызовы WireMock — `tests/common/wiremock`: реестр даунстримов `Downstream` (имя, метод, путь,
  дефолтный статус и тело ответа), переопределение ответа `StubResponse` (статус/тело),
  компонент `WireMockStubs` (`createStub`, `createAllStubs`, `deleteAllMappings`,
  `verifyStubResponds`, `requestCount`, `uploadStub` для произвольного маппинга).
  Внутри — простые вызовы RestAssured к Admin API (`/__admin/mappings`, `/__admin/requests/count`):
  отдельный HTTP-клиент (RestTemplate/Feign) не нужен, зависимость wiremock-сервера не добавляется.
- Выбор даунстримов и ответов — данными кейса (TestData): `downstreams` — имена enum Downstream
  в порядке предпроверки/проверки (не задано — все); `stubResponses` — переопределения
  {имя даунстрима: {status, body}} (body — JSON-объект или строка; не заданные поля — дефолт
  даунстрима; неизвестное имя — падение с понятной ошибкой). Пример:
  ```json
  "downstreams": ["WSO2_AUTHORIZED_APPS", "SIGN_RULE_SERVICE"],
  "stubResponses": {
    "SIGN_RULE_SERVICE": {
      "status": 200,
      "body": { "queryTypes": [ { "queryType": 453, "signGroupCodes": [1], "numberRequired": 1 } ] }
    }
  }
  ```
- Поток WireMock-кейса (`Tests.createConsentWithWireMock`): пересоздание стабов даунстримов кейса
  (чистые маппинги + ответы из данных) → предпроверка — тест сам бьёт в WireMock по заглушенным
  путям (`verifyStubResponds`), стаб не отвечает → падение до запроса к сервису → счётчики
  обращений из журнала до и после кейса — сервис должен дёрнуть каждый даунстрим сценария
  (приращение счётчика, журнал общий на прогон). Пересоздание под каждый кейс — следствие
  поддержки разных ответов одного даунстрима в разных кейсах.
- Жизненный цикл: удаление ВСЕХ стабов после завершения тестов класса
  (`Tests.deleteWireMockStubs`, @AfterClass alwaysRun — отработает даже при падениях).
  В k8s-прогоне (wiremock.cases.enabled=false) стабы не выставляются и не удаляются.
- В данных кейса НЕТ описания заглушек — вместо этого флаг `"local": true` в TestData: при
  `wiremock.cases.enabled=false` (k8s-прогон) такой кейс скипается через SkipException
  (в Allure виден как skipped). Локально флаг по умолчанию true — ничего настраивать не нужно;
  в k8s выключить env `WIREMOCK_CASES_ENABLED=false` или `-Dwiremock.cases.enabled=false`.
- Адрес Admin API — `wiremock.url` в `application.properties`. Тесты НЕ поднимают WireMock
  сами — предполагается отдельно развёрнутый WireMock, доступный тестируемому сервису по сети.
- **Инфраструктурное требование (вне этого проекта)**: сервис обязан ходить в WireMock вместо
  реальных даунстримов. Рабочий (kubernetes) деплой сервиса при этом НЕ меняется — под
  автотесты разворачивается отдельный инстанс/под сервиса (тот же образ), у которого env/профиль
  перекрывает URL даунстримов на WireMock (отдельный namespace: под сервиса + под WireMock).
  В CI реального проекта это роль TestRunner/PodSupport; URL этого тестового инстанса
  и есть `autotest.url`.

## DataProviders (восстановлены дословно по рабочему проекту — НЕ ИЗМЕНЯТЬ)

`DataProviders` и `JSONReader` — дословные копии со скриншотов рабочего проекта (Gson, `Iterator<Object[]>`,
возврат `null` при отсутствии ресурса, опечатка `methodeName` сохранена). Доработанный метод — только `AllFilesDP`.

- `MainDP` — кейсы метода из общего файла `/testData.json` (в корне ресурсов): корневой объект,
  ключ = имя тестового метода, значение = массив `TestData`.
- `FileDP` — файл целиком `/testdata/<имя метода>` **без расширения** (соглашение из рабочего проекта:
  «Файл — массив объектов TestData.class в формате JSON без расширения»), файл — обязательно массив.
- `AllFilesDP` (доработка) — все файлы `*.json` из `testdata/api/` (массивы `TestData`), имена файлов
  не привязаны к методам, чтение в отсортированном порядке. Список файлов ищется через classpath
  (`PathMatchingResourcePatternResolver`, `classpath*:testdata/api/*.json`) — как и чтение в остальных
  провайдерах, без обращения к файловой системе/CWD; само чтение — через `JSONReader.getTestDataFile`.
  Пустой каталог или нечитаемый файл → `IllegalStateException` (не тихий skip с пустыми данными).
- `DateFormatter` — подстановка дат `{now_<pattern>}` (напр. `{now_yyyy-MM-dd}`), вызывается внутри JSONReader.
- Заглушки под оригинальные классы рабочего проекта (при переносе заменить на оригиналы):
  `Matchers` (якорь для getResourceAsStream), `JSONReaderForKafka` (+ `MainDPForKafka`),
  `SuppliedTestData`, `DataSupplier`, `JacksonTreeFactory` (+ `SuppliedFileDP`).
- Зависимость `com.google.code.gson:gson` добавлена в pom (оригинал работает на Gson).

## Раскладка тестовых данных

```
src/test/resources/
├── getHealth                                        # FileDP: имя = имя метода, без расширения, массив TestData
├── create_consent_idempotent_body_mismatch_conflict_409
├── create_consent_idempotent_same_body_returns_201
├── testData.json                                    # MainDP (создать при переходе на общий файл)
└── testdata/api/*.json                              # AllFilesDP: простые кейсы, файлы = массивы TestData
```

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

Сценарии с состоянием (методы в `Tests`, файлы в корне ресурсов):

- `create_consent_idempotent_body_mismatch_conflict_409` — повтор с тем же ключом и другим телом → 409 Conflict, `BY.Rules.IllegalAttemptOfCreation`.
- `create_consent_idempotent_same_body_returns_201` — повтор с тем же ключом и совпадающим телом → 201, тот же `listPassportsConsentId`, дубль в БД не создаётся.

Шаг 2 обоих сценариев сверяет сохранённый в БД initiation ЦЕЛИКОМ (`DbClient.findInitiationByConsentId`,
рекурсивная сверка JSON из колонки `initiation` с `/listPassportsConsentRequest/data/initiation` тела
запроса) — по логике `isMatchWithExisting` сервиса, а не отдельное поле amount. Колонка читается как
`initiation::text` (приведение на стороне БД) — прямое чтение json/jsonb через `getString` падает
с `conversion to class java.lang.String is not supported`. Сверка (`Tests.assertJsonEquals`):
порядок полей в объектах не важен, числа по значению (150 = 150.0), порядок в массивах важен;
сообщение об ошибке показывает путь к расходящемуся полю.

Идемпотентные кейсы в конце удаляют созданные записи из БД (`DbClient.deleteAllDataConsentById`); негативные проверяют, что записей не появилось.
