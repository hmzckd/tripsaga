package com.hmzckd.tripsaga.hotel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class HotelRepository {

	private static final String SELECT_HOLD = """
			SELECT h.id, h.hotel_id, h.guest, h.nights, h.status, h.nights * o.price_per_night_kurus AS total_kurus
			FROM holds h LEFT JOIN hotels o ON o.id = h.hotel_id
			""";

	private final JdbcClient jdbc;

	HotelRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<Hotel> findAllHotels() {
		return jdbc.sql("SELECT * FROM hotels ORDER BY id").query(Hotel.class).list();
	}

	Optional<Hotel> findHotel(String id) {
		return jdbc.sql("SELECT * FROM hotels WHERE id = :id").param("id", id).query(Hotel.class).optional();
	}

	boolean takeRoom(String hotelId) {
		return jdbc.sql("UPDATE hotels SET rooms_free = rooms_free - 1 WHERE id = :id AND rooms_free > 0")
				.param("id", hotelId)
				.update() == 1;
	}

	void releaseRoom(String hotelId) {
		jdbc.sql("UPDATE hotels SET rooms_free = rooms_free + 1 WHERE id = :id")
				.param("id", hotelId)
				.update();
	}

	boolean insertHold(UUID id, String idempotencyKey, HoldRequest request) {
		return jdbc.sql("""
				INSERT INTO holds (id, idempotency_key, hotel_id, guest, nights, status)
				VALUES (:id, :key, :hotelId, :guest, :nights, 'HELD')
				ON CONFLICT (idempotency_key) DO NOTHING
				""")
				.param("id", id)
				.param("key", idempotencyKey)
				.param("hotelId", request.hotelId())
				.param("guest", request.guest())
				.param("nights", request.nights())
				.update() == 1;
	}

	boolean insertTombstone(UUID id, String idempotencyKey) {
		return jdbc.sql("""
				INSERT INTO holds (id, idempotency_key, status)
				VALUES (:id, :key, 'CANCELLED')
				ON CONFLICT (idempotency_key) DO NOTHING
				""")
				.param("id", id)
				.param("key", idempotencyKey)
				.update() == 1;
	}

	Optional<Hold> findHoldByKey(String idempotencyKey) {
		return jdbc.sql(SELECT_HOLD + " WHERE h.idempotency_key = :key")
				.param("key", idempotencyKey)
				.query(Hold.class)
				.optional();
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
