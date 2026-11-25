package com.hmzckd.tripsaga.booking.participants;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hmzckd.tripsaga.booking.saga.FlightActivities;
import com.hmzckd.tripsaga.booking.saga.FlightHold;

@Component
class FlightClient implements FlightActivities {

	private final RestClient http;

	FlightClient(RestClient.Builder builder, ParticipantsProperties properties) {
		this.http = Http.client(builder, properties.flightUrl());
	}

	@Override
	public FlightHold hold(String idempotencyKey, String flightId, String passenger) {
		return Http.call(() -> http.post().uri("/holds")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("flightId", flightId, "passenger", passenger))
				.retrieve()
				.body(FlightHold.class));
	}

	@Override
	public void confirm(UUID holdId) {
		Http.call(() -> http.post().uri("/holds/{id}/confirm", holdId).retrieve().toBodilessEntity());
	}

	@Override
	public void cancel(UUID holdId) {
		Http.call(() -> http.post().uri("/holds/{id}/cancel", holdId).retrieve().toBodilessEntity());
	}
}
