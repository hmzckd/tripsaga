package com.hmzckd.tripsaga.booking.participants;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

final class Http {

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
}
