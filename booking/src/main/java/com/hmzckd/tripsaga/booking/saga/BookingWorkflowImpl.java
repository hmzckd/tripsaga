package com.hmzckd.tripsaga.booking.saga;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;

public class BookingWorkflowImpl implements BookingWorkflow {

	static final ActivityOptions FORWARD = ActivityOptions.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(10))
			.setRetryOptions(RetryOptions.newBuilder()
					.setInitialInterval(Duration.ofSeconds(1))
					.setBackoffCoefficient(2)
					.setMaximumInterval(Duration.ofSeconds(10))
					.setMaximumAttempts(4)
					.build())
			.build();

	static final ActivityOptions UNTIL_DONE = ActivityOptions.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(10))
			.setRetryOptions(RetryOptions.newBuilder()
					.setInitialInterval(Duration.ofSeconds(1))
					.setBackoffCoefficient(2)
					.setMaximumInterval(Duration.ofMinutes(1))
					.setMaximumAttempts(0)
					.build())
			.build();

	private final FlightActivities flight = Workflow.newActivityStub(FlightActivities.class, FORWARD);
	private final HotelActivities hotel = Workflow.newActivityStub(HotelActivities.class, FORWARD);
	private final PaymentActivities payment = Workflow.newActivityStub(PaymentActivities.class, FORWARD);
	private final FlightActivities flightUntilDone = Workflow.newActivityStub(FlightActivities.class, UNTIL_DONE);
	private final HotelActivities hotelUntilDone = Workflow.newActivityStub(HotelActivities.class, UNTIL_DONE);
	private final PaymentActivities paymentUntilDone = Workflow.newActivityStub(PaymentActivities.class, UNTIL_DONE);
	private final RecordActivities records = Workflow.newActivityStub(RecordActivities.class, UNTIL_DONE);

	private final List<Step> steps = new ArrayList<>();
	private final Deque<Compensation> compensations = new ArrayDeque<>();
	private Booking booking;
	private BookingStatus status = BookingStatus.STARTED;
	private Long totalKurus;
	private String failure;

	@Override
	public BookingState book(Booking booking) {
		this.booking = booking;
		String action = null;
		FlightHold seat;
		HotelHold room;
		try {
			action = "hold seat on " + booking.flightId();
			compensations.push(new Compensation("cancel seat on " + booking.flightId(),
					() -> flightUntilDone.cancel(key("flight-hold"))));
			seat = flight.hold(key("flight-hold"), booking.flightId(), booking.customer());
			advance(BookingStatus.FLIGHT_HELD, action, seat.status());

			action = "hold room at " + booking.hotelId();
			compensations.push(new Compensation("cancel room at " + booking.hotelId(),
					() -> hotelUntilDone.cancel(key("hotel-hold"))));
			room = hotel.hold(key("hotel-hold"), booking.hotelId(), booking.customer(), booking.nights());
			advance(BookingStatus.HOTEL_HELD, action, room.status());

			totalKurus = seat.priceKurus() + room.totalKurus();
			action = "charge " + totalKurus + " kurus";
			compensations.push(new Compensation("refund payment", () -> paymentUntilDone.refund(key("payment"))));
			Charge charge = payment.charge(key("payment"), booking.customer(), totalKurus, booking.cardToken());
			advance(BookingStatus.PAYMENT_CAPTURED, action, charge.status());
		}
		catch (ActivityFailure ex) {
			failure = reason(ex);
			steps.add(new Step(action, "FAILED: " + failure));
			compensate();
			return finish();
		}

		boolean confirmed = attempt("confirm seat on " + seat.flightId(), () -> flightUntilDone.confirm(seat.id()))
				& attempt("confirm room at " + room.hotelId(), () -> hotelUntilDone.confirm(room.id()));
		status = confirmed ? BookingStatus.CONFIRMED : BookingStatus.NEEDS_ATTENTION;
		return finish();
	}

	@Override
	public BookingState state() {
		return new BookingState(booking.id(), booking.customer(), booking.flightId(), booking.hotelId(),
				booking.nights(), status, totalKurus, failure, List.copyOf(steps));
	}

	private void compensate() {
		status = BookingStatus.COMPENSATING;
		boolean allUndone = true;
		while (!compensations.isEmpty()) {
			Compensation compensation = compensations.pop();
			allUndone &= attempt(compensation.action(), compensation.undo());
		}
		status = allUndone ? BookingStatus.CANCELLED : BookingStatus.NEEDS_ATTENTION;
	}

	private boolean attempt(String action, Runnable call) {
		try {
			call.run();
			steps.add(new Step(action, "DONE"));
			return true;
		}
		catch (ActivityFailure ex) {
			steps.add(new Step(action, "FAILED: " + reason(ex)));
			return false;
		}
	}

	private BookingState finish() {
		records.save(state());
		return state();
	}

	private String key(String step) {
		return booking.id() + "-" + step;
	}

	private void advance(BookingStatus next, String action, String outcome) {
		steps.add(new Step(action, outcome));
		status = next;
	}

	private static String reason(ActivityFailure ex) {
		if (ex.getCause() instanceof ApplicationFailure application) {
			return application.getOriginalMessage();
		}
		return ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
	}

	private record Compensation(String action, Runnable undo) {
	}
}
