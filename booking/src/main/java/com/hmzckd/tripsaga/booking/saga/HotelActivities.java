package com.hmzckd.tripsaga.booking.saga;

import java.util.UUID;

import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "Hotel")
public interface HotelActivities {

	HotelHold hold(String idempotencyKey, String hotelId, String guest, int nights);

	void confirm(UUID holdId);

	void cancel(String holdIdempotencyKey);
}
