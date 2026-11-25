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

	static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(15);
	static final Duration PAYMENT_CHECK_INTERVAL = Duration.ofSeconds(10);

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

	private static final String PENDING = "PENDING";
	private static final String CAPTURED = "CAPTURED";

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
	private String cancelReason;
	private boolean paid;

	@Override
	public BookingState book(Booking booking) {
		this.booking = booking;
		String action = null;
		FlightHold seat;
		HotelHold room;
		try {
			stopIfCancelled();
			action = "hold seat on " + booking.flightId();
			compensations.push(new Compensation("cancel seat on " + booking.flightId(),
					() -> flightUntilDone.cancel(key("flight-hold"))));
			seat = flight.hold(key("flight-hold"), booking.flightId(), booking.customer());
			advance(BookingStatus.FLIGHT_HELD, action, seat.status());
			stopIfCancelled();

			action = "hold room at " + booking.hotelId();
			compensations.push(new Compensation("cancel room at " + booking.hotelId(),
					() -> hotelUntilDone.cancel(key("hotel-hold"))));
			room = hotel.hold(key("hotel-hold"), booking.hotelId(), booking.customer(), booking.nights());
			advance(BookingStatus.HOTEL_HELD, action, room.status());
			stopIfCancelled();

			totalKurus = seat.priceKurus() + room.totalKurus();
			action = "charge " + totalKurus + " kurus";
			compensations.push(new Compensation("refund payment", () -> paymentUntilDone.refund(key("payment"))));
			Charge charge = payment.charge(key("payment"), booking.customer(), totalKurus, booking.cardToken());
			if (PENDING.equals(charge.status())) {
				advance(BookingStatus.PAYMENT_PENDING, action, PENDING);
				action = "wait for the payment";
				charge = waitForPayment(charge);
			}
			paid = true;
			advance(BookingStatus.PAYMENT_CAPTURED, action, charge.status());
		}
		catch (ActivityFailure ex) {
			failure = reason(ex);
			steps.add(new Step(action, "FAILED: " + failure));
			compensate();
			return finish();
		}
		catch (SagaStopped ex) {
			failure = ex.getMessage();
			compensate();
			return finish();
		}
		if (cancelReason != null) {
			steps.add(new Step("cancel request", "IGNORED: the payment was already captured"));
		}

		boolean confirmed = attempt("confirm seat on " + seat.flightId(), () -> flightUntilDone.confirm(seat.id()))
				& attempt("confirm room at " + room.hotelId(), () -> hotelUntilDone.confirm(room.id()));
		status = confirmed ? BookingStatus.CONFIRMED : BookingStatus.NEEDS_ATTENTION;
		return finish();
	}

	@Override
	public void cancel(String reason) {
		if (paid) {
			steps.add(new Step("cancel request", "IGNORED: the payment was already captured"));
			return;
		}
		if (cancelReason == null) {
			cancelReason = reason == null || reason.isBlank() ? "no reason given" : reason;
		}
	}

	@Override
	public BookingState state() {
		return new BookingState(booking.id(), booking.customer(), booking.flightId(), booking.hotelId(),
				booking.nights(), status, totalKurus, failure, List.copyOf(steps));
	}

	private Charge waitForPayment(Charge pending) {
		long deadline = Workflow.currentTimeMillis() + PAYMENT_TIMEOUT.toMillis();
		Charge charge = pending;
		while (PENDING.equals(charge.status())) {
			long left = deadline - Workflow.currentTimeMillis();
			if (left <= 0) {
				String timeout = PAYMENT_TIMEOUT.toMinutes() + " minutes";
				steps.add(new Step("wait for the payment", "TIMED OUT after " + timeout));
				throw new SagaStopped("the payment was not completed within " + timeout);
			}
			Workflow.await(Duration.ofMillis(Math.min(left, PAYMENT_CHECK_INTERVAL.toMillis())),
					() -> cancelReason != null);
			stopIfCancelled();
			charge = payment.status(pending.id());
		}
		if (!CAPTURED.equals(charge.status())) {
			steps.add(new Step("wait for the payment", charge.status()));
			throw new SagaStopped("the payment ended as " + charge.status());
		}
		return charge;
	}

	private void stopIfCancelled() {
		if (cancelReason != null) {
			steps.add(new Step("cancel request", "ACCEPTED: " + cancelReason));
			throw new SagaStopped("cancelled by the customer: " + cancelReason);
		}
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

	private static final class SagaStopped extends RuntimeException {

		SagaStopped(String reason) {
			super(reason, null, false, false);
		}
	}
}
