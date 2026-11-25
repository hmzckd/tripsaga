package com.hmzckd.tripsaga.booking.saga;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface BookingWorkflow {

	String TASK_QUEUE = "booking";

	static String workflowId(String bookingId) {
		return "booking-" + bookingId;
	}

	@WorkflowMethod
	BookingState book(Booking booking);

	@QueryMethod
	BookingState state();
}
