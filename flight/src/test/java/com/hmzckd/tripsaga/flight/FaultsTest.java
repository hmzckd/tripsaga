package com.hmzckd.tripsaga.flight;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "tripsaga.faults.enabled=true")
@Import(TestcontainersConfiguration.class)
class FaultsTest {

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
		http.delete().uri("/admin/faults").retrieve().toBodilessEntity();
		jdbc.sql("DELETE FROM holds").update();
		jdbc.sql("UPDATE flights SET seats_free = seats_total").update();
	}

	@Test
	void failedRequestsAreNotProcessed() {
		faults(Map.of("delayMs", 0, "failNext", 2));

		assertThat(holdStatus()).isEqualTo(503);
		assertThat(holdStatus()).isEqualTo(503);
		assertThat(seatsFree()).isEqualTo(50);
		assertThat(holdStatus()).isEqualTo(201);
		assertThat(seatsFree()).isEqualTo(49);
	}

	@Test
	void aDelayIsAddedToEveryWrite() {
		faults(Map.of("delayMs", 300, "failNext", 0));

		long started = System.nanoTime();
		holdStatus();

		assertThat((System.nanoTime() - started) / 1_000_000).isGreaterThanOrEqualTo(300);
	}

	@Test
	void readsAreNeverFailed() {
		faults(Map.of("delayMs", 0, "failNext", 5));

		assertThat(http.get().uri("/flights").retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(200);
	}

	private void faults(Map<String, Object> settings) {
		http.put().uri("/admin/faults").contentType(MediaType.APPLICATION_JSON).body(settings).retrieve()
				.toBodilessEntity();
	}

	private int holdStatus() {
		return http.post().uri("/holds")
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.body(new HoldRequest("TK2412", "Ayşe Yılmaz"))
				.retrieve()
				.toBodilessEntity()
				.getStatusCode()
				.value();
	}

	private int seatsFree() {
		return jdbc.sql("SELECT seats_free FROM flights WHERE id = 'TK2412'").query(Integer.class).single();
	}
}
