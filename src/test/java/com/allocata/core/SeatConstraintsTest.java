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
 * Checks that the events and seats tables reject bad data on their own,
 * without any application code. All data here is fake.
 */
@Testcontainers
class SeatConstraintsTest {

	// Postgres error codes (SQLSTATE) for each kind of rejection.
	private static final String FOREIGN_KEY_VIOLATION = "23503";
	private static final String UNIQUE_VIOLATION = "23505";
	private static final String CHECK_VIOLATION = "23514";

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
	void validEventAndSeatAreAccepted() throws Exception {
		UUID tenantId = insertTenant();
		UUID eventId = insertEvent(tenantId, "DRAFT");

		UUID seatId = insertSeat(tenantId, eventId, "A1", 5000, "AVAILABLE");

		assertThat(seatId).isNotNull();
	}

	@Test
	void eventWithUnknownTenantIsRejected() {
		assertRejected(() -> insertEvent(UUID.randomUUID(), "DRAFT"), FOREIGN_KEY_VIOLATION);
	}

	@Test
	void eventWithUnknownStatusIsRejected() throws Exception {
		UUID tenantId = insertTenant();

		assertRejected(() -> insertEvent(tenantId, "CANCELLED"), CHECK_VIOLATION);
	}

	@Test
	void seatWithUnknownTenantIsRejected() throws Exception {
		UUID eventId = insertEvent(insertTenant(), "DRAFT");

		assertRejected(() -> insertSeat(UUID.randomUUID(), eventId, "A1", 5000, "AVAILABLE"),
				FOREIGN_KEY_VIOLATION);
	}

	@Test
	void seatWithUnknownEventIsRejected() throws Exception {
		UUID tenantId = insertTenant();

		assertRejected(() -> insertSeat(tenantId, UUID.randomUUID(), "A1", 5000, "AVAILABLE"),
				FOREIGN_KEY_VIOLATION);
	}

	@Test
	void seatWithUnknownStatusIsRejected() throws Exception {
		UUID tenantId = insertTenant();
		UUID eventId = insertEvent(tenantId, "DRAFT");

		assertRejected(() -> insertSeat(tenantId, eventId, "A1", 5000, "RESERVED"), CHECK_VIOLATION);
	}

	@Test
	void seatWithNegativePriceIsRejected() throws Exception {
		UUID tenantId = insertTenant();
		UUID eventId = insertEvent(tenantId, "DRAFT");

		assertRejected(() -> insertSeat(tenantId, eventId, "A1", -1, "AVAILABLE"), CHECK_VIOLATION);
	}

	@Test
	void seatWithZeroPriceIsAccepted() throws Exception {
		UUID tenantId = insertTenant();
		UUID eventId = insertEvent(tenantId, "DRAFT");

		// 0 is allowed on purpose, for example a free or complimentary seat.
		assertThat(insertSeat(tenantId, eventId, "A1", 0, "AVAILABLE")).isNotNull();
	}

	@Test
	void duplicateSeatLabelInSameEventIsRejected() throws Exception {
		UUID tenantId = insertTenant();
		UUID eventId = insertEvent(tenantId, "DRAFT");
		insertSeat(tenantId, eventId, "A1", 5000, "AVAILABLE");

		assertRejected(() -> insertSeat(tenantId, eventId, "A1", 6000, "AVAILABLE"), UNIQUE_VIOLATION);
	}

	@Test
	void sameSeatLabelInDifferentEventsIsAccepted() throws Exception {
		UUID tenantId = insertTenant();
		UUID firstEvent = insertEvent(tenantId, "DRAFT");
		UUID secondEvent = insertEvent(tenantId, "DRAFT");
		insertSeat(tenantId, firstEvent, "A1", 5000, "AVAILABLE");

		assertThat(insertSeat(tenantId, secondEvent, "A1", 5000, "AVAILABLE")).isNotNull();
	}

	/** Runs the insert and expects Postgres to refuse it with the given error code. */
	private static void assertRejected(ThrowingInsert insert, String expectedSqlState) {
		assertThatThrownBy(insert::run)
				.isInstanceOfSatisfying(SQLException.class,
						e -> assertThat(e.getSQLState()).isEqualTo(expectedSqlState));
	}

	private static UUID insertTenant() throws SQLException {
		String slug = "tenant-" + UUID.randomUUID();
		return insertReturningId("INSERT INTO tenants (name, slug) VALUES (?, ?) RETURNING id",
				"Test Tenant", slug);
	}

	private static UUID insertEvent(UUID tenantId, String status) throws SQLException {
		try (Connection connection = open();
				PreparedStatement statement = connection.prepareStatement(
						"INSERT INTO events (tenant_id, name, venue, starts_at, status) "
								+ "VALUES (?, 'Test Show', 'Test Hall', now() + interval '30 days', ?) RETURNING id")) {
			statement.setObject(1, tenantId);
			statement.setString(2, status);
			return readId(statement);
		}
	}

	private static UUID insertSeat(UUID tenantId, UUID eventId, String label, int priceCents, String status)
			throws SQLException {
		try (Connection connection = open();
				PreparedStatement statement = connection.prepareStatement(
						"INSERT INTO seats (tenant_id, event_id, label, price_cents, status) "
								+ "VALUES (?, ?, ?, ?, ?) RETURNING id")) {
			statement.setObject(1, tenantId);
			statement.setObject(2, eventId);
			statement.setString(3, label);
			statement.setInt(4, priceCents);
			statement.setString(5, status);
			return readId(statement);
		}
	}

	private static UUID insertReturningId(String sql, String first, String second) throws SQLException {
		try (Connection connection = open();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, first);
			statement.setString(2, second);
			return readId(statement);
		}
	}

	private static UUID readId(PreparedStatement statement) throws SQLException {
		try (ResultSet rows = statement.executeQuery()) {
			rows.next();
			return rows.getObject(1, UUID.class);
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
