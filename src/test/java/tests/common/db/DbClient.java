package tests.common.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Прямой доступ к БД тестируемого сервиса для проверок и очистки тестовых данных.
 *
 * AGENTS.md: имена таблиц и колонок — допущение (snake_case от имён сущностей сервиса):
 *   idempotency_key(list_passports_payment_consent_uuid, idempotency_key),
 *   list_passports_payment_consent(uuid, initiation),
 *   list_passports_payment_consent_event(list_passports_payment_consent_uuid),
 *   list_passports_payment_consent_signed(list_passports_payment_consent_uuid),
 *   multi_authorisation(list_passports_payment_consent_uuid).
 * При расхождении с реальной схемой поправьте константы ниже.
 */
@Slf4j
@Component
public class DbClient {

    private static final String TABLE_IDEMPOTENCY_KEY = "idempotency_key";
    private static final String TABLE_CONSENT = "list_passports_payment_consent";
    private static final String TABLE_CONSENT_EVENT = "list_passports_payment_consent_event";
    private static final String TABLE_CONSENT_SIGNED = "list_passports_payment_consent_signed";
    private static final String TABLE_MULTI_AUTHORISATION = "multi_authorisation";

    private static final String COL_CONSENT_UUID = "list_passports_payment_consent_uuid";
    private static final String COL_IDEMPOTENCY_KEY = "idempotency_key";
    private static final String COL_UUID = "uuid";
    private static final String COL_INITIATION = "initiation";

    private final JdbcTemplate jdbcTemplate;

    public DbClient(@Value("${db.url}") String dbUrl,
                    @Value("${db.user}") String dbUser,
                    @Value("${db.password}") String dbPassword) {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(org.postgresql.Driver.class);
        dataSource.setUrl(dbUrl);
        dataSource.setUsername(dbUser);
        dataSource.setPassword(dbPassword);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /** Ищет UUID согласия по x-idempotency-key. */
    public Optional<UUID> findConsentUuidByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.query(
                        "SELECT " + COL_CONSENT_UUID + " FROM " + TABLE_IDEMPOTENCY_KEY
                                + " WHERE " + COL_IDEMPOTENCY_KEY + " = ?",
                        (rs, rowNum) -> rs.getObject(COL_CONSENT_UUID, UUID.class),
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    /**
     * Возвращает все строки таблицы по UUID согласия — одним запросом на таблицу,
     * для сверки с ожидаемыми строками из кейса. В основной таблице согласие ищется
     * по uuid, в дочерних (signed/event/multi_authorisation) — по list_passports_payment_consent_uuid.
     * Значения приводятся к строке через toString — единообразно для numeric/timestamp,
     * json/jsonb приходят PGobject'ом, toString даёт JSON-текст.
     * Таблица без строк по ключу — пустой список (падение с проверкой в тесте, не исключением).
     */
    public List<Map<String, String>> findRows(String table, UUID consentUuid) {
        String keyColumn = TABLE_CONSENT.equals(table) ? COL_UUID : COL_CONSENT_UUID;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + validateIdentifier(table) + " WHERE " + keyColumn + " = ?",
                consentUuid);
        return rows.stream()
                .map(row -> {
                    Map<String, String> converted = new LinkedHashMap<>();
                    row.forEach((column, value) -> converted.put(column, value != null ? value.toString() : null));
                    return converted;
                })
                .collect(Collectors.toList());
    }

    /**
     * UPDATE колонок строки по UUID согласия — подготовка данных кейса (TestData.dbSetup):
     * доводит созданное через API согласие до нужного состояния (напр. меняет статус).
     * Ключ строки — как в findRows: uuid в основной таблице, list_passports_payment_consent_uuid
     * в дочерних. Возвращает число обновлённых строк — 0 означает, что строки нет (падение в тесте).
     */
    public int updateColumns(String table, Map<String, String> columns, UUID consentUuid) {
        String keyColumn = TABLE_CONSENT.equals(table) ? COL_UUID : COL_CONSENT_UUID;
        String setClause = columns.keySet().stream()
                .map(DbClient::validateIdentifier)
                .map(column -> column + " = ?")
                .collect(Collectors.joining(", "));
        List<Object> args = new ArrayList<>(columns.values());
        args.add(consentUuid);
        int updated = jdbcTemplate.update(
                "UPDATE " + validateIdentifier(table) + " SET " + setClause + " WHERE " + keyColumn + " = ?",
                args.toArray());
        log.info("Updated {} row(s) in {} (consent {})", updated, table, consentUuid);
        return updated;
    }

    /** Имена таблиц/колонок подставляются в SQL — пропускаем только простые идентификаторы. */
    private static String validateIdentifier(String identifier) {        if (identifier == null || !identifier.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL identifier in dbParams: " + identifier);
        }
        return identifier;
    }

    /** Возвращает initiation согласия как JSON-строку (колонка json) — для сверки с телом запроса целиком. */
    public Optional<String> findInitiationByConsentId(UUID consentUuid) {
        // ::text — приведение json/jsonb к varchar на стороне БД: драйверу отдаётся обычная строка,
        // иначе чтение колонки json напрямую через getString падает с "conversion ... is not supported"
        return jdbcTemplate.query(
                        "SELECT " + COL_INITIATION + "::text FROM " + TABLE_CONSENT + " WHERE " + COL_UUID + " = ?",
                        (rs, rowNum) -> rs.getString(COL_INITIATION),
                        consentUuid)
                .stream()
                .findFirst();
    }

    /**
     * Удаляет все данные согласия по его UUID (аналог deleteAllDataConsentById в сервисе):
     * подписи, мультиавторизация, события, идемпотентность, само согласие.
     */
    public void deleteAllDataConsentById(UUID consentUuid) {
        int deleted;
        // AGENTS.md: имена таблиц и колонок вынесены в константы выше — при расхождении со схемой БД править там
        deleted = jdbcTemplate.update("DELETE FROM " + TABLE_CONSENT_SIGNED + " WHERE " + COL_CONSENT_UUID + " = ?", consentUuid);
        log.info("Deleted {} rows from {}", deleted, TABLE_CONSENT_SIGNED);
        deleted = jdbcTemplate.update("DELETE FROM " + TABLE_MULTI_AUTHORISATION + " WHERE " + COL_CONSENT_UUID + " = ?", consentUuid);
        log.info("Deleted {} rows from {}", deleted, TABLE_MULTI_AUTHORISATION);
        deleted = jdbcTemplate.update("DELETE FROM " + TABLE_CONSENT_EVENT + " WHERE " + COL_CONSENT_UUID + " = ?", consentUuid);
        log.info("Deleted {} rows from {}", deleted, TABLE_CONSENT_EVENT);
        deleted = jdbcTemplate.update("DELETE FROM " + TABLE_IDEMPOTENCY_KEY + " WHERE " + COL_CONSENT_UUID + " = ?", consentUuid);
        log.info("Deleted {} rows from {}", deleted, TABLE_IDEMPOTENCY_KEY);
        deleted = jdbcTemplate.update("DELETE FROM " + TABLE_CONSENT + " WHERE " + COL_UUID + " = ?", consentUuid);
        log.info("Deleted {} rows from {}", deleted, TABLE_CONSENT);
    }
}
