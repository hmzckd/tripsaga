package com.hmzckd.tripsaga.hotel;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hmzckd.tripsaga.hotel.web.ConflictException;
import com.hmzckd.tripsaga.hotel.web.NotFoundException;

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
	public Hold hold(HoldRequest request) {
		if (repository.findHotel(request.hotelId()).isEmpty()) {
			throw new NotFoundException("Hotel " + request.hotelId() + " does not exist");
		}
		if (!repository.takeRoom(request.hotelId())) {
			throw new ConflictException("Hotel " + request.hotelId() + " has no free rooms");
		}
		UUID id = UUID.randomUUID();
		repository.insertHold(id, request);
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
