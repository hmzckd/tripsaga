package com.hmzckd.tripsaga.booking.saga;

import java.util.UUID;

import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "Flight")
public interface FlightActivities {

	FlightHold hold(String idempotencyKey, String flightId, String passenger);

	void confirm(UUID holdId);

	void cancel(String holdIdempotencyKey);
}
