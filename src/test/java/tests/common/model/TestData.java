package tests.common.model;

import lombok.Data;
import tests.common.wiremock.Downstream;

import java.util.List;

@Data
public class TestData {
    /** ID тест-кейса в Allure (TestOps), например "24188" */
    private String id;
    private String name;
    private String description;
    private Input input;
    private Expected expected;
    /**
     * Кейс предназначен только для локального прогона (напр. зависит от WireMock):
     * при wiremock.cases.enabled=false такой кейс скипается.
     */
    private boolean local;
    /**
     * Даунстримы сценария для WireMock-кейсов: полные описания ({@link Downstream} — имя,
     * метод, путь, статус и тело ответа) в порядке предпроверки/проверки. Каждый кейс
     * объявляет только свои даунстримы — реестра в коде нет. Не задано — без заглушек,
     * предпроверка и проверка обращений к даунстримам пропускаются.
     */
    private List<Downstream> downstreams;
    /**
     * Подготовка БД перед запросом ({@link DbSetup}): UPDATE колонок строки согласия
     * после её создания через API — для кейсов, где запись должна уже существовать
     * в определённом состоянии, а тестируемый метод её меняет. Имеет смысл только
     * в сценарных методах (между созданием согласия и тестируемым вызовом);
     * универсальный createConsent создаёт запись самим запросом.
     */
    private List<DbSetup> dbSetup;
    /**
     * Предсоздание согласия перед запросом (только GET-раннер): не задано/true — раннер
     * создаёт запись дефолтным позитивным запросом (поведение по умолчанию); false —
     * создание не нужно: одношаговые негативные кейсы валидации заголовков/параметров,
     * где запрос отклоняется до обращения к БД. При false путь GET-запроса должен
     * использовать "{randomUuid}", а dbSetup неприменим.
     */
    private Boolean createConsent;
}
