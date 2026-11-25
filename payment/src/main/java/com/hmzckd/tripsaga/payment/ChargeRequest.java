package com.hmzckd.tripsaga.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ChargeRequest(@NotBlank String customer, @Positive long amountKurus, @NotBlank String cardToken) {
}
