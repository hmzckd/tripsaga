package com.hmzckd.tripsaga.booking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

@IntegrationTest
class HealthTest {

	@LocalServerPort
	int port;

	@Test
	void reportsUp() {
		String body = RestClient.create("http://localhost:" + port)
				.get().uri("/actuator/health")
				.retrieve()
				.body(String.class);

		assertThat(body).contains("\"status\":\"UP\"");
	}
}
