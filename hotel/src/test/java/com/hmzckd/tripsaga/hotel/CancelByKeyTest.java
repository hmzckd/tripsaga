package com.hmzckd.tripsaga.hotel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CancelByKeyTest {

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
		jdbc.sql("DELETE FROM holds").update();
		jdbc.sql("UPDATE hotels SET rooms_free = rooms_total").update();
	}

	@Test
	void cancellingByKeyGivesTheRoomBack() {
		hold("booking-1-hotel-hold");

		ResponseEntity<String> cancelled = cancel("booking-1-hotel-hold");

		assertThat(cancelled.getStatusCode().value()).isEqualTo(200);
		assertThat(cancelled.getBody()).contains("\"status\":\"CANCELLED\"");
		assertThat(roomsFree()).isEqualTo(20);
	}

	@Test
	void aCancelThatArrivesFirstBlocksTheLateHold() {
		assertThat(cancel("booking-2-hotel-hold").getStatusCode().value()).isEqualTo(200);

		assertThat(hold("booking-2-hotel-hold").getStatusCode().value()).isEqualTo(409);
		assertThat(roomsFree()).isEqualTo(20);
	}

	@Test
	void cancellingByKeyTwiceGivesTheRoomBackOnce() {
		hold("booking-3-hotel-hold");

		cancel("booking-3-hotel-hold");
		cancel("booking-3-hotel-hold");

		assertThat(roomsFree()).isEqualTo(20);
	}

	@Test
	void aConfirmedHoldCannotBeCancelledByKey() {
		String id = hold("booking-4-hotel-hold").getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
		http.post().uri("/holds/{id}/confirm", id).retrieve().toBodilessEntity();

		assertThat(cancel("booking-4-hotel-hold").getStatusCode().value()).isEqualTo(409);
	}

	private ResponseEntity<String> hold(String key) {
		return http.post().uri("/holds")
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new HoldRequest("AYT-LARA", "Ayşe Yılmaz", 2))
				.retrieve()
				.toEntity(String.class);
	}

	private ResponseEntity<String> cancel(String key) {
		return http.post().uri("/holds/by-key/{key}/cancel", key).retrieve().toEntity(String.class);
	}

	private int roomsFree() {
		return jdbc.sql("SELECT rooms_free FROM hotels WHERE id = 'AYT-LARA'").query(Integer.class).single();
	}
}
