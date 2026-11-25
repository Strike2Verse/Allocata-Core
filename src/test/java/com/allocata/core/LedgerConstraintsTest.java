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
 * Checks that the ledger_entries table is append-only and that every transaction
 * balances to zero, without any application code. All data here is fake.
 * The ledger cannot delete rows, so each test uses its own tenant and transaction id.
 */
@Testcontainers
class LedgerConstraintsTest {

	// Postgres error codes (SQLSTATE) for each kind of rejection.
	private static final String FOREIGN_KEY_VIOLATION = "23503";
	private static final String NOT_NULL_VIOLATION = "23502";
	// Our own codes from the migrations: LG001 append-only, LG002 unbalanced transaction.
	private static final String LEDGER_APPEND_ONLY = "LG001";
	private static final String LEDGER_UNBALANCED = "LG002";

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
	void balancedPairIsAccepted() throws Exception {
		UUID tenantId = insertTenant();
		UUID transactionId = UUID.randomUUID();

		insertTransaction(tenantId, transactionId,
				new Entry("CASH", 5000),
				new Entry("TICKET_REVENUE", -5000));

		assertThat(countEntries(transactionId)).isEqualTo(2);
	}

	@Test
	void balancedTransactionWithThreeRowsIsAccepted() throws Exception {
		UUID tenantId = insertTenant();
		UUID transactionId = UUID.randomUUID();

		insertTransaction(tenantId, transactionId,
				new Entry("CASH", 5000),
				new Entry("TICKET_REVENUE", -3000),
				new Entry("FEES", -2000));

		assertThat(countEntries(transactionId)).isEqualTo(3);
	}

	@Test
	void twoSeparateBalancedTransactionsAreAccepted() throws Exception {
		UUID tenantId = insertTenant();
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();

		insertTransaction(tenantId, first, new Entry("CASH", 5000), new Entry("TICKET_REVENUE", -5000));
		insertTransaction(tenantId, second, new Entry("CASH", 5000), new Entry("TICKET_REVENUE", -5000));

		assertThat(countEntries(first)).isEqualTo(2);
		assertThat(countEntries(second)).isEqualTo(2);
	}

	@Test
	void singleRowThatDoesNotBalanceIsRejectedAtCommit() throws Exception {
		UUID tenantId = insertTenant();
		UUID transactionId = UUID.randomUUID();

		assertRejected(() -> insertTransaction(tenantId, transactionId, new Entry("CASH", 5000)),
				LEDGER_UNBALANCED);
		assertThat(countEntries(transactionId)).isZero();
	}

	@Test
	void pairThatDoesNotBalanceIsRejectedAtCommit() throws Exception {
		UUID tenantId = insertTenant();
		UUID transactionId = UUID.randomUUID();

		assertRejected(() -> insertTransaction(tenantId, transactionId,
				new Entry("CASH", 5000),
				new Entry("TICKET_REVENUE", -4000)),
				LEDGER_UNBALANCED);
		assertThat(countEntries(transactionId)).isZero();
	}

	@Test
	void updatingAnEntryIsRejected() throws Exception {
		UUID tenantId = insertTenant();
		UUID transactionId = UUID.randomUUID();
		insertTransaction(tenantId, transactionId, new Entry("CASH", 5000), new Entry("TICKET_REVENUE", -5000));

		assertRejected(() -> execute("UPDATE ledger_entries SET amount_cents = 1 WHERE transaction_id = ?",
				transactionId), LEDGER_APPEND_ONLY);
		assertThat(countEntries(transactionId)).isEqualTo(2);
	}

	@Test
	void deletingAnEntryIsRejected() throws Exception {
		UUID tenantId = insertTenant();
		UUID transactionId = UUID.randomUUID();
		insertTransaction(tenantId, transactionId, new Entry("CASH", 5000), new Entry("TICKET_REVENUE", -5000));

		assertRejected(() -> execute("DELETE FROM ledger_entries WHERE transaction_id = ?", transactionId),
				LEDGER_APPEND_ONLY);
		assertThat(countEntries(transactionId)).isEqualTo(2);
	}

