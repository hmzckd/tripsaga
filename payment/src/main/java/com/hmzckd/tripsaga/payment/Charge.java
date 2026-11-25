package com.hmzckd.tripsaga.payment;

import java.util.UUID;

public record Charge(UUID id, String customer, Long amountKurus, ChargeStatus status) {
}
