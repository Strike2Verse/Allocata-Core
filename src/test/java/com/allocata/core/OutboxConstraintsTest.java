package com.allocata.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Checks that the outbox_events table rejects bad data on its own,
 * without any application code. All data here is fake.
 */
@Testcontainers
class OutboxConstraintsTest {

	// Postgres error codes (SQLSTATE) for each kind of rejection.
	private static final String FOREIGN_KEY_VIOLATION = "23503";
	private static final String NOT_NULL_VIOLATION = "23502";
	private static final String CHECK_VIOLATION = "23514";
	private static final String INVALID_TEXT_REPRESENTATION = "22P02";

	private static final String VALID_PAYLOAD = "{\"seatId\": \"fake-seat-1\"}";

	// Same image as docker-compose.yml, so the test matches the real database.
	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	@BeforeAll
	static void migrate() {
		Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.locations("classpath:db/migration")
				.load()
				.migrate();
	}

	@Test
	void validEventIsAcceptedWithDefaults() throws Exception {
		UUID tenantId = insertTenant();

		UUID id = insertEvent(tenantId, "SEAT_HELD", UUID.randomUUID(), VALID_PAYLOAD, null);

		try (Connection connection = open();
				PreparedStatement statement = connection
						.prepareStatement("SELECT status, attempts FROM outbox_events WHERE id = ?")) {
			statement.setObject(1, id);
			try (ResultSet rows = statement.executeQuery()) {
				assertThat(rows.next()).isTrue();
				assertThat(rows.getString("status")).isEqualTo("PENDING");
				assertThat(rows.getInt("attempts")).isZero();
			}
		}
	}

	@Test
	void sentAndFailedStatusesAreAccepted() throws Exception {
		UUID tenantId = insertTenant();

		assertThat(insertEvent(tenantId, "SEAT_HELD", UUID.randomUUID(), VALID_PAYLOAD, "SENT")).isNotNull();
		assertThat(insertEvent(tenantId, "SEAT_HELD", UUID.randomUUID(), VALID_PAYLOAD, "FAILED")).isNotNull();
	}

	@Test
	void eventWithUnknownTenantIsRejected() {
		assertRejected(() -> insertEvent(UUID.randomUUID(), "SEAT_HELD", UUID.randomUUID(), VALID_PAYLOAD, null),
				FOREIGN_KEY_VIOLATION);
	}

	@Test
	void eventWithUnknownStatusIsRejected() throws Exception {
		UUID tenantId = insertTenant();

		assertRejected(() -> insertEvent(tenantId, "SEAT_HELD", UUID.randomUUID(), VALID_PAYLOAD, "DONE"),
				CHECK_VIOLATION);
	}

	@Test
	void payloadThatIsNotValidJsonIsRejected() throws Exception {
		UUID tenantId = insertTenant();

		assertRejected(() -> insertEvent(tenantId, "SEAT_HELD", UUID.randomUUID(), "not json", null),
				INVALID_TEXT_REPRESENTATION);
	}

	@Test
	void eventWithoutPayloadIsRejected() throws Exception {
		UUID tenantId = insertTenant();

		assertRejected(() -> insertEvent(tenantId, "SEAT_HELD", UUID.randomUUID(), null, null),
				NOT_NULL_VIOLATION);
	}

	@Test
	void eventWithoutTypeIsRejected() throws Exception {
		UUID tenantId = insertTenant();

		assertRejected(() -> insertEvent(tenantId, null, UUID.randomUUID(), VALID_PAYLOAD, null),
				NOT_NULL_VIOLATION);
	}

	@Test
	void eventWithoutAggregateIdIsRejected() throws Exception {
		UUID tenantId = insertTenant();

		assertRejected(() -> insertEvent(tenantId, "SEAT_HELD", null, VALID_PAYLOAD, null),
				NOT_NULL_VIOLATION);
	}

	/** Runs the insert and expects Postgres to refuse it with the given error code. */
	private static void assertRejected(ThrowingInsert insert, String expectedSqlState) {
		assertThatThrownBy(insert::run)
				.isInstanceOfSatisfying(SQLException.class,
						e -> assertThat(e.getSQLState()).isEqualTo(expectedSqlState));
	}

	private static UUID insertTenant() throws SQLException {
		return insertReturningId(
				"INSERT INTO tenants (name, slug) VALUES ('Test Tenant', ?) RETURNING id",
				"tenant-" + UUID.randomUUID());
	}

	/** A null status leaves the column out, so the database default (PENDING) applies. */
	private static UUID insertEvent(UUID tenantId, String eventType, UUID aggregateId, String payload, String status)
			throws SQLException {
		if (status == null) {
			return insertReturningId(
					"INSERT INTO outbox_events (tenant_id, event_type, aggregate_id, payload) "
							+ "VALUES (?, ?, ?, ?::jsonb) RETURNING id",
					tenantId, eventType, aggregateId, payload);
		}
		return insertReturningId(
				"INSERT INTO outbox_events (tenant_id, event_type, aggregate_id, payload, status) "
						+ "VALUES (?, ?, ?, ?::jsonb, ?) RETURNING id",
				tenantId, eventType, aggregateId, payload, status);
	}

	private static UUID insertReturningId(String sql, Object... parameters) throws SQLException {
		try (Connection connection = open();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; i++) {
				statement.setObject(i + 1, parameters[i]);
			}
			try (ResultSet rows = statement.executeQuery()) {
				rows.next();
				return rows.getObject(1, UUID.class);
			}
		}
	}

	private static Connection open() throws SQLException {
		return DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	@FunctionalInterface
	private interface ThrowingInsert {
		void run() throws Exception;
	}
}
