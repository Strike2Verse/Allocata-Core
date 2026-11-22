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
 * Checks that the waitlist_entries table rejects bad data on its own,
 * without any application code. All data here is fake.
 */
@Testcontainers
class WaitlistConstraintsTest {

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
	void firstWaitlistEntryIsAccepted() throws Exception {
		Fixture fixture = newFixture();

		assertThat(insertEntry(fixture.tenantId(), fixture.eventId(), fixture.userId(), "WAITING")).isNotNull();
	}

	@Test
	void sameUserJoiningSameEventTwiceIsRejected() throws Exception {
		Fixture fixture = newFixture();
		insertEntry(fixture.tenantId(), fixture.eventId(), fixture.userId(), "WAITING");

		assertRejected(() -> insertEntry(fixture.tenantId(), fixture.eventId(), fixture.userId(), "WAITING"),
				UNIQUE_VIOLATION);
	}

	@Test
	void sameUserOnDifferentEventIsAccepted() throws Exception {
		Fixture fixture = newFixture();
		UUID otherEvent = insertEvent(fixture.tenantId());
		insertEntry(fixture.tenantId(), fixture.eventId(), fixture.userId(), "WAITING");

		assertThat(insertEntry(fixture.tenantId(), otherEvent, fixture.userId(), "WAITING")).isNotNull();
	}

	@Test
	void differentUserOnSameEventIsAccepted() throws Exception {
		Fixture fixture = newFixture();
		UUID otherUser = insertUser(fixture.tenantId(), "other@example.com");
		insertEntry(fixture.tenantId(), fixture.eventId(), fixture.userId(), "WAITING");

		assertThat(insertEntry(fixture.tenantId(), fixture.eventId(), otherUser, "WAITING")).isNotNull();
	}

	@Test
	void entryWithUnknownEventIsRejected() throws Exception {
		Fixture fixture = newFixture();

		assertRejected(() -> insertEntry(fixture.tenantId(), UUID.randomUUID(), fixture.userId(), "WAITING"),
				FOREIGN_KEY_VIOLATION);
	}

	@Test
	void entryWithUnknownUserIsRejected() throws Exception {
		Fixture fixture = newFixture();

		assertRejected(() -> insertEntry(fixture.tenantId(), fixture.eventId(), UUID.randomUUID(), "WAITING"),
				FOREIGN_KEY_VIOLATION);
	}

	@Test
	void entryWithUnknownStatusIsRejected() throws Exception {
		Fixture fixture = newFixture();

		assertRejected(() -> insertEntry(fixture.tenantId(), fixture.eventId(), fixture.userId(), "EXPIRED"),
				CHECK_VIOLATION);
	}

	/** Runs the insert and expects Postgres to refuse it with the given error code. */
	private static void assertRejected(ThrowingInsert insert, String expectedSqlState) {
		assertThatThrownBy(insert::run)
				.isInstanceOfSatisfying(SQLException.class,
						e -> assertThat(e.getSQLState()).isEqualTo(expectedSqlState));
	}

	/** One tenant with one buyer and one event, ready for a waitlist entry. */
	private record Fixture(UUID tenantId, UUID eventId, UUID userId) {
	}

	private static Fixture newFixture() throws SQLException {
		UUID tenantId = insertReturningId(
				"INSERT INTO tenants (name, slug) VALUES ('Test Tenant', ?) RETURNING id",
				"tenant-" + UUID.randomUUID());
		UUID userId = insertUser(tenantId, "buyer@example.com");
		UUID eventId = insertEvent(tenantId);
		return new Fixture(tenantId, eventId, userId);
	}

	private static UUID insertUser(UUID tenantId, String email) throws SQLException {
		return insertReturningId(
				"INSERT INTO users (tenant_id, email, role) VALUES (?, ?, 'BUYER') RETURNING id",
				tenantId, email);
	}

	private static UUID insertEvent(UUID tenantId) throws SQLException {
		return insertReturningId(
				"INSERT INTO events (tenant_id, name, venue, starts_at, status) "
						+ "VALUES (?, 'Test Show', 'Test Hall', now() + interval '30 days', 'ON_SALE') RETURNING id",
				tenantId);
	}

	private static UUID insertEntry(UUID tenantId, UUID eventId, UUID userId, String status) throws SQLException {
		return insertReturningId(
				"INSERT INTO waitlist_entries (tenant_id, event_id, user_id, status) "
						+ "VALUES (?, ?, ?, ?) RETURNING id",
				tenantId, eventId, userId, status);
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
