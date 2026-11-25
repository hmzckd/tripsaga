package com.hmzckd.tripsaga.flight;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
		jdbc.sql("UPDATE flights SET seats_free = seats_total").update();
	}

	@Test
	void holdingASeatTakesItFromTheFlight() {
		ResponseEntity<Hold> response = hold("TK2412", "Ayşe Yılmaz");

		assertThat(response.getStatusCode().value()).isEqualTo(201);
		assertThat(response.getBody().status()).isEqualTo(HoldStatus.HELD);
		assertThat(response.getBody().priceKurus()).isEqualTo(245000);
		assertThat(seatsFree("TK2412")).isEqualTo(49);
	}

	@Test
	void aFullFlightAnswersConflict() {
		assertThat(hold("XQ0000", "Ayşe Yılmaz").getStatusCode().value()).isEqualTo(409);
	}

	@Test
	void anUnknownFlightAnswersNotFound() {
		assertThat(hold("NOPE", "Ayşe Yılmaz").getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void cancellingTwiceGivesTheSeatBackOnce() {
		UUID id = hold("TK2412", "Ayşe Yılmaz").getBody().id();

		assertThat(post("/holds/" + id + "/cancel").getStatusCode().value()).isEqualTo(200);
		assertThat(post("/holds/" + id + "/cancel").getStatusCode().value()).isEqualTo(200);

		assertThat(seatsFree("TK2412")).isEqualTo(50);
	}

	@Test
	void aConfirmedHoldCannotBeCancelled() {
		UUID id = hold("TK2412", "Ayşe Yılmaz").getBody().id();

		assertThat(post("/holds/" + id + "/confirm").getStatusCode().value()).isEqualTo(200);
		assertThat(post("/holds/" + id + "/cancel").getStatusCode().value()).isEqualTo(409);
		assertThat(seatsFree("TK2412")).isEqualTo(49);
	}

	@Test
	void aCancelledHoldCannotBeConfirmed() {
		UUID id = hold("TK2412", "Ayşe Yılmaz").getBody().id();

		post("/holds/" + id + "/cancel");

		assertThat(post("/holds/" + id + "/confirm").getStatusCode().value()).isEqualTo(409);
	}

	@Test
	void parallelHoldsNeverSellMoreSeatsThanTheFlightHas() throws Exception {
		List<Callable<Integer>> buyers = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			String passenger = "Passenger " + i;
			buyers.add(() -> hold("TK2850", passenger).getStatusCode().value());
		}

		List<Integer> statuses = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
			for (Future<Integer> result : pool.invokeAll(buyers)) {
				statuses.add(result.get());
			}
		}

		assertThat(statuses).filteredOn(status -> status == 201).hasSize(2);
		assertThat(statuses).filteredOn(status -> status == 409).hasSize(8);
		assertThat(seatsFree("TK2850")).isZero();
	}

	private ResponseEntity<Hold> hold(String flightId, String passenger) {
		return http.post().uri("/holds")
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.body(new HoldRequest(flightId, passenger))
				.exchange((request, response) -> response.getStatusCode().is2xxSuccessful()
						? ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(Hold.class))
						: ResponseEntity.status(response.getStatusCode()).build());
	}

	private ResponseEntity<String> post(String path) {
		return http.post().uri(path).retrieve().toEntity(String.class);
	}

	private int seatsFree(String flightId) {
		return jdbc.sql("SELECT seats_free FROM flights WHERE id = :id").param("id", flightId).query(Integer.class)
				.single();
	}
}
