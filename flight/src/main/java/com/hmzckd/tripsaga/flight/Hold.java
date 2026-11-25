package com.hmzckd.tripsaga.flight;

import java.util.UUID;

public record Hold(UUID id, String flightId, String passenger, HoldStatus status, Long priceKurus) {
}
