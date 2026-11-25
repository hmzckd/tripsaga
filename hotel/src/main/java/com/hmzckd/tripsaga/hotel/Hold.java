package com.hmzckd.tripsaga.hotel;

import java.util.UUID;

public record Hold(UUID id, String hotelId, String guest, Integer nights, HoldStatus status, Long totalKurus) {
}
