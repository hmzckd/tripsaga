package com.hmzckd.tripsaga.booking.saga;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;

public class BookingWorkflowImpl implements BookingWorkflow {

	private static final ActivityOptions CALLS = ActivityOptions.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(10))
			.build();

	private final FlightActivities flight = Workflow.newActivityStub(FlightActivities.class, CALLS);
	private final HotelActivities hotel = Workflow.newActivityStub(HotelActivities.class, CALLS);
	private final PaymentActivities payment = Workflow.newActivityStub(PaymentActivities.class, CALLS);
	private final RecordActivities records = Workflow.newActivityStub(RecordActivities.class, CALLS);

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
		try {
			action = "hold seat on " + booking.flightId();
			FlightHold seat = flight.hold(key("flight-hold"), booking.flightId(), booking.customer());
			compensations.push(new Compensation("cancel seat on " + seat.flightId(), () -> flight.cancel(seat.id())));
			advance(BookingStatus.FLIGHT_HELD, action, seat.status());

			action = "hold room at " + booking.hotelId();
			HotelHold room = hotel.hold(key("hotel-hold"), booking.hotelId(), booking.customer(), booking.nights());
			compensations.push(new Compensation("cancel room at " + room.hotelId(), () -> hotel.cancel(room.id())));
			advance(BookingStatus.HOTEL_HELD, action, room.status());

			totalKurus = seat.priceKurus() + room.totalKurus();
			action = "charge " + totalKurus + " kurus";
			Charge charge = payment.charge(key("payment"), booking.customer(), totalKurus, booking.cardToken());
			advance(BookingStatus.PAYMENT_CAPTURED, action, charge.status());

			flight.confirm(seat.id());
			hotel.confirm(room.id());
			advance(BookingStatus.CONFIRMED, "confirm seat and room", "CONFIRMED");
		}
		catch (ActivityFailure ex) {
			failure = reason(ex);
			steps.add(new Step(action, "FAILED: " + failure));
			compensate();
		}

		records.save(state());
		return state();
	}

	@Override
	public BookingState state() {
		return new BookingState(booking.id(), booking.customer(), booking.flightId(), booking.hotelId(),
				booking.nights(), status, totalKurus, failure, List.copyOf(steps));
	}

	private void compensate() {
		status = BookingStatus.COMPENSATING;
		while (!compensations.isEmpty()) {
			Compensation compensation = compensations.pop();
			compensation.undo().run();
			steps.add(new Step(compensation.action(), "DONE"));
		}
		status = BookingStatus.CANCELLED;
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
