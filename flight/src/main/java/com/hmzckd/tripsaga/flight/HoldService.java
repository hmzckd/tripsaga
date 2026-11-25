package com.hmzckd.tripsaga.flight;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hmzckd.tripsaga.flight.web.ConflictException;
import com.hmzckd.tripsaga.flight.web.NotFoundException;

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
	public Hold hold(HoldRequest request) {
		if (repository.findFlight(request.flightId()).isEmpty()) {
			throw new NotFoundException("Flight " + request.flightId() + " does not exist");
		}
		if (!repository.takeSeat(request.flightId())) {
			throw new ConflictException("Flight " + request.flightId() + " has no free seats");
		}
		UUID id = UUID.randomUUID();
		repository.insertHold(id, request.flightId(), request.passenger());
		return get(id);
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
}
