package com.hmzckd.tripsaga.flight;

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
		jdbc.sql("UPDATE flights SET seats_free = seats_total").update();
	}

	@Test
	void cancellingByKeyGivesTheSeatBack() {
		hold("booking-1-flight-hold");

		ResponseEntity<String> cancelled = cancel("booking-1-flight-hold");

		assertThat(cancelled.getStatusCode().value()).isEqualTo(200);
		assertThat(cancelled.getBody()).contains("\"status\":\"CANCELLED\"");
		assertThat(seatsFree()).isEqualTo(50);
	}

	@Test
	void aCancelThatArrivesFirstBlocksTheLateHold() {
		assertThat(cancel("booking-2-flight-hold").getStatusCode().value()).isEqualTo(200);

		assertThat(hold("booking-2-flight-hold").getStatusCode().value()).isEqualTo(409);
		assertThat(seatsFree()).isEqualTo(50);
	}

	@Test
	void cancellingByKeyTwiceGivesTheSeatBackOnce() {
		hold("booking-3-flight-hold");

		cancel("booking-3-flight-hold");
		cancel("booking-3-flight-hold");

		assertThat(seatsFree()).isEqualTo(50);
	}

	@Test
	void aConfirmedHoldCannotBeCancelledByKey() {
		String id = hold("booking-4-flight-hold").getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
		http.post().uri("/holds/{id}/confirm", id).retrieve().toBodilessEntity();

		assertThat(cancel("booking-4-flight-hold").getStatusCode().value()).isEqualTo(409);
	}

	private ResponseEntity<String> hold(String key) {
		return http.post().uri("/holds")
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new HoldRequest("TK2412", "Ayşe Yılmaz"))
				.retrieve()
				.toEntity(String.class);
	}

	private ResponseEntity<String> cancel(String key) {
		return http.post().uri("/holds/by-key/{key}/cancel", key).retrieve().toEntity(String.class);
	}

	private int seatsFree() {
		return jdbc.sql("SELECT seats_free FROM flights WHERE id = 'TK2412'").query(Integer.class).single();
	}
}
