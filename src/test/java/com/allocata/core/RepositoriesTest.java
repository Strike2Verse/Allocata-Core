package com.allocata.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.allocata.core.event.Event;
import com.allocata.core.event.EventRepository;
import com.allocata.core.hold.HoldStatus;
import com.allocata.core.hold.SeatHold;
import com.allocata.core.hold.SeatHoldRepository;
import com.allocata.core.ledger.LedgerEntry;
import com.allocata.core.ledger.LedgerEntryRepository;
import com.allocata.core.order.Order;
import com.allocata.core.order.OrderRepository;
import com.allocata.core.outbox.OutboxEvent;
import com.allocata.core.outbox.OutboxEventRepository;
import com.allocata.core.outbox.OutboxStatus;
import com.allocata.core.seat.Seat;
import com.allocata.core.seat.SeatRepository;
import com.allocata.core.seat.SeatStatus;
import com.allocata.core.tenant.Tenant;
import com.allocata.core.tenant.TenantRepository;
import com.allocata.core.user.User;
import com.allocata.core.user.UserRepository;
import com.allocata.core.user.UserRole;
import com.allocata.core.waitlist.WaitlistEntry;
import com.allocata.core.waitlist.WaitlistEntryRepository;
import com.allocata.core.waitlist.WaitlistStatus;

import jakarta.persistence.EntityManager;

