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
 * Checks that the seat_holds table allows at most one ACTIVE hold per seat,
 * without any application code. All data here is fake.
 */
@Testcontainers
class SeatHoldConstraintsTest {

	// Postgres error code (SQLSTATE) for a unique violation.
	private static final String UNIQUE_VIOLATION = "23505";

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
	void firstActiveHoldIsAccepted() throws Exception {
		Fixture fixture = newFixture();

		assertThat(insertHold(fixture, "ACTIVE")).isNotNull();
	}

	@Test
	void secondActiveHoldOnSameSeatIsRejected() throws Exception {
		Fixture fixture = newFixture();
		insertHold(fixture, "ACTIVE");

		assertThatThrownBy(() -> insertHold(fixture, "ACTIVE"))
				.isInstanceOfSatisfying(SQLException.class,
						e -> assertThat(e.getSQLState()).isEqualTo(UNIQUE_VIOLATION));
	}

	@Test
	void newActiveHoldIsAcceptedAfterFirstIsReleased() throws Exception {
		Fixture fixture = newFixture();
		UUID firstHold = insertHold(fixture, "ACTIVE");

		setStatus(firstHold, "RELEASED");

		assertThat(insertHold(fixture, "ACTIVE")).isNotNull();
	}

	@Test
	void activeHoldsOnDifferentSeatsAreAccepted() throws Exception {
		Fixture first = newFixture();
		insertHold(first, "ACTIVE");
		Fixture second = newFixture();

		assertThat(insertHold(second, "ACTIVE")).isNotNull();
	}

	@Test
	void manyNonActiveHoldsOnSameSeatAreAccepted() throws Exception {
		Fixture fixture = newFixture();

		// The unique index only covers ACTIVE holds, so history can pile up.
		assertThat(insertHold(fixture, "EXPIRED")).isNotNull();
		assertThat(insertHold(fixture, "EXPIRED")).isNotNull();
		assertThat(insertHold(fixture, "RELEASED")).isNotNull();
		assertThat(insertHold(fixture, "CONVERTED")).isNotNull();
	}

	/** One tenant with one buyer and one seat, ready to be held. */
	private record Fixture(UUID tenantId, UUID seatId, UUID userId) {
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
		return new Fixture(tenantId, seatId, userId);
	}

	private static UUID insertHold(Fixture fixture, String status) throws SQLException {
		return insertReturningId(
				"INSERT INTO seat_holds (tenant_id, seat_id, user_id, status, expires_at) "
						+ "VALUES (?, ?, ?, ?, now() + interval '10 minutes') RETURNING id",
				fixture.tenantId(), fixture.seatId(), fixture.userId(), status);
	}

	private static void setStatus(UUID holdId, String status) throws SQLException {
		try (Connection connection = open();
				PreparedStatement statement = connection.prepareStatement(
						"UPDATE seat_holds SET status = ? WHERE id = ?")) {
			statement.setString(1, status);
			statement.setObject(2, holdId);
			statement.executeUpdate();
		}
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
}
