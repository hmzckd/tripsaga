package com.hmzckd.tripsaga.booking.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.ApplicationFailure;
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
		assertThat(state.steps()).extracting(Step::outcome).containsExactly("HELD", "HELD", "CAPTURED", "DONE", "DONE");

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

	@Test
	void aFullHotelCancelsTheSeatAndNeverCharges() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenReturn(new FlightHold(SEAT, "TK2412", "HELD", 245000));
		when(hotel.hold(anyString(), anyString(), anyString(), anyInt()))
				.thenThrow(rejected("Hotel BJV-DOLU has no free rooms", 409));

		BookingState state = book(new Booking("b3", "Ayşe Yılmaz", "TK2412", "BJV-DOLU", 3, "tok_ok"));

		assertThat(state.status()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(state.failure()).isEqualTo("Hotel BJV-DOLU has no free rooms");
		assertThat(state.steps()).containsExactly(
				new Step("hold seat on TK2412", "HELD"),
				new Step("hold room at BJV-DOLU", "FAILED: Hotel BJV-DOLU has no free rooms"),
				new Step("cancel room at BJV-DOLU", "DONE"),
				new Step("cancel seat on TK2412", "DONE"));
		verify(flight).cancel("b3-flight-hold");
		verify(payment, never()).charge(anyString(), anyString(), anyLong(), anyString());
		verify(flight, never()).confirm(SEAT);
		verify(records).save(state);
	}

	@Test
	void aDeclinedCardCancelsTheRoomThenTheSeat() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenReturn(new FlightHold(SEAT, "TK2412", "HELD", 245000));
		when(hotel.hold(anyString(), anyString(), anyString(), anyInt()))
				.thenReturn(new HotelHold(ROOM, "AYT-LARA", 3, "HELD", 960000));
		when(payment.charge(anyString(), anyString(), anyLong(), anyString()))
				.thenThrow(rejected("The card was declined", 402));

		BookingState state = book(new Booking("b4", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_declined"));

		assertThat(state.status()).isEqualTo(BookingStatus.CANCELLED);
		InOrder undo = inOrder(payment, hotel, flight);
		undo.verify(payment).refund("b4-payment");
		undo.verify(hotel).cancel("b4-hotel-hold");
		undo.verify(flight).cancel("b4-flight-hold");
		verify(flight, never()).confirm(SEAT);
		verify(hotel, never()).confirm(ROOM);
	}

	@Test
	void aSoldOutFlightTouchesNothingElse() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenThrow(rejected("Flight XQ0000 has no free seats", 409));

		BookingState state = book(new Booking("b5", "Ayşe Yılmaz", "XQ0000", "AYT-LARA", 3, "tok_ok"));

		assertThat(state.status()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(state.steps()).hasSize(2);
		verify(hotel, never()).hold(anyString(), anyString(), anyString(), anyInt());
		verify(hotel, never()).cancel(anyString());
		verify(flight).cancel("b5-flight-hold");
	}

	@Test
	void aShortOutageIsRetriedAndTheBookingStillSucceeds() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenThrow(new IllegalStateException("503 Service Unavailable"))
				.thenThrow(new IllegalStateException("503 Service Unavailable"))
				.thenReturn(new FlightHold(SEAT, "TK2412", "HELD", 245000));
		when(hotel.hold(anyString(), anyString(), anyString(), anyInt()))
				.thenReturn(new HotelHold(ROOM, "AYT-LARA", 3, "HELD", 960000));
		when(payment.charge(anyString(), anyString(), anyLong(), anyString()))
				.thenReturn(new Charge(UUID.randomUUID(), 1205000, "CAPTURED"));

		BookingState state = book(new Booking("b6", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_ok"));

		assertThat(state.status()).isEqualTo(BookingStatus.CONFIRMED);
		verify(flight, times(3)).hold("b6-flight-hold", "TK2412", "Ayşe Yılmaz");
	}

	@Test
	void aLongOutageGivesUpAfterFourAttemptsAndCompensates() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenReturn(new FlightHold(SEAT, "TK2412", "HELD", 245000));
		when(hotel.hold(anyString(), anyString(), anyString(), anyInt()))
				.thenThrow(new IllegalStateException("503 Service Unavailable"));

		BookingState state = book(new Booking("b7", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_ok"));

		assertThat(state.status()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(state.failure()).isEqualTo("503 Service Unavailable");
		verify(hotel, times(4)).hold("b7-hotel-hold", "AYT-LARA", "Ayşe Yılmaz", 3);
		verify(hotel).cancel("b7-hotel-hold");
		verify(flight).cancel("b7-flight-hold");
	}

	@Test
	void aCompensationIsRetriedUntilItSucceeds() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenReturn(new FlightHold(SEAT, "TK2412", "HELD", 245000));
		when(hotel.hold(anyString(), anyString(), anyString(), anyInt()))
				.thenThrow(rejected("Hotel BJV-DOLU has no free rooms", 409));
		IllegalStateException outage = new IllegalStateException("503 Service Unavailable");
		doThrow(outage, outage, outage, outage, outage).doNothing().when(flight).cancel("b8-flight-hold");

		BookingState state = book(new Booking("b8", "Ayşe Yılmaz", "TK2412", "BJV-DOLU", 3, "tok_ok"));

		assertThat(state.status()).isEqualTo(BookingStatus.CANCELLED);
		verify(flight, times(6)).cancel("b8-flight-hold");
	}

	@Test
	void aRejectedConfirmAfterPaymentNeedsAttentionAndUndoesNothing() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenReturn(new FlightHold(SEAT, "TK2412", "HELD", 245000));
		when(hotel.hold(anyString(), anyString(), anyString(), anyInt()))
				.thenReturn(new HotelHold(ROOM, "AYT-LARA", 3, "HELD", 960000));
		when(payment.charge(anyString(), anyString(), anyLong(), anyString()))
				.thenReturn(new Charge(UUID.randomUUID(), 1205000, "CAPTURED"));
		doThrow(rejected("Hold " + SEAT + " is CANCELLED and cannot be confirmed", 409)).when(flight).confirm(SEAT);

		BookingState state = book(new Booking("b9", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_ok"));

		assertThat(state.status()).isEqualTo(BookingStatus.NEEDS_ATTENTION);
		verify(hotel).confirm(ROOM);
		verify(hotel, never()).cancel(anyString());
		verify(flight, never()).cancel(anyString());
		verify(payment, never()).refund(anyString());
	}

	@Test
	void aSeatWhoseAnswerWasLostIsStillCancelled() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenThrow(new IllegalStateException("503 the request was processed but the answer was lost"));

		BookingState state = book(new Booking("b10", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_ok"));

		assertThat(state.status()).isEqualTo(BookingStatus.CANCELLED);
		verify(flight).cancel("b10-flight-hold");
	}

	@Test
	void aChargeWhoseAnswerWasLostIsRefundedAndTheHoldsAreCancelled() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenReturn(new FlightHold(SEAT, "TK2412", "HELD", 245000));
		when(hotel.hold(anyString(), anyString(), anyString(), anyInt()))
				.thenReturn(new HotelHold(ROOM, "AYT-LARA", 3, "HELD", 960000));
		when(payment.charge(anyString(), anyString(), anyLong(), anyString()))
				.thenThrow(new IllegalStateException("503 the request was processed but the answer was lost"));

		BookingState state = book(new Booking("b11", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_ok"));

		assertThat(state.status()).isEqualTo(BookingStatus.CANCELLED);
		InOrder undo = inOrder(payment, hotel, flight);
		undo.verify(payment).refund("b11-payment");
		undo.verify(hotel).cancel("b11-hotel-hold");
		undo.verify(flight).cancel("b11-flight-hold");
	}

	@Test
	void aCancelDuringTheRoomHoldUndoesBothHoldsAndNeverCharges() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenReturn(new FlightHold(SEAT, "TK2412", "HELD", 245000));
		when(hotel.hold(anyString(), anyString(), anyString(), anyInt())).thenAnswer(call -> {
			saga("b12").cancel("changed my plans");
			return new HotelHold(ROOM, "AYT-LARA", 3, "HELD", 960000);
		});

		BookingState state = book(new Booking("b12", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_ok"));

		assertThat(state.status()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(state.failure()).isEqualTo("cancelled by the customer: changed my plans");
		assertThat(state.steps()).contains(new Step("cancel request", "ACCEPTED: changed my plans"));
		verify(payment, never()).charge(anyString(), anyString(), anyLong(), anyString());
		verify(hotel).cancel("b12-hotel-hold");
		verify(flight).cancel("b12-flight-hold");
	}

	@Test
	void aCancelAfterThePaymentIsIgnored() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenReturn(new FlightHold(SEAT, "TK2412", "HELD", 245000));
		when(hotel.hold(anyString(), anyString(), anyString(), anyInt()))
				.thenReturn(new HotelHold(ROOM, "AYT-LARA", 3, "HELD", 960000));
		when(payment.charge(anyString(), anyString(), anyLong(), anyString())).thenAnswer(call -> {
			saga("b13").cancel("too late");
			return new Charge(UUID.randomUUID(), 1205000, "CAPTURED");
		});

		BookingState state = book(new Booking("b13", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_ok"));

		assertThat(state.status()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(state.steps()).contains(new Step("cancel request", "IGNORED: the payment was already captured"));
		verify(flight, never()).cancel(anyString());
	}

	@Test
	void aPendingPaymentIsCheckedUntilTheBankApproves() {
		UUID chargeId = UUID.randomUUID();
		holdsSucceed();
		when(payment.charge(anyString(), anyString(), anyLong(), anyString()))
				.thenReturn(new Charge(chargeId, 1205000, "PENDING"));
		when(payment.status(chargeId))
				.thenReturn(new Charge(chargeId, 1205000, "PENDING"))
				.thenReturn(new Charge(chargeId, 1205000, "PENDING"))
				.thenReturn(new Charge(chargeId, 1205000, "CAPTURED"));

		BookingState state = book(new Booking("b14", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_pending"));

		assertThat(state.status()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(state.steps()).contains(new Step("charge 1205000 kurus", "PENDING"),
				new Step("wait for the payment", "CAPTURED"));
		verify(payment, times(3)).status(chargeId);
	}

	@Test
	void aPaymentThatNeverCompletesTimesOutAfterFifteenMinutesAndIsVoided() {
		UUID chargeId = UUID.randomUUID();
		holdsSucceed();
		when(payment.charge(anyString(), anyString(), anyLong(), anyString()))
				.thenReturn(new Charge(chargeId, 1205000, "PENDING"));
		when(payment.status(chargeId)).thenReturn(new Charge(chargeId, 1205000, "PENDING"));
		long started = env.currentTimeMillis();

		BookingState state = book(new Booking("b15", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_pending"));

		assertThat(state.status()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(state.failure()).isEqualTo("the payment was not completed within 15 minutes");
		assertThat(Duration.ofMillis(env.currentTimeMillis() - started)).isGreaterThanOrEqualTo(Duration.ofMinutes(15));
		InOrder undo = inOrder(payment, hotel, flight);
		undo.verify(payment).refund("b15-payment");
		undo.verify(hotel).cancel("b15-hotel-hold");
		undo.verify(flight).cancel("b15-flight-hold");
	}

	@Test
	void aPaymentDeclinedByTheBankLaterIsCompensated() {
		UUID chargeId = UUID.randomUUID();
		holdsSucceed();
		when(payment.charge(anyString(), anyString(), anyLong(), anyString()))
				.thenReturn(new Charge(chargeId, 1205000, "PENDING"));
		when(payment.status(chargeId)).thenReturn(new Charge(chargeId, 1205000, "DECLINED"));

		BookingState state = book(new Booking("b16", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_pending"));

		assertThat(state.status()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(state.failure()).isEqualTo("the payment ended as DECLINED");
	}

	@Test
	void aCustomerCanCancelWhileThePaymentIsPending() {
		UUID chargeId = UUID.randomUUID();
		holdsSucceed();
		when(payment.charge(anyString(), anyString(), anyLong(), anyString()))
				.thenReturn(new Charge(chargeId, 1205000, "PENDING"));
		when(payment.status(chargeId)).thenReturn(new Charge(chargeId, 1205000, "PENDING"));
		BookingWorkflow saga = env.getWorkflowClient().newWorkflowStub(BookingWorkflow.class,
				WorkflowOptions.newBuilder()
						.setWorkflowId(BookingWorkflow.workflowId("b17"))
						.setTaskQueue(BookingWorkflow.TASK_QUEUE)
						.build());
		WorkflowClient.start(saga::book, new Booking("b17", "Ayşe Yılmaz", "TK2412", "AYT-LARA", 3, "tok_pending"));
		await().atMost(Duration.ofSeconds(5))
				.until(() -> saga.state().status() == BookingStatus.PAYMENT_PENDING);

		saga.cancel("found a cheaper trip");
		BookingState state = WorkflowStub.fromTyped(saga).getResult(BookingState.class);

		assertThat(state.status()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(state.failure()).isEqualTo("cancelled by the customer: found a cheaper trip");
		verify(payment).refund("b17-payment");
	}

	private void holdsSucceed() {
		when(flight.hold(anyString(), anyString(), anyString()))
				.thenReturn(new FlightHold(SEAT, "TK2412", "HELD", 245000));
		when(hotel.hold(anyString(), anyString(), anyString(), anyInt()))
				.thenReturn(new HotelHold(ROOM, "AYT-LARA", 3, "HELD", 960000));
	}

	private BookingWorkflow saga(String bookingId) {
		return env.getWorkflowClient().newWorkflowStub(BookingWorkflow.class, BookingWorkflow.workflowId(bookingId));
	}

	private static ApplicationFailure rejected(String message, int status) {
		return ApplicationFailure.newNonRetryableFailure(message, "ParticipantRejected", status);
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
