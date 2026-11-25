package com.hmzckd.tripsaga.payment;

import jakarta.validation.constraints.NotNull;

public record CompleteRequest(@NotNull Outcome outcome) {

	public enum Outcome {
		CAPTURED, DECLINED
	}
}
