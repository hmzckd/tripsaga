package com.hmzckd.tripsaga.payment;

import java.net.URI;
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
class ChargeController {

	private final ChargeService charges;

	ChargeController(ChargeService charges) {
		this.charges = charges;
	}

	@PostMapping("/charges")
	ResponseEntity<Charge> charge(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody ChargeRequest request) {
		ChargeResult result = charges.charge(idempotencyKey, request);
		if (result.replayed()) {
			return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.charge());
		}
		return ResponseEntity.created(URI.create("/charges/" + result.charge().id())).body(result.charge());
	}

	@GetMapping("/charges/{id}")
	Charge get(@PathVariable UUID id) {
		return charges.get(id);
	}

	@PostMapping("/charges/{id}/refund")
	Charge refund(@PathVariable UUID id) {
		return charges.refund(id);
	}

	@PostMapping("/charges/by-key/{idempotencyKey}/refund")
	Charge refundByKey(@PathVariable String idempotencyKey) {
		return charges.refundByKey(idempotencyKey);
	}
}
