package com.hmzckd.tripsaga.flight;

import jakarta.validation.constraints.NotBlank;

public record HoldRequest(@NotBlank String flightId, @NotBlank String passenger) {
}
