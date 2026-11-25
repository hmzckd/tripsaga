package com.hmzckd.tripsaga.hotel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hmzckd.tripsaga.hotel.web.ConflictException;
import com.hmzckd.tripsaga.hotel.web.NotFoundException;
import com.hmzckd.tripsaga.hotel.web.UnprocessableException;

@Service
public class HoldService {

	private final HotelRepository repository;

	HoldService(HotelRepository repository) {
		this.repository = repository;
	}

	public List<Hotel> hotels() {
		return repository.findAllHotels();
	}

	@Transactional
	public HoldResult hold(String idempotencyKey, HoldRequest request) {
		Optional<Hold> earlier = repository.findHoldByKey(idempotencyKey);
		if (earlier.isPresent()) {
			return replay(earlier.get(), request);
		}
		if (repository.findHotel(request.hotelId()).isEmpty()) {
			throw new NotFoundException("Hotel " + request.hotelId() + " does not exist");
		}
		UUID id = UUID.randomUUID();
		if (!repository.insertHold(id, idempotencyKey, request)) {
			return replay(repository.findHoldByKey(idempotencyKey).orElseThrow(), request);
		}
		if (!repository.takeRoom(request.hotelId())) {
			throw new ConflictException("Hotel " + request.hotelId() + " has no free rooms");
		}
		return new HoldResult(get(id), false);
	}

	private static HoldResult replay(Hold earlier, HoldRequest request) {
		if (!earlier.hotelId().equals(request.hotelId()) || !earlier.guest().equals(request.guest())
				|| earlier.nights() != request.nights()) {
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
			repository.releaseRoom(hold.hotelId());
			return hold;
		}
		Hold hold = get(id);
		if (hold.status() == HoldStatus.CANCELLED) {
			return hold;
		}
		throw new ConflictException("Hold " + id + " is " + hold.status() + " and cannot be cancelled");
	}
}
