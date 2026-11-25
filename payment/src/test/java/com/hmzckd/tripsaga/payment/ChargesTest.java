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
		return http.post().uri("/charges")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new ChargeRequest("Ayşe Yılmaz", 1205000, cardToken))
				.retrieve()
				.toEntity(new ParameterizedTypeReference<>() {
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
