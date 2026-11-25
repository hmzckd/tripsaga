package com.hmzckd.tripsaga.hotel;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HotelController {

	private final HoldService holds;

	HotelController(HoldService holds) {
		this.holds = holds;
	}

	@GetMapping("/hotels")
	List<Hotel> hotels() {
		return holds.hotels();
	}

	@PostMapping("/holds")
	ResponseEntity<Hold> hold(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody HoldRequest request) {
		HoldResult result = holds.hold(idempotencyKey, request);
		if (result.replayed()) {
			return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.hold());
		}
		return ResponseEntity.created(URI.create("/holds/" + result.hold().id())).body(result.hold());
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
