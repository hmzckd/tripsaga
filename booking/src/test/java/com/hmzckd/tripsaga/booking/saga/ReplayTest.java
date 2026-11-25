package com.hmzckd.tripsaga.booking.saga;

import java.io.File;

import org.junit.jupiter.api.Test;

import io.temporal.testing.WorkflowReplayer;

class ReplayTest {

	@Test
	void bookingsStartedBeforeRecordOnStartStillReplay() throws Exception {
		WorkflowReplayer.replayWorkflowExecution(
				new File("src/test/resources/histories/booking-before-record-on-start.json"), BookingWorkflowImpl.class);
	}
}
