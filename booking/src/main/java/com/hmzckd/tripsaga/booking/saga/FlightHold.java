package com.hmzckd.tripsaga.booking.saga;

import java.util.UUID;

public record FlightHold(UUID id, String flightId, String status, long priceKurus) {
}
