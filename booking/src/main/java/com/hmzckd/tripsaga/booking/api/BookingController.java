package com.hmzckd.tripsaga.booking.api;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hmzckd.tripsaga.booking.records.BookingRecords;
import com.hmzckd.tripsaga.booking.saga.Booking;
import com.hmzckd.tripsaga.booking.saga.BookingState;
import com.hmzckd.tripsaga.booking.saga.BookingStatus;
import com.hmzckd.tripsaga.booking.saga.BookingWorkflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;

@RestController
@RequestMapping("/api/bookings")
class BookingController {

	private static final Set<BookingStatus> CANCELLABLE = EnumSet.of(BookingStatus.STARTED, BookingStatus.FLIGHT_HELD,
			BookingStatus.HOTEL_HELD, BookingStatus.PAYMENT_PENDING);

	private final WorkflowClient temporal;
	private final BookingRecords records;

	BookingController(WorkflowClient temporal, BookingRecords records) {
		this.temporal = temporal;
		this.records = records;
	}

	@PostMapping
	ResponseEntity<BookingState> book(@Valid @RequestBody BookingRequest request) {
		Booking booking = new Booking(UUID.randomUUID().toString(), request.customer(), request.flightId(),
				request.hotelId(), request.nights(), request.cardToken());

		BookingWorkflow saga = temporal.newWorkflowStub(BookingWorkflow.class, WorkflowOptions.newBuilder()
				.setWorkflowId(BookingWorkflow.workflowId(booking.id()))
				.setTaskQueue(BookingWorkflow.TASK_QUEUE)
				.build());
		WorkflowClient.start(saga::book, booking);

		BookingState started = new BookingState(booking.id(), booking.customer(), booking.flightId(),
				booking.hotelId(), booking.nights(), BookingStatus.STARTED, null, null, List.of());
		return ResponseEntity.accepted().location(URI.create("/api/bookings/" + booking.id())).body(started);
	}

	@GetMapping("/{id}")
	BookingState get(@PathVariable UUID id) {
		try {
			return saga(id).state();
		}
		catch (WorkflowNotFoundException ex) {
			return records.find(id).orElseThrow(() -> notFound(id));
		}
	}

	@PostMapping("/{id}/cancel")
	ResponseEntity<BookingState> cancel(@PathVariable UUID id, @RequestBody(required = false) CancelRequest request) {
		BookingState current = get(id);
		if (!CANCELLABLE.contains(current.status())) {
			throw new ConflictException("Booking " + id + " is " + current.status() + " and can no longer be cancelled");
		}
		try {
			saga(id).cancel(request == null ? null : request.reason());
		}
		catch (WorkflowNotFoundException ex) {
			throw new ConflictException("Booking " + id + " has already finished");
		}
		return ResponseEntity.accepted().body(current);
	}

	private BookingWorkflow saga(UUID id) {
		return temporal.newWorkflowStub(BookingWorkflow.class, BookingWorkflow.workflowId(id.toString()));
	}

	private static NotFoundException notFound(UUID id) {
		return new NotFoundException("Booking " + id + " does not exist");
	}
}
