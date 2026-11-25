package com.hmzckd.tripsaga.booking.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;

class BookingWorkflowTest {

	static final UUID SEAT = UUID.randomUUID();
	static final UUID ROOM = UUID.randomUUID();

	TestWorkflowEnvironment env;
	FlightActivities flight = mock(FlightActivities.class, withSettings().withoutAnnotations());
	HotelActivities hotel = mock(HotelActivities.class, withSettings().withoutAnnotations());
	PaymentActivities payment = mock(PaymentActivities.class, withSettings().withoutAnnotations());
	RecordActivities records = mock(RecordActivities.class, withSettings().withoutAnnotations());

	@BeforeEach
	void setUp() {
		env = TestWorkflowEnvironment.newInstance();
		Worker worker = env.newWorker(BookingWorkflow.TASK_QUEUE);
		worker.registerWorkflowImplementationTypes(BookingWorkflowImpl.class);
		worker.registerActivitiesImplementations(flight, hotel, payment, records);
		env.start();
	}

	@AfterEach
	void tearDown() {
		env.close();
	}

	@Test
	void holdsBothThenChargesTheSumThenConfirms() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenReturn(new FlightHold(SEAT, "TK2412", "HELD", 245000));
		when(hotel.hold(anyString(), anyString(), anyString(), anyInt()))
				.thenReturn(new HotelHold(ROOM, "AYT-LARA", 3, "HELD", 960000));
		when(payment.charge(anyString(), anyString(), anyLong(), anyString()))
				.thenReturn(new Charge(UUID.randomUUID(), 1205000, "CAPTURED"));

		BookingState state = book(new Booking("b1", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_ok"));

		assertThat(state.status()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(state.totalKurus()).isEqualTo(1205000);
		assertThat(state.steps()).extracting(Step::outcome).containsExactly("HELD", "HELD", "CAPTURED", "CONFIRMED");

		InOrder order = inOrder(flight, hotel, payment, records);
		order.verify(flight).hold("b1-flight-hold", "TK2412", "Ayşe Yılmaz");
		order.verify(hotel).hold("b1-hotel-hold", "AYT-LARA", "Ayşe Yılmaz", 3);
		order.verify(payment).charge("b1-payment", "Ayşe Yılmaz", 1205000, "tok_ok");
		order.verify(flight).confirm(SEAT);
		order.verify(hotel).confirm(ROOM);
		order.verify(records).save(state);
	}

	@Test
	void theStateCanBeQueriedAfterTheSagaFinished() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenReturn(new FlightHold(SEAT, "TK2412", "HELD", 245000));
		when(hotel.hold(anyString(), anyString(), anyString(), anyInt()))
				.thenReturn(new HotelHold(ROOM, "AYT-LARA", 1, "HELD", 320000));
		when(payment.charge(anyString(), anyString(), anyLong(), anyString()))
				.thenReturn(new Charge(UUID.randomUUID(), 565000, "CAPTURED"));

		book(new Booking("b2", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 1, "tok_ok"));

		BookingWorkflow finished = env.getWorkflowClient()
				.newWorkflowStub(BookingWorkflow.class, BookingWorkflow.workflowId("b2"));
		assertThat(finished.state().status()).isEqualTo(BookingStatus.CONFIRMED);
		verify(records).save(finished.state());
	}

	private BookingState book(Booking booking) {
		WorkflowClient client = env.getWorkflowClient();
		BookingWorkflow saga = client.newWorkflowStub(BookingWorkflow.class, WorkflowOptions.newBuilder()
				.setWorkflowId(BookingWorkflow.workflowId(booking.id()))
				.setTaskQueue(BookingWorkflow.TASK_QUEUE)
				.build());
		return saga.book(booking);
	}
}
