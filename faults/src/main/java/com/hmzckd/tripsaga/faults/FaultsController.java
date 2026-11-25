package com.hmzckd.tripsaga.faults;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/faults")
class FaultsController {

	private final Faults faults;

	FaultsController(Faults faults) {
		this.faults = faults;
	}

	@GetMapping
	FaultSettings get() {
		return faults.settings();
	}

	@PutMapping
	FaultSettings put(@RequestBody FaultSettings settings) {
		faults.apply(settings);
		return faults.settings();
	}

	@DeleteMapping
	FaultSettings reset() {
		faults.apply(new FaultSettings(0, 0));
		return faults.settings();
	}
}
