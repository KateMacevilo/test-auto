package tests.common.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Прямой доступ к БД тестируемого сервиса для проверок и очистки тестовых данных.
 *
 * AGENTS.md: имена таблиц и колонок — допущение (snake_case от имён сущностей сервиса):
 *   idempotency_key(list_passports_payment_consent_uuid, idempotency_key),
 *   list_passports_payment_consent(uuid, amount),
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
    private static final String COL_AMOUNT = "amount";

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

    /** Возвращает сумму (amount), сохранённую в согласии. */
    public BigDecimal findConsentAmount(UUID consentUuid) {
        return jdbcTemplate.queryForObject(
                "SELECT " + COL_AMOUNT + " FROM " + TABLE_CONSENT + " WHERE " + COL_UUID + " = ?",
                BigDecimal.class, consentUuid);
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
