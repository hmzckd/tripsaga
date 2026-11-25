package com.hmzckd.tripsaga.flight;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FlightController {

	private final HoldService holds;

	FlightController(HoldService holds) {
		this.holds = holds;
	}

	@GetMapping("/flights")
	List<Flight> flights() {
		return holds.flights();
	}

	@PostMapping("/holds")
	ResponseEntity<Hold> hold(@Valid @RequestBody HoldRequest request) {
		Hold hold = holds.hold(request);
		return ResponseEntity.created(URI.create("/holds/" + hold.id())).body(hold);
	}

	@GetMapping("/holds/{id}")
	Hold get(@PathVariable UUID id) {
		return holds.get(id);
	}

	@PostMapping("/holds/{id}/confirm")
	Hold confirm(@PathVariable UUID id) {
		return holds.confirm(id);
	}

	@PostMapping("/holds/{id}/cancel")
	Hold cancel(@PathVariable UUID id) {
		return holds.cancel(id);
	}
}
