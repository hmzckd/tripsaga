package com.hmzckd.tripsaga.flight;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hmzckd.tripsaga.flight.web.ConflictException;
import com.hmzckd.tripsaga.flight.web.NotFoundException;
import com.hmzckd.tripsaga.flight.web.UnprocessableException;

@Service
public class HoldService {

	private final FlightRepository repository;

	HoldService(FlightRepository repository) {
		this.repository = repository;
	}

	public List<Flight> flights() {
		return repository.findAllFlights();
	}

	@Transactional
	public HoldResult hold(String idempotencyKey, HoldRequest request) {
		Optional<Hold> earlier = repository.findHoldByKey(idempotencyKey);
		if (earlier.isPresent()) {
			return replay(earlier.get(), request);
		}
		if (repository.findFlight(request.flightId()).isEmpty()) {
			throw new NotFoundException("Flight " + request.flightId() + " does not exist");
		}
		UUID id = UUID.randomUUID();
		if (!repository.insertHold(id, idempotencyKey, request)) {
			return replay(repository.findHoldByKey(idempotencyKey).orElseThrow(), request);
		}
		if (!repository.takeSeat(request.flightId())) {
			throw new ConflictException("Flight " + request.flightId() + " has no free seats");
		}
		return new HoldResult(get(id), false);
	}

	private static HoldResult replay(Hold earlier, HoldRequest request) {
		if (earlier.flightId() == null) {
			throw new ConflictException("The hold for this Idempotency-Key was cancelled before it arrived");
		}
		if (!earlier.flightId().equals(request.flightId()) || !earlier.passenger().equals(request.passenger())) {
			throw new UnprocessableException("This Idempotency-Key was already used for a different hold");
		}
		return new HoldResult(earlier, true);
	}

	public Hold get(UUID id) {
		return repository.findHold(id)
				.orElseThrow(() -> new NotFoundException("Hold " + id + " does not exist"));
	}

	@Transactional
	public Hold confirm(UUID id) {
		if (repository.changeStatus(id, HoldStatus.HELD, HoldStatus.CONFIRMED)) {
			return get(id);
		}
		Hold hold = get(id);
		if (hold.status() == HoldStatus.CONFIRMED) {
			return hold;
		}
		throw new ConflictException("Hold " + id + " is " + hold.status() + " and cannot be confirmed");
	}

	@Transactional
	public Hold cancel(UUID id) {
		if (repository.changeStatus(id, HoldStatus.HELD, HoldStatus.CANCELLED)) {
			Hold hold = get(id);
			repository.releaseSeat(hold.flightId());
			return hold;
		}
		Hold hold = get(id);
		if (hold.status() == HoldStatus.CANCELLED) {
			return hold;
		}
		throw new ConflictException("Hold " + id + " is " + hold.status() + " and cannot be cancelled");
	}

	@Transactional
	public Hold cancelByKey(String idempotencyKey) {
		if (repository.insertTombstone(UUID.randomUUID(), idempotencyKey)) {
			return repository.findHoldByKey(idempotencyKey).orElseThrow();
		}
		return cancel(repository.findHoldByKey(idempotencyKey).orElseThrow().id());
	}
}
