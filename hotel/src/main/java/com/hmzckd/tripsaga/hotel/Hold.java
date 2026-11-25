package com.hmzckd.tripsaga.hotel;

import java.util.UUID;

public record Hold(UUID id, String hotelId, String guest, int nights, HoldStatus status, long totalKurus) {
}
