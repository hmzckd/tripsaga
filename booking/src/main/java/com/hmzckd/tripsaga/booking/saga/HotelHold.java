package com.hmzckd.tripsaga.booking.saga;

import java.util.UUID;

public record HotelHold(UUID id, String hotelId, int nights, String status, long totalKurus) {
}
