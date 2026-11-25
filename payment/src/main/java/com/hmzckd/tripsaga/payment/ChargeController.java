package com.hmzckd.tripsaga.payment;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChargeController {

	private final ChargeService charges;

	ChargeController(ChargeService charges) {
		this.charges = charges;
	}

	@PostMapping("/charges")
	ResponseEntity<Charge> charge(@Valid @RequestBody ChargeRequest request) {
		Charge charge = charges.charge(request);
		return ResponseEntity.created(URI.create("/charges/" + charge.id())).body(charge);
	}

	@GetMapping("/charges/{id}")
	Charge get(@PathVariable UUID id) {
		return charges.get(id);
	}

	@PostMapping("/charges/{id}/refund")
	Charge refund(@PathVariable UUID id) {
		return charges.refund(id);
	}
}