	@Test
	void truncatingTheLedgerIsRejected() throws Exception {
		UUID tenantId = insertTenant();
		UUID transactionId = UUID.randomUUID();
		insertTransaction(tenantId, transactionId, new Entry("CASH", 5000), new Entry("TICKET_REVENUE", -5000));

		assertRejected(() -> execute("TRUNCATE ledger_entries"), LEDGER_APPEND_ONLY);
		assertThat(countEntries(transactionId)).isEqualTo(2);
	}

	@Test
	void entryWithUnknownTenantIsRejected() {
		assertRejected(() -> insertTransaction(UUID.randomUUID(), UUID.randomUUID(),
				new Entry("CASH", 5000),
				new Entry("TICKET_REVENUE", -5000)),
				FOREIGN_KEY_VIOLATION);
	}

	@Test
	void entryWithoutAccountIsRejected() throws Exception {
		UUID tenantId = insertTenant();

		assertRejected(() -> insertTransaction(tenantId, UUID.randomUUID(),
				new Entry(null, 5000),
				new Entry("TICKET_REVENUE", -5000)),
				NOT_NULL_VIOLATION);
	}

	@Test
	void entryWithoutAmountIsRejected() throws Exception {
		UUID tenantId = insertTenant();

		assertRejected(() -> insertTransaction(tenantId, UUID.randomUUID(),
				new Entry("CASH", null),
				new Entry("TICKET_REVENUE", -5000)),
				NOT_NULL_VIOLATION);
	}

	/** One ledger row: an account and a signed amount in cents (positive debit, negative credit). */
	private record Entry(String account, Integer amountCents) {
	}

	/** Runs the action and expects Postgres to refuse it with the given error code. */
	private static void assertRejected(ThrowingAction action, String expectedSqlState) {
		assertThatThrownBy(action::run)
				.isInstanceOfSatisfying(SQLException.class,
						e -> assertThat(e.getSQLState()).isEqualTo(expectedSqlState));
	}

	/**
	 * Inserts all rows of one ledger transaction in a single database transaction.
	 * The balance check is deferred, so an unbalanced set fails at commit.
	 */
	private static void insertTransaction(UUID tenantId, UUID transactionId, Entry... entries) throws SQLException {
		try (Connection connection = open()) {
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO ledger_entries (tenant_id, transaction_id, account, amount_cents) "
							+ "VALUES (?, ?, ?, ?)")) {
				for (Entry entry : entries) {
					statement.setObject(1, tenantId);
					statement.setObject(2, transactionId);
					statement.setObject(3, entry.account(), java.sql.Types.VARCHAR);
					statement.setObject(4, entry.amountCents(), java.sql.Types.INTEGER);
					statement.executeUpdate();
				}
				connection.commit();
			} catch (SQLException e) {
				connection.rollback();
				throw e;
			}
		}
	}

	private static int countEntries(UUID transactionId) throws SQLException {
		try (Connection connection = open();
				PreparedStatement statement = connection
						.prepareStatement("SELECT count(*) FROM ledger_entries WHERE transaction_id = ?")) {
			statement.setObject(1, transactionId);
			try (ResultSet rows = statement.executeQuery()) {
				rows.next();
				return rows.getInt(1);
			}
		}
	}

	private static UUID insertTenant() throws SQLException {
		try (Connection connection = open();
				PreparedStatement statement = connection.prepareStatement(
						"INSERT INTO tenants (name, slug) VALUES ('Test Tenant', ?) RETURNING id")) {
			statement.setString(1, "tenant-" + UUID.randomUUID());
			try (ResultSet rows = statement.executeQuery()) {
				rows.next();
				return rows.getObject(1, UUID.class);
			}
		}
	}

	/** Runs one statement in auto-commit mode, with optional parameters. */
	private static void execute(String sql, Object... parameters) throws SQLException {
		try (Connection connection = open();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; i++) {
				statement.setObject(i + 1, parameters[i]);
			}
			statement.execute();
		}
	}

	private static Connection open() throws SQLException {
		return DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	@FunctionalInterface
	private interface ThrowingAction {
		void run() throws Exception;
	}
}
