package tests.common.dataprovider;

import tests.common.model.TestData;

import java.io.IOException;
import java.util.Iterator;

/**
 * Загрузка одиночных ресурсов с тест-кейсами вне DataProviders — напр. дефолтный
 * позитивный запрос создания согласия, которым Tests.getConsent готовит запись в БД.
 * Ресурс валидируется TestDataValidator ДО десериализации (см. AGENTS.md — Gson молча
 * игнорирует неизвестные поля), отсутствие ресурса или пустой массив — IllegalStateException.
 */
public final class CaseLoader {

    private CaseLoader() {
    }

    /** Читает ресурс с кейсами (массив TestData) и возвращает первый — для одиночных ресурсов. */
    public static TestData singleCase(String resourcePath) {
        TestDataValidator.validateResource(resourcePath, null);
        Iterator<Object[]> rows;
        try {
            rows = JSONReader.getTestDataFile(resourcePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read test case resource: /" + resourcePath, e);
        }
        if (rows == null || !rows.hasNext()) {
            throw new IllegalStateException("No test cases in resource: /" + resourcePath);
        }
        return (TestData) rows.next()[0];
    }
}
