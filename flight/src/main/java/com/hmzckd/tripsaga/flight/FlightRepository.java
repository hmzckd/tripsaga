package com.hmzckd.tripsaga.flight;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class FlightRepository {

	private static final String SELECT_HOLD = """
			SELECT h.id, h.flight_id, h.passenger, h.status, f.price_kurus
			FROM holds h JOIN flights f ON f.id = h.flight_id
			""";

	private final JdbcClient jdbc;

	FlightRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<Flight> findAllFlights() {
		return jdbc.sql("SELECT * FROM flights ORDER BY departs_at").query(Flight.class).list();
	}

	Optional<Flight> findFlight(String id) {
		return jdbc.sql("SELECT * FROM flights WHERE id = :id").param("id", id).query(Flight.class).optional();
	}

	boolean takeSeat(String flightId) {
		return jdbc.sql("UPDATE flights SET seats_free = seats_free - 1 WHERE id = :id AND seats_free > 0")
				.param("id", flightId)
				.update() == 1;
	}

	void releaseSeat(String flightId) {
		jdbc.sql("UPDATE flights SET seats_free = seats_free + 1 WHERE id = :id")
				.param("id", flightId)
				.update();
	}

	void insertHold(UUID id, String flightId, String passenger) {
		jdbc.sql("INSERT INTO holds (id, flight_id, passenger, status) VALUES (:id, :flightId, :passenger, 'HELD')")
				.param("id", id)
				.param("flightId", flightId)
				.param("passenger", passenger)
				.update();
	}

	Optional<Hold> findHold(UUID id) {
		return jdbc.sql(SELECT_HOLD + " WHERE h.id = :id").param("id", id).query(Hold.class).optional();
	}

	boolean changeStatus(UUID id, HoldStatus from, HoldStatus to) {
		return jdbc.sql("""
				UPDATE holds SET status = :to, updated_at = now()
				WHERE id = :id AND status = :from
				""")
				.param("id", id)
				.param("from", from.name())
				.param("to", to.name())
				.update() == 1;
	}
}
