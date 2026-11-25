package com.hmzckd.tripsaga.booking.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BookingRequest(@NotBlank String customer, @NotBlank String flightId, @NotBlank String hotelId,
		@Min(1) @Max(30) int nights, @NotBlank String cardToken) {
}
