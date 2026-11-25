package com.hmzckd.tripsaga.hotel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

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
class HoldsTest {

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
	void holdingARoomTakesItAndPricesAllNights() {
		ResponseEntity<Hold> response = hold("AYT-LARA", 3);

		assertThat(response.getStatusCode().value()).isEqualTo(201);
		assertThat(response.getBody().status()).isEqualTo(HoldStatus.HELD);
		assertThat(response.getBody().totalKurus()).isEqualTo(3 * 320000);
		assertThat(roomsFree("AYT-LARA")).isEqualTo(19);
	}

	@Test
	void aFullHotelAnswersConflict() {
		assertThat(hold("BJV-DOLU", 2).getStatusCode().value()).isEqualTo(409);
	}

	@Test
	void theLastRoomCanBeHeldOnlyOnce() {
		assertThat(hold("DLM-KOY", 2).getStatusCode().value()).isEqualTo(201);
		assertThat(hold("DLM-KOY", 2).getStatusCode().value()).isEqualTo(409);
	}

	@Test
	void zeroNightsIsABadRequest() {
		assertThat(hold("AYT-LARA", 0).getStatusCode().value()).isEqualTo(400);
	}

	@Test
	void cancellingTwiceGivesTheRoomBackOnce() {
		UUID id = hold("AYT-LARA", 2).getBody().id();

		assertThat(post("/holds/" + id + "/cancel").getStatusCode().value()).isEqualTo(200);
		assertThat(post("/holds/" + id + "/cancel").getStatusCode().value()).isEqualTo(200);

		assertThat(roomsFree("AYT-LARA")).isEqualTo(20);
	}

	@Test
	void aConfirmedHoldCannotBeCancelled() {
		UUID id = hold("AYT-LARA", 2).getBody().id();

		assertThat(post("/holds/" + id + "/confirm").getStatusCode().value()).isEqualTo(200);
		assertThat(post("/holds/" + id + "/cancel").getStatusCode().value()).isEqualTo(409);
	}

	private ResponseEntity<Hold> hold(String hotelId, int nights) {
		return http.post().uri("/holds")
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.body(new HoldRequest(hotelId, "Ayşe Yılmaz", nights))
				.exchange((request, response) -> response.getStatusCode().is2xxSuccessful()
						? ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(Hold.class))
						: ResponseEntity.status(response.getStatusCode()).build());
	}

	private ResponseEntity<String> post(String path) {
		return http.post().uri(path).retrieve().toEntity(String.class);
	}

	private int roomsFree(String hotelId) {
		return jdbc.sql("SELECT rooms_free FROM hotels WHERE id = :id").param("id", hotelId).query(Integer.class)
				.single();
	}
}
