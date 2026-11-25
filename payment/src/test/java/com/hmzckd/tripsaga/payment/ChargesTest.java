package com.hmzckd.tripsaga.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ChargesTest {

	@LocalServerPort
	int port;

	@Autowired
	JdbcClient jdbc;

	RestClient http;

	@BeforeEach
	void setUp() {
		http = RestClient.builder()
				.baseUrl("http://localhost:" + port)
				.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
				})
				.build();
		jdbc.sql("DELETE FROM charges").update();
	}

	@Test
	void aGoodCardIsCaptured() {
		ResponseEntity<Map<String, Object>> response = charge("tok_ok");

		assertThat(response.getStatusCode().value()).isEqualTo(201);
		assertThat(response.getBody()).containsEntry("status", "CAPTURED").containsEntry("amountKurus", 1205000);
	}

	@Test
	void aDeclinedCardAnswersPaymentRequiredAndIsRecorded() {
		ResponseEntity<Map<String, Object>> response = charge("tok_declined");

		assertThat(response.getStatusCode().value()).isEqualTo(402);
		UUID chargeId = UUID.fromString((String) response.getBody().get("chargeId"));
		assertThat(get(chargeId)).containsEntry("status", "DECLINED");
	}

	@Test
	void refundingTwiceRefundsOnce() {
		UUID id = UUID.fromString((String) charge("tok_ok").getBody().get("id"));

		assertThat(refund(id)).containsEntry("status", "REFUNDED");
		assertThat(refund(id)).containsEntry("status", "REFUNDED");
	}

	@Test
	void refundingADeclinedChargeLeavesItDeclined() {
		UUID id = UUID.fromString((String) charge("tok_declined").getBody().get("chargeId"));

		assertThat(refund(id)).containsEntry("status", "DECLINED");
	}

	private ResponseEntity<Map<String, Object>> charge(String cardToken) {
		return charge(UUID.randomUUID().toString(), 1205000, cardToken);
	}

	private ResponseEntity<Map<String, Object>> charge(String key, long amountKurus, String cardToken) {
		return http.post().uri("/charges")
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new ChargeRequest("Ayşe Yılmaz", amountKurus, cardToken))
				.retrieve()
				.toEntity(new ParameterizedTypeReference<>() {
				});
	}

	@Test
	void theSameKeyTwiceChargesOnce() {
		ResponseEntity<Map<String, Object>> first = charge("booking-1-payment", 1205000, "tok_ok");
		ResponseEntity<Map<String, Object>> second = charge("booking-1-payment", 1205000, "tok_ok");

		assertThat(first.getStatusCode().value()).isEqualTo(201);
		assertThat(second.getStatusCode().value()).isEqualTo(200);
		assertThat(second.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
		assertThat(second.getBody()).isEqualTo(first.getBody());
		assertThat(jdbc.sql("SELECT count(*) FROM charges").query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void aRetriedDeclineIsDeclinedAgainWithoutASecondCharge() {
		ResponseEntity<Map<String, Object>> first = charge("booking-2-payment", 1205000, "tok_declined");
		ResponseEntity<Map<String, Object>> second = charge("booking-2-payment", 1205000, "tok_declined");

		assertThat(second.getStatusCode().value()).isEqualTo(402);
		assertThat(second.getBody().get("chargeId")).isEqualTo(first.getBody().get("chargeId"));
		assertThat(jdbc.sql("SELECT count(*) FROM charges").query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void theSameKeyForADifferentAmountIsRejected() {
		charge("booking-3-payment", 1205000, "tok_ok");

		assertThat(charge("booking-3-payment", 999, "tok_ok").getStatusCode().value()).isEqualTo(422);
	}

	private Map<String, Object> get(UUID id) {
		return http.get().uri("/charges/" + id).retrieve().body(new ParameterizedTypeReference<>() {
		});
	}

	private Map<String, Object> refund(UUID id) {
		return http.post().uri("/charges/" + id + "/refund").retrieve().body(new ParameterizedTypeReference<>() {
		});
	}
}
