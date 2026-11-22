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
 * Checks that the orders table rejects bad data on its own,
 * without any application code. All data here is fake.
 */
@Testcontainers
class OrderConstraintsTest {

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
	void validOrderIsAccepted() throws Exception {
		Fixture fixture = newFixture();

		assertThat(insertOrder(fixture.tenantId(), fixture.userId(), fixture.seatId(), fixture.holdId(), 5000,
				"PENDING")).isNotNull();
	}

	@Test
	void orderWithUnknownTenantIsRejected() throws Exception {
		Fixture fixture = newFixture();

		assertRejected(() -> insertOrder(UUID.randomUUID(), fixture.userId(), fixture.seatId(), fixture.holdId(),
				5000, "PENDING"), FOREIGN_KEY_VIOLATION);
	}

	@Test
	void orderWithUnknownUserIsRejected() throws Exception {
		Fixture fixture = newFixture();

		assertRejected(() -> insertOrder(fixture.tenantId(), UUID.randomUUID(), fixture.seatId(), fixture.holdId(),
				5000, "PENDING"), FOREIGN_KEY_VIOLATION);
	}

	@Test
	void orderWithUnknownSeatIsRejected() throws Exception {
		Fixture fixture = newFixture();

		assertRejected(() -> insertOrder(fixture.tenantId(), fixture.userId(), UUID.randomUUID(), fixture.holdId(),
				5000, "PENDING"), FOREIGN_KEY_VIOLATION);
	}

	@Test
	void orderWithUnknownHoldIsRejected() throws Exception {
		Fixture fixture = newFixture();

		assertRejected(() -> insertOrder(fixture.tenantId(), fixture.userId(), fixture.seatId(), UUID.randomUUID(),
				5000, "PENDING"), FOREIGN_KEY_VIOLATION);
	}

	@Test
	void orderWithUnknownStatusIsRejected() throws Exception {
		Fixture fixture = newFixture();

		// REFUNDED is not allowed yet; refunds come in a later phase.
		assertRejected(() -> insertOrder(fixture.tenantId(), fixture.userId(), fixture.seatId(), fixture.holdId(),
				5000, "REFUNDED"), CHECK_VIOLATION);
	}

	@Test
	void orderWithNegativePriceIsRejected() throws Exception {
		Fixture fixture = newFixture();

		assertRejected(() -> insertOrder(fixture.tenantId(), fixture.userId(), fixture.seatId(), fixture.holdId(),
				-1, "PENDING"), CHECK_VIOLATION);
	}

	@Test
	void orderWithZeroPriceIsAccepted() throws Exception {
		Fixture fixture = newFixture();

		// 0 is allowed on purpose, for example a free or complimentary seat.
		assertThat(insertOrder(fixture.tenantId(), fixture.userId(), fixture.seatId(), fixture.holdId(), 0,
				"PENDING")).isNotNull();
	}

	@Test
	void secondOrderForSameHoldIsRejected() throws Exception {
		Fixture fixture = newFixture();
		insertOrder(fixture.tenantId(), fixture.userId(), fixture.seatId(), fixture.holdId(), 5000, "PENDING");

		assertRejected(() -> insertOrder(fixture.tenantId(), fixture.userId(), fixture.seatId(), fixture.holdId(),
				5000, "PENDING"), UNIQUE_VIOLATION);
	}

	/** Runs the insert and expects Postgres to refuse it with the given error code. */
	private static void assertRejected(ThrowingInsert insert, String expectedSqlState) {
		assertThatThrownBy(insert::run)
				.isInstanceOfSatisfying(SQLException.class,
						e -> assertThat(e.getSQLState()).isEqualTo(expectedSqlState));
	}

	/** One tenant with one buyer, one seat and one active hold, ready to be ordered. */
	private record Fixture(UUID tenantId, UUID userId, UUID seatId, UUID holdId) {
	}

	private static Fixture newFixture() throws SQLException {
		UUID tenantId = insertReturningId(
				"INSERT INTO tenants (name, slug) VALUES ('Test Tenant', ?) RETURNING id",
				"tenant-" + UUID.randomUUID());
		UUID userId = insertReturningId(
				"INSERT INTO users (tenant_id, email, role) VALUES (?, 'buyer@example.com', 'BUYER') RETURNING id",
				tenantId);
		UUID eventId = insertReturningId(
				"INSERT INTO events (tenant_id, name, venue, starts_at, status) "
						+ "VALUES (?, 'Test Show', 'Test Hall', now() + interval '30 days', 'DRAFT') RETURNING id",
				tenantId);
		UUID seatId = insertReturningId(
				"INSERT INTO seats (tenant_id, event_id, label, price_cents, status) "
						+ "VALUES (?, ?, 'A1', 5000, 'AVAILABLE') RETURNING id",
				tenantId, eventId);
		UUID holdId = insertReturningId(
				"INSERT INTO seat_holds (tenant_id, seat_id, user_id, status, expires_at) "
						+ "VALUES (?, ?, ?, 'ACTIVE', now() + interval '10 minutes') RETURNING id",
				tenantId, seatId, userId);
		return new Fixture(tenantId, userId, seatId, holdId);
	}

	private static UUID insertOrder(UUID tenantId, UUID userId, UUID seatId, UUID holdId, int priceCents,
			String status) throws SQLException {
		return insertReturningId(
				"INSERT INTO orders (tenant_id, user_id, seat_id, hold_id, price_cents, status) "
						+ "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
				tenantId, userId, seatId, holdId, priceCents, status);
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
