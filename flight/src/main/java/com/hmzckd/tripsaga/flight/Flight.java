package com.hmzckd.tripsaga.flight;

import java.time.OffsetDateTime;

public record Flight(String id, String origin, String destination, OffsetDateTime departsAt, int seatsTotal,
		int seatsFree, long priceKurus) {
}
