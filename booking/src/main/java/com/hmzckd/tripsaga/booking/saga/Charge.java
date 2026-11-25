package com.hmzckd.tripsaga.booking.saga;

import java.util.UUID;

public record Charge(UUID id, long amountKurus, String status) {
}
