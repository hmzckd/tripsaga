package com.hmzckd.tripsaga.booking.saga;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.temporal.activity.ActivityOptions;
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
	private Booking booking;
	private BookingStatus status = BookingStatus.STARTED;
	private Long totalKurus;

	@Override
	public BookingState book(Booking booking) {
		this.booking = booking;

		FlightHold seat = flight.hold(key("flight-hold"), booking.flightId(), booking.customer());
		advance(BookingStatus.FLIGHT_HELD, "hold seat on " + seat.flightId(), seat.status());

		HotelHold room = hotel.hold(key("hotel-hold"), booking.hotelId(), booking.customer(), booking.nights());
		advance(BookingStatus.HOTEL_HELD, "hold room at " + room.hotelId(), room.status());

		totalKurus = seat.priceKurus() + room.totalKurus();
		Charge charge = payment.charge(key("payment"), booking.customer(), totalKurus, booking.cardToken());
		advance(BookingStatus.PAYMENT_CAPTURED, "charge " + totalKurus + " kurus", charge.status());

		flight.confirm(seat.id());
		hotel.confirm(room.id());
		advance(BookingStatus.CONFIRMED, "confirm seat and room", "CONFIRMED");

		records.save(state());
		return state();
	}

	@Override
	public BookingState state() {
		return new BookingState(booking.id(), booking.customer(), booking.flightId(), booking.hotelId(),
				booking.nights(), status, totalKurus, List.copyOf(steps));
	}

	private String key(String step) {
		return booking.id() + "-" + step;
	}

	private void advance(BookingStatus next, String action, String outcome) {
		steps.add(new Step(action, outcome));
		status = next;
	}
}
