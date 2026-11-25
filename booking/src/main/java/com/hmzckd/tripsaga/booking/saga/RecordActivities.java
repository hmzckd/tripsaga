package com.hmzckd.tripsaga.booking.saga;

import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "Record")
public interface RecordActivities {

	void save(BookingState state);
}
