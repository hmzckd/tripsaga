package com.hmzckd.tripsaga.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
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
