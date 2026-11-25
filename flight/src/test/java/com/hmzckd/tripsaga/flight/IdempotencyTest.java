package com.hmzckd.tripsaga.flight;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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
class IdempotencyTest {

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
	void theSameKeyTwiceHoldsOneSeatAndReplaysTheFirstAnswer() {
		ResponseEntity<String> first = hold("booking-1-flight", "TK2412", "Ayşe Yılmaz");
		ResponseEntity<String> second = hold("booking-1-flight", "TK2412", "Ayşe Yılmaz");

		assertThat(first.getStatusCode().value()).isEqualTo(201);
		assertThat(second.getStatusCode().value()).isEqualTo(200);
		assertThat(second.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
		assertThat(second.getBody()).isEqualTo(first.getBody());
		assertThat(seatsFree("TK2412")).isEqualTo(49);
	}

	@Test
	void theSameKeyForADifferentHoldIsRejected() {
		hold("booking-1-flight", "TK2412", "Ayşe Yılmaz");

		assertThat(hold("booking-1-flight", "PC2016", "Ayşe Yılmaz").getStatusCode().value()).isEqualTo(422);
	}

	@Test
	void aHoldWithoutAKeyIsABadRequest() {
		ResponseEntity<String> response = http.post().uri("/holds")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new HoldRequest("TK2412", "Ayşe Yılmaz"))
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
	}

	@Test
	void parallelRetriesWithTheSameKeyHoldOneSeat() throws Exception {
		List<Callable<Integer>> retries = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			retries.add(() -> hold("booking-2-flight", "TK2412", "Ayşe Yılmaz").getStatusCode().value());
		}

		List<Integer> statuses = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
			for (Future<Integer> result : pool.invokeAll(retries)) {
				statuses.add(result.get());
			}
		}

		assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
		assertThat(statuses).filteredOn(status -> status == 200).hasSize(9);
		assertThat(seatsFree("TK2412")).isEqualTo(49);
	}

	private ResponseEntity<String> hold(String key, String flightId, String passenger) {
		return http.post().uri("/holds")
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new HoldRequest(flightId, passenger))
				.retrieve()
				.toEntity(String.class);
	}

	private int seatsFree(String flightId) {
		return jdbc.sql("SELECT seats_free FROM flights WHERE id = :id").param("id", flightId).query(Integer.class)
				.single();
	}
}
