package tests.common.model;

import lombok.Data;

@Data
public class TestData {
    /** ID тест-кейса в Allure (TestOps), например "24188" */
    private String id;
    private String name;
    private String description;
    private Input input;
    private Expected expected;
}
