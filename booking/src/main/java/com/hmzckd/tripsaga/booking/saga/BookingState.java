package com.hmzckd.tripsaga.booking.saga;

import java.util.List;

public record BookingState(String id, String customer, String flightId, String hotelId, int nights,
		BookingStatus status, Long totalKurus, List<Step> steps) {
}
