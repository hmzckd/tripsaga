package com.hmzckd.tripsaga.booking.participants;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hmzckd.tripsaga.booking.saga.Charge;
import com.hmzckd.tripsaga.booking.saga.PaymentActivities;

@Component
class PaymentClient implements PaymentActivities {

	private final RestClient http;

	PaymentClient(RestClient.Builder builder, ParticipantsProperties properties) {
		this.http = Http.client(builder, properties.paymentUrl());
	}

	@Override
	public Charge charge(String idempotencyKey, String customer, long amountKurus, String cardToken) {
		return Http.call(() -> http.post().uri("/charges")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("customer", customer, "amountKurus", amountKurus, "cardToken", cardToken))
				.retrieve()
				.body(Charge.class));
	}

	@Override
	public Charge status(UUID chargeId) {
		return Http.call(() -> http.get().uri("/charges/{id}", chargeId).retrieve().body(Charge.class));
	}

	@Override
	public void refund(String chargeIdempotencyKey) {
		Http.call(() -> http.post().uri("/charges/by-key/{key}/refund", chargeIdempotencyKey).retrieve()
				.toBodilessEntity());
	}
}
