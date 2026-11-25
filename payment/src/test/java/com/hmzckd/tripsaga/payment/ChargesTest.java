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

	@Test
	void refundingByKeyRefundsACapturedCharge() {
		charge("booking-4-payment", 1205000, "tok_ok");

		assertThat(refundByKey("booking-4-payment")).containsEntry("status", "REFUNDED");
	}

	@Test
	void aRefundThatArrivesFirstBlocksTheLateCharge() {
		assertThat(refundByKey("booking-5-payment")).containsEntry("status", "CANCELLED");

		assertThat(charge("booking-5-payment", 1205000, "tok_ok").getStatusCode().value()).isEqualTo(409);
		assertThat(jdbc.sql("SELECT count(*) FROM charges WHERE status = 'CAPTURED'").query(Integer.class).single())
				.isZero();
	}

	@Test
	void refundingADeclinedChargeByKeyLeavesItDeclined() {
		charge("booking-6-payment", 1205000, "tok_declined");

		assertThat(refundByKey("booking-6-payment")).containsEntry("status", "DECLINED");
	}

	@Test
	void aPendingChargeIsAcceptedAndCompletedLater() {
		ResponseEntity<Map<String, Object>> accepted = charge("tok_pending");
		UUID id = UUID.fromString((String) accepted.getBody().get("id"));

		assertThat(accepted.getStatusCode().value()).isEqualTo(202);
		assertThat(accepted.getBody()).containsEntry("status", "PENDING");
		assertThat(complete(id, "CAPTURED").getStatusCode().value()).isEqualTo(200);
		assertThat(get(id)).containsEntry("status", "CAPTURED");
		assertThat(complete(id, "CAPTURED").getStatusCode().value()).isEqualTo(200);
		assertThat(complete(id, "DECLINED").getStatusCode().value()).isEqualTo(409);
	}

	@Test
	void refundingAPendingChargeCancelsItAndALateApprovalIsRefused() {
		charge("booking-7-payment", 1205000, "tok_pending");

		assertThat(http.get().uri("/charges/by-key/booking-7-payment").retrieve().body(String.class))
				.contains("\"status\":\"PENDING\"");

		Map<String, Object> cancelled = refundByKey("booking-7-payment");

		assertThat(cancelled).containsEntry("status", "CANCELLED");
		assertThat(complete(UUID.fromString((String) cancelled.get("id")), "CAPTURED").getStatusCode().value())
				.isEqualTo(409);
	}

	private ResponseEntity<String> complete(UUID id, String outcome) {
		return http.post().uri("/charges/{id}/complete", id)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("outcome", outcome))
				.retrieve()
				.toEntity(String.class);
	}

	private Map<String, Object> refundByKey(String key) {
		return http.post().uri("/charges/by-key/{key}/refund", key).retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
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
