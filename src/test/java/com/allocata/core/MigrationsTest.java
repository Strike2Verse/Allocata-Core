package com.allocata.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs every Flyway migration on a brand new, empty Postgres database.
 * The database is a throwaway container, so the real local database is never touched.
 */
@Testcontainers
class MigrationsTest {

	// Same image as docker-compose.yml, so the test matches the real database.
	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	@Test
	void allMigrationsRunOnEmptyDatabase() throws Exception {
		Flyway flyway = Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.locations("classpath:db/migration")
				.load();

		MigrateResult result = flyway.migrate();

		int migrationFiles = new PathMatchingResourcePatternResolver()
				.getResources("classpath:db/migration/V*.sql").length;

		assertThat(migrationFiles).isGreaterThan(0);
		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted).isEqualTo(migrationFiles);
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(countFailedMigrations()).isZero();
	}

	private int countFailedMigrations() throws Exception {
		try (Connection connection = DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
				Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery(
						"SELECT count(*) FROM flyway_schema_history WHERE success = false")) {
			rows.next();
			return rows.getInt(1);
		}
	}
}
