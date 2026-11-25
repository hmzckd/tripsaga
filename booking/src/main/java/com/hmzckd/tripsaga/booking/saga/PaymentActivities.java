package com.hmzckd.tripsaga.booking.saga;

import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "Payment")
public interface PaymentActivities {

	Charge charge(String idempotencyKey, String customer, long amountKurus, String cardToken);
}
