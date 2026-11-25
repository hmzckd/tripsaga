package com.hmzckd.tripsaga.booking.records;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.hmzckd.tripsaga.booking.saga.BookingState;
import com.hmzckd.tripsaga.booking.saga.BookingStatus;
import com.hmzckd.tripsaga.booking.saga.RecordActivities;

@Component
public class BookingRecords implements RecordActivities {

	private final JdbcClient jdbc;

	BookingRecords(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(BookingState state) {
		jdbc.sql("""
				INSERT INTO bookings (id, customer, flight_id, hotel_id, nights, status, total_kurus, failure)
				VALUES (:id, :customer, :flightId, :hotelId, :nights, :status, :totalKurus, :failure)
				ON CONFLICT (id) DO UPDATE
				SET status = EXCLUDED.status, total_kurus = EXCLUDED.total_kurus, failure = EXCLUDED.failure,
				    updated_at = now()
				""")
				.param("id", UUID.fromString(state.id()))
				.param("customer", state.customer())
				.param("flightId", state.flightId())
				.param("hotelId", state.hotelId())
				.param("nights", state.nights())
				.param("status", state.status().name())
				.param("totalKurus", state.totalKurus())
				.param("failure", state.failure())
				.update();
	}

	public Optional<BookingState> find(UUID id) {
		return jdbc.sql("SELECT * FROM bookings WHERE id = :id")
				.param("id", id)
				.query((rs, row) -> new BookingState(rs.getString("id"), rs.getString("customer"),
						rs.getString("flight_id"), rs.getString("hotel_id"), rs.getInt("nights"),
						BookingStatus.valueOf(rs.getString("status")), rs.getObject("total_kurus", Long.class),
						rs.getString("failure"), List.of()))
				.optional();
	}
}