/**
 * Real saves and queries through the repositories on a throwaway database.
 * Each test runs in a transaction that is rolled back at the end, except the
 * ledger tests, which must commit because the balance is checked at commit.
 * All data here is fake.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class RepositoriesTest {

	// Same image as docker-compose.yml, so the test matches the real database.
	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	/** Points the app and Flyway at the container instead of the local stack. */
	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
		registry.add("spring.flyway.user", POSTGRES::getUsername);
		registry.add("spring.flyway.password", POSTGRES::getPassword);
	}

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private TenantRepository tenants;

	@Autowired
	private UserRepository users;

	@Autowired
	private EventRepository events;

	@Autowired
	private SeatRepository seats;

	@Autowired
	private SeatHoldRepository holds;

	@Autowired
	private OrderRepository orders;

	@Autowired
	private WaitlistEntryRepository waitlist;

	@Autowired
	private OutboxEventRepository outbox;

	@Autowired
	private LedgerEntryRepository ledger;

	// Tenants, users and events

	@Test
	void tenantIsFoundBySlug() {
		Tenant tenant = newTenant();

		assertThat(tenants.findBySlug(tenant.getSlug())).get()
				.extracting(Tenant::getId).isEqualTo(tenant.getId());
	}

	@Test
	void userIsFoundByTenantAndEmailButNotInAnotherTenant() {
		Tenant tenant = newTenant();
		Tenant otherTenant = newTenant();
		User buyer = users.save(new User(tenant.getId(), "buyer1@example.com", UserRole.BUYER));

		assertThat(users.findByTenantIdAndEmail(tenant.getId(), "buyer1@example.com")).get()
				.extracting(User::getId).isEqualTo(buyer.getId());
		assertThat(users.findByTenantIdAndEmail(otherTenant.getId(), "buyer1@example.com")).isEmpty();
	}

	@Test
	void eventIsFoundOnlyByItsOwnTenant() {
		Tenant tenant = newTenant();
		Tenant otherTenant = newTenant();
		Event event = newEvent(tenant);

		assertThat(events.findByIdAndTenantId(event.getId(), tenant.getId())).isPresent();
		assertThat(events.findByIdAndTenantId(event.getId(), otherTenant.getId())).isEmpty();
	}

	// Seats

	@Test
	void seatsOfAnEventComeBackInLabelOrder() {
		Tenant tenant = newTenant();
		Event event = newEvent(tenant);
		seats.save(new Seat(tenant.getId(), event.getId(), "A3", 5000));
		seats.save(new Seat(tenant.getId(), event.getId(), "A1", 5000));
		seats.save(new Seat(tenant.getId(), event.getId(), "A2", 5000));

		assertThat(seats.findByEventIdOrderByLabel(event.getId()))
				.extracting(Seat::getLabel).containsExactly("A1", "A2", "A3");
	}

	@Test
	void availableSeatsAreCountedPerEvent() {
		Tenant tenant = newTenant();
		Event event = newEvent(tenant);
		Event otherEvent = newEvent(tenant);
		seats.save(new Seat(tenant.getId(), event.getId(), "A1", 5000));
		seats.save(new Seat(tenant.getId(), event.getId(), "A2", 5000));
		seats.save(new Seat(tenant.getId(), otherEvent.getId(), "A1", 5000));

		assertThat(seats.countByEventIdAndStatus(event.getId(), SeatStatus.AVAILABLE)).isEqualTo(2);
		assertThat(seats.countByEventIdAndStatus(event.getId(), SeatStatus.SOLD)).isZero();
	}

	@Test
	void newSeatIsStoredAsAvailableWithVersionZero() {
		Tenant tenant = newTenant();
		Event event = newEvent(tenant);
		Seat seat = seats.save(new Seat(tenant.getId(), event.getId(), "A1", 5000));
		reloadFromDatabase();

		Seat stored = seats.findById(seat.getId()).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
		assertThat(stored.getVersion()).isZero();
		assertThat(stored.getPriceCents()).isEqualTo(5000);
	}

	// Holds and orders

	@Test
	void activeHoldIsFoundBySeat() {
		Booking booking = newBooking();
		SeatHold hold = holds.save(new SeatHold(booking.tenantId(), booking.seatId(), booking.userId(),
				Instant.now().plus(10, ChronoUnit.MINUTES)));

		assertThat(holds.findBySeatIdAndStatus(booking.seatId(), HoldStatus.ACTIVE)).get()
				.extracting(SeatHold::getId).isEqualTo(hold.getId());
		assertThat(holds.findBySeatIdAndStatus(booking.seatId(), HoldStatus.EXPIRED)).isEmpty();
	}

	@Test
	void onlyHoldsPastTheirExpiryAreFound() {
		Booking booking = newBooking();
		Seat otherSeat = seats.save(new Seat(booking.tenantId(), booking.eventId(), "B1", 5000));
		Instant now = Instant.now();
		SeatHold expired = holds.save(new SeatHold(booking.tenantId(), booking.seatId(), booking.userId(),
				now.minus(1, ChronoUnit.MINUTES)));
		holds.save(new SeatHold(booking.tenantId(), otherSeat.getId(), booking.userId(),
				now.plus(10, ChronoUnit.MINUTES)));

		List<SeatHold> found = holds.findByStatusAndExpiresAtBefore(HoldStatus.ACTIVE, now);

		assertThat(found).extracting(SeatHold::getId).containsExactly(expired.getId());
	}

	@Test
	void orderIsFoundByHold() {
		Booking booking = newBooking();
		SeatHold hold = holds.save(new SeatHold(booking.tenantId(), booking.seatId(), booking.userId(),
				Instant.now().plus(10, ChronoUnit.MINUTES)));
		Order order = orders.save(new Order(booking.tenantId(), booking.userId(), booking.seatId(),
				hold.getId(), 5000));

		assertThat(orders.findByHoldId(hold.getId())).get()
				.extracting(Order::getId).isEqualTo(order.getId());
		assertThat(orders.findByHoldId(UUID.randomUUID())).isEmpty();
	}

	// Waitlist

	@Test
	void waitlistKnowsWhoJoinedAnEvent() {
		Booking booking = newBooking();
		User otherBuyer = users.save(new User(booking.tenantId(), "buyer2@example.com", UserRole.BUYER));
		waitlist.save(new WaitlistEntry(booking.tenantId(), booking.eventId(), booking.userId()));

		assertThat(waitlist.existsByEventIdAndUserId(booking.eventId(), booking.userId())).isTrue();
		assertThat(waitlist.existsByEventIdAndUserId(booking.eventId(), otherBuyer.getId())).isFalse();
	}

	@Test
	void earliestWaitingEntryComesFirst() throws InterruptedException {
		Booking booking = newBooking();
		User secondBuyer = users.save(new User(booking.tenantId(), "buyer2@example.com", UserRole.BUYER));
		WaitlistEntry first = waitlist.save(
				new WaitlistEntry(booking.tenantId(), booking.eventId(), booking.userId()));
		pauseSoTimestampsDiffer();
		waitlist.save(new WaitlistEntry(booking.tenantId(), booking.eventId(), secondBuyer.getId()));

		assertThat(waitlist.findFirstByEventIdAndStatusOrderByCreatedAt(booking.eventId(), WaitlistStatus.WAITING))
				.get().extracting(WaitlistEntry::getId).isEqualTo(first.getId());
	}

	// Outbox

	@Test
	void outboxPayloadRoundTripsThroughJsonb() {
		Tenant tenant = newTenant();
		OutboxEvent event = outbox.save(new OutboxEvent(tenant.getId(), "SEAT_HELD", UUID.randomUUID(),
				"{\"seatId\": \"fake-seat-1\"}"));
		reloadFromDatabase();

		OutboxEvent stored = outbox.findById(event.getId()).orElseThrow();
		assertThat(stored.getPayload()).isEqualTo("{\"seatId\": \"fake-seat-1\"}");
		assertThat(stored.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(stored.getAttempts()).isZero();
	}

	@Test
	void outboxPayloadThatIsNotJsonIsRejected() {
		Tenant tenant = newTenant();
		outbox.save(new OutboxEvent(tenant.getId(), "SEAT_HELD", UUID.randomUUID(), "not json"));

		assertThatThrownBy(entityManager::flush)
				.satisfies(e -> assertThat(sqlStateOf(e)).isEqualTo("22P02"));
	}

	@Test
	void pendingOutboxEventsComeOldestFirst() throws InterruptedException {
		Tenant tenant = newTenant();
		OutboxEvent older = outbox.save(new OutboxEvent(tenant.getId(), "SEAT_HELD", UUID.randomUUID(), "{}"));
		pauseSoTimestampsDiffer();
		OutboxEvent newer = outbox.save(new OutboxEvent(tenant.getId(), "SEAT_HELD", UUID.randomUUID(), "{}"));

		assertThat(outbox.findTop100ByStatusOrderByCreatedAt(OutboxStatus.PENDING))
				.extracting(OutboxEvent::getId).containsExactly(older.getId(), newer.getId());
		assertThat(outbox.findTop100ByStatusOrderByCreatedAt(OutboxStatus.SENT)).isEmpty();
	}

	// Ledger: these tests commit for real, because the balance is checked at commit.

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void balancedLedgerPairIsSavedAndFoundByTransaction() {
		TransactionTemplate inTransaction = new TransactionTemplate(transactionManager);
		Tenant tenant = inTransaction.execute(status -> newTenant());
		UUID transactionId = UUID.randomUUID();

		inTransaction.executeWithoutResult(status -> ledger.saveAll(List.of(
				new LedgerEntry(tenant.getId(), transactionId, "CASH", 5000),
				new LedgerEntry(tenant.getId(), transactionId, "TICKET_REVENUE", -5000))));

		assertThat(ledger.findByTransactionId(transactionId))
				.extracting(LedgerEntry::getAmountCents).containsExactlyInAnyOrder(5000, -5000);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void unbalancedLedgerPairIsRejectedAtCommit() {
		TransactionTemplate inTransaction = new TransactionTemplate(transactionManager);
		Tenant tenant = inTransaction.execute(status -> newTenant());
		UUID transactionId = UUID.randomUUID();

		assertThatThrownBy(() -> inTransaction.executeWithoutResult(status -> ledger.saveAll(List.of(
				new LedgerEntry(tenant.getId(), transactionId, "CASH", 5000),
				new LedgerEntry(tenant.getId(), transactionId, "TICKET_REVENUE", -4000)))))
				.satisfies(e -> assertThat(sqlStateOf(e)).isEqualTo("LG002"));
		assertThat(ledger.findByTransactionId(transactionId)).isEmpty();
	}

	// Helpers

	/** One tenant with a buyer, an event and a seat, ready for holds, orders and waitlist entries. */
	private record Booking(UUID tenantId, UUID userId, UUID eventId, UUID seatId) {
	}

	private Booking newBooking() {
		Tenant tenant = newTenant();
		User buyer = users.save(new User(tenant.getId(), "buyer1@example.com", UserRole.BUYER));
		Event event = newEvent(tenant);
		Seat seat = seats.save(new Seat(tenant.getId(), event.getId(), "A1", 5000));
		return new Booking(tenant.getId(), buyer.getId(), event.getId(), seat.getId());
	}

	private Tenant newTenant() {
		return tenants.save(new Tenant("Test Tenant", "tenant-" + UUID.randomUUID()));
	}

	private Event newEvent(Tenant tenant) {
		return events.save(new Event(tenant.getId(), "Test Show", "Test Hall",
				Instant.now().plus(30, ChronoUnit.DAYS)));
	}

	/** Writes pending changes and forgets loaded objects, so the next read really comes from the database. */
	private void reloadFromDatabase() {
		entityManager.flush();
		entityManager.clear();
	}

	/** createdAt comes from the clock, so two rows made back to back could get the same time. */
	private static void pauseSoTimestampsDiffer() throws InterruptedException {
		Thread.sleep(5);
	}

	/** Finds the Postgres error code (SQLSTATE) inside the exceptions Spring wraps around it. */
	private static String sqlStateOf(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sqlException && sqlException.getSQLState() != null) {
				return sqlException.getSQLState();
			}
		}
		return null;
	}
}
