package com.hmzckd.tripsaga.booking.participants;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.http.ProblemDetail;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import io.temporal.failure.ApplicationFailure;

final class Http {

	static final String REJECTED = "ParticipantRejected";
	static final String UNAVAILABLE = "ParticipantUnavailable";

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
	static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	private Http() {
	}

	static RestClient client(RestClient.Builder builder, URI baseUrl) {
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(CONNECT_TIMEOUT)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return builder.clone().baseUrl(baseUrl.toString()).requestFactory(requestFactory).build();
	}

	static <T> T call(Supplier<T> call) {
		try {
			return call.get();
		}
		catch (HttpClientErrorException ex) {
			throw ApplicationFailure.newNonRetryableFailure(detail(ex), REJECTED, ex.getStatusCode().value());
		}
		catch (HttpServerErrorException ex) {
			throw ApplicationFailure.newFailure(ex.getStatusCode().value() + " " + detail(ex), UNAVAILABLE);
		}
	}

	private static String detail(RestClientResponseException ex) {
		ProblemDetail problem = ex.getResponseBodyAs(ProblemDetail.class);
		return problem != null && problem.getDetail() != null ? problem.getDetail() : ex.getStatusText();
	}
}
