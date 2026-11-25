package com.hmzckd.tripsaga.booking.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.hmzckd.tripsaga.booking.IntegrationTest;
import com.hmzckd.tripsaga.booking.Participants;
import com.hmzckd.tripsaga.booking.records.BookingRecords;
import com.hmzckd.tripsaga.booking.saga.BookingStatus;

@IntegrationTest
class BookingApiTest {

	static final String SEAT = "11111111-1111-1111-1111-111111111111";
	static final String ROOM = "22222222-2222-2222-2222-222222222222";

	@LocalServerPort
	int port;

	@Autowired
	Participants participants;

	@Autowired
	BookingRecords records;

	RestClient http;

	@BeforeEach
	void setUp() {
		participants.reset();
		http = RestClient.builder()
				.baseUrl("http://localhost:" + port)
				.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
				})
				.build();
	}

	@Test
	void aBookingRunsTheSagaAndIsConfirmed() {
		stub(participants.flight, "/holds", 201,
				"{\"id\":\"" + SEAT + "\",\"flightId\":\"TK2412\",\"status\":\"HELD\",\"priceKurus\":245000}");
		stub(participants.hotel, "/holds", 201, "{\"id\":\"" + ROOM
				+ "\",\"hotelId\":\"AYT-LARA\",\"nights\":3,\"status\":\"HELD\",\"totalKurus\":960000}");
		stub(participants.payment, "/charges", 201,
				"{\"id\":\"" + UUID.randomUUID() + "\",\"amountKurus\":1205000,\"status\":\"CAPTURED\"}");
		stub(participants.flight, "/holds/" + SEAT + "/confirm", 200, "{}");
		stub(participants.hotel, "/holds/" + ROOM + "/confirm", 200, "{}");

		ResponseEntity<Map<String, Object>> accepted = book();

		assertThat(accepted.getStatusCode().value()).isEqualTo(202);
		String id = (String) accepted.getBody().get("id");
		assertThat(accepted.getHeaders().getLocation()).hasPath("/api/bookings/" + id);

		await().atMost(Duration.ofSeconds(10))
				.until(() -> "CONFIRMED".equals(get(id).getBody().get("status")));

		assertThat(get(id).getBody()).containsEntry("totalKurus", 1205000);
		participants.flight.verify(postRequestedFor(urlEqualTo("/holds"))
				.withHeader("Idempotency-Key", equalTo(id + "-flight-hold"))
				.withRequestBody(equalToJson("{\"flightId\":\"TK2412\",\"passenger\":\"Ayşe Yılmaz\"}")));
		participants.payment.verify(postRequestedFor(urlEqualTo("/charges"))
				.withHeader("Idempotency-Key", equalTo(id + "-payment"))
				.withRequestBody(equalToJson(
						"{\"customer\":\"Ayşe Yılmaz\",\"amountKurus\":1205000,\"cardToken\":\"tok_ok\"}")));
		assertThat(records.find(UUID.fromString(id))).hasValueSatisfying(
				saved -> assertThat(saved.status()).isEqualTo(BookingStatus.CONFIRMED));
	}

	@Test
	void aRejectedRoomIsCompensatedByCancellingTheSeat() {
		stub(participants.flight, "/holds", 201,
				"{\"id\":\"" + SEAT + "\",\"flightId\":\"TK2412\",\"status\":\"HELD\",\"priceKurus\":245000}");
		stub(participants.hotel, "/holds", 409, "{\"title\":\"Conflict\",\"status\":409,"
				+ "\"detail\":\"Hotel BJV-DOLU has no free rooms\"}");
		stubAny(participants.flight, "/holds/by-key/.*/cancel");
		stubAny(participants.hotel, "/holds/by-key/.*/cancel");

		String id = (String) book().getBody().get("id");

		await().atMost(Duration.ofSeconds(10))
				.until(() -> "CANCELLED".equals(get(id).getBody().get("status")));

		assertThat(get(id).getBody()).containsEntry("failure", "Hotel BJV-DOLU has no free rooms");
		participants.hotel.verify(1, postRequestedFor(urlEqualTo("/holds")));
		participants.hotel.verify(postRequestedFor(urlEqualTo("/holds/by-key/" + id + "-hotel-hold/cancel")));
		participants.flight.verify(postRequestedFor(urlEqualTo("/holds/by-key/" + id + "-flight-hold/cancel")));
		participants.payment.verify(0, postRequestedFor(urlMatching(".*")));
	}

	@Test
	void aPaymentOutageIsRetriedWithTheSameIdempotencyKey() {
		stub(participants.flight, "/holds", 201,
				"{\"id\":\"" + SEAT + "\",\"flightId\":\"TK2412\",\"status\":\"HELD\",\"priceKurus\":245000}");
		stub(participants.hotel, "/holds", 201, "{\"id\":\"" + ROOM
				+ "\",\"hotelId\":\"AYT-LARA\",\"nights\":3,\"status\":\"HELD\",\"totalKurus\":960000}");
		participants.payment.stubFor(post(urlEqualTo("/charges")).inScenario("outage")
				.whenScenarioStateIs(Scenario.STARTED)
				.willReturn(aResponse().withStatus(503))
				.willSetStateTo("recovered"));
		participants.payment.stubFor(post(urlEqualTo("/charges")).inScenario("outage")
				.whenScenarioStateIs("recovered")
				.willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
						.withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"amountKurus\":1205000,\"status\":\"CAPTURED\"}")));
		stub(participants.flight, "/holds/" + SEAT + "/confirm", 200, "{}");
		stub(participants.hotel, "/holds/" + ROOM + "/confirm", 200, "{}");

		String id = (String) book().getBody().get("id");

		await().atMost(Duration.ofSeconds(10))
				.until(() -> "CONFIRMED".equals(get(id).getBody().get("status")));
		participants.payment.verify(2, postRequestedFor(urlEqualTo("/charges"))
				.withHeader("Idempotency-Key", equalTo(id + "-payment")));
	}

	@Test
	void aCustomerCanCancelWhileTheRoomIsBeingHeld() {
		stub(participants.flight, "/holds", 201,
				"{\"id\":\"" + SEAT + "\",\"flightId\":\"TK2412\",\"status\":\"HELD\",\"priceKurus\":245000}");
		participants.hotel.stubFor(post(urlEqualTo("/holds")).willReturn(aResponse()
				.withStatus(201)
				.withFixedDelay(1500)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"id\":\"" + ROOM
						+ "\",\"hotelId\":\"AYT-LARA\",\"nights\":3,\"status\":\"HELD\",\"totalKurus\":960000}")));
		stubAny(participants.flight, "/holds/by-key/.*/cancel");
		stubAny(participants.hotel, "/holds/by-key/.*/cancel");

		String id = (String) book().getBody().get("id");
		await().atMost(Duration.ofSeconds(5)).until(() -> participants.hotel.getAllServeEvents().size() == 1);

		assertThat(cancel(id).getStatusCode().value()).isEqualTo(202);

		await().atMost(Duration.ofSeconds(10))
				.until(() -> "CANCELLED".equals(get(id).getBody().get("status")));
		assertThat(get(id).getBody()).containsEntry("failure", "cancelled by the customer: changed my plans");
		participants.payment.verify(0, postRequestedFor(urlMatching(".*")));
		participants.hotel.verify(postRequestedFor(urlEqualTo("/holds/by-key/" + id + "-hotel-hold/cancel")));
		participants.flight.verify(postRequestedFor(urlEqualTo("/holds/by-key/" + id + "-flight-hold/cancel")));
	}

	@Test
	void aConfirmedBookingCannotBeCancelled() {
		stub(participants.flight, "/holds", 201,
				"{\"id\":\"" + SEAT + "\",\"flightId\":\"TK2412\",\"status\":\"HELD\",\"priceKurus\":245000}");
		stub(participants.hotel, "/holds", 201, "{\"id\":\"" + ROOM
				+ "\",\"hotelId\":\"AYT-LARA\",\"nights\":3,\"status\":\"HELD\",\"totalKurus\":960000}");
		stub(participants.payment, "/charges", 201,
				"{\"id\":\"" + UUID.randomUUID() + "\",\"amountKurus\":1205000,\"status\":\"CAPTURED\"}");
		stub(participants.flight, "/holds/" + SEAT + "/confirm", 200, "{}");
		stub(participants.hotel, "/holds/" + ROOM + "/confirm", 200, "{}");
		String id = (String) book().getBody().get("id");
		await().atMost(Duration.ofSeconds(10))
				.until(() -> "CONFIRMED".equals(get(id).getBody().get("status")));

		assertThat(cancel(id).getStatusCode().value()).isEqualTo(409);
	}

	@Test
	void cancellingAnUnknownBookingIsNotFound() {
		assertThat(cancel(UUID.randomUUID().toString()).getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void anInvalidRequestIsRejectedBeforeTheSagaStarts() {
		ResponseEntity<String> response = http.post().uri("/api/bookings")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("customer", "Ayşe Yılmaz", "flightId", "TK2412", "hotelId", "AYT-LARA", "nights", 0,
						"cardToken", "tok_ok"))
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		participants.flight.verify(0, postRequestedFor(urlMatching(".*")));
	}

	@Test
	void anUnknownBookingIsNotFound() {
		assertThat(get(UUID.randomUUID().toString()).getStatusCode().value()).isEqualTo(404);
	}

	private void stub(WireMockServer server, String path, int status, String body) {
		server.stubFor(post(urlEqualTo(path)).willReturn(aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody(body)));
	}

	private void stubAny(WireMockServer server, String pathPattern) {
		server.stubFor(post(urlMatching(pathPattern)).willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{}")));
	}

	private ResponseEntity<Map<String, Object>> book() {
		return http.post().uri("/api/bookings")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("customer", "Ayşe Yılmaz", "flightId", "TK2412", "hotelId", "AYT-LARA", "nights", 3,
						"cardToken", "tok_ok"))
				.retrieve()
				.toEntity(new ParameterizedTypeReference<>() {
				});
	}

	private ResponseEntity<String> cancel(String id) {
		return http.post().uri("/api/bookings/{id}/cancel", id)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("reason", "changed my plans"))
				.retrieve()
				.toEntity(String.class);
	}

	private ResponseEntity<Map<String, Object>> get(String id) {
		return http.get().uri("/api/bookings/{id}", id).retrieve().toEntity(new ParameterizedTypeReference<>() {
		});
	}
}
