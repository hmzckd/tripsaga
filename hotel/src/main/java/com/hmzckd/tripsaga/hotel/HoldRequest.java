package com.hmzckd.tripsaga.hotel;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record HoldRequest(@NotBlank String hotelId, @NotBlank String guest, @Min(1) @Max(30) int nights) {
}
