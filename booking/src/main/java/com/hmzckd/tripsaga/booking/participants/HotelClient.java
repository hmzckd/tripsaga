package com.hmzckd.tripsaga.booking.participants;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hmzckd.tripsaga.booking.saga.HotelActivities;
import com.hmzckd.tripsaga.booking.saga.HotelHold;

@Component
class HotelClient implements HotelActivities {

	private final RestClient http;

	HotelClient(RestClient.Builder builder, ParticipantsProperties properties) {
		this.http = Http.client(builder, properties.hotelUrl());
	}

	@Override
	public HotelHold hold(String idempotencyKey, String hotelId, String guest, int nights) {
		return http.post().uri("/holds")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("hotelId", hotelId, "guest", guest, "nights", nights))
				.retrieve()
				.body(HotelHold.class);
	}

	@Override
	public void confirm(UUID holdId) {
		http.post().uri("/holds/{id}/confirm", holdId).retrieve().toBodilessEntity();
	}
}
