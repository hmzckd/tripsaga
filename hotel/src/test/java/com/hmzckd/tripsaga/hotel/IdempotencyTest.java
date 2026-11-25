package com.hmzckd.tripsaga.hotel;

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
		jdbc.sql("UPDATE hotels SET rooms_free = rooms_total").update();
	}

	@Test
	void theSameKeyTwiceHoldsOneRoomAndReplaysTheFirstAnswer() {
		ResponseEntity<String> first = hold("booking-1-hotel", "AYT-LARA", 3);
		ResponseEntity<String> second = hold("booking-1-hotel", "AYT-LARA", 3);

		assertThat(first.getStatusCode().value()).isEqualTo(201);
		assertThat(second.getStatusCode().value()).isEqualTo(200);
		assertThat(second.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
		assertThat(second.getBody()).isEqualTo(first.getBody());
		assertThat(roomsFree("AYT-LARA")).isEqualTo(19);
	}

	@Test
	void theSameKeyForADifferentHoldIsRejected() {
		hold("booking-1-hotel", "AYT-LARA", 3);

		assertThat(hold("booking-1-hotel", "AYT-LARA", 4).getStatusCode().value()).isEqualTo(422);
	}

	@Test
	void aHoldWithoutAKeyIsABadRequest() {
		ResponseEntity<String> response = http.post().uri("/holds")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new HoldRequest("AYT-LARA", "Ayşe Yılmaz", 3))
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
	}

	@Test
	void parallelRetriesWithTheSameKeyHoldOneRoom() throws Exception {
		List<Callable<Integer>> retries = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			retries.add(() -> hold("booking-2-hotel", "AYT-LARA", 3).getStatusCode().value());
		}

		List<Integer> statuses = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
			for (Future<Integer> result : pool.invokeAll(retries)) {
				statuses.add(result.get());
			}
		}

		assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
		assertThat(statuses).filteredOn(status -> status == 200).hasSize(9);
		assertThat(roomsFree("AYT-LARA")).isEqualTo(19);
	}

	private ResponseEntity<String> hold(String key, String hotelId, int nights) {
		return http.post().uri("/holds")
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new HoldRequest(hotelId, "Ayşe Yılmaz", nights))
				.retrieve()
				.toEntity(String.class);
	}

	private int roomsFree(String hotelId) {
		return jdbc.sql("SELECT rooms_free FROM hotels WHERE id = :id").param("id", hotelId).query(Integer.class)
				.single();
	}
}
