package com.hmzckd.tripsaga.booking.saga;

public record Booking(String id, String customer, String flightId, String hotelId, int nights, String cardToken) {
}
