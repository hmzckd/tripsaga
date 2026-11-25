package com.hmzckd.tripsaga.booking;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class TestParticipantsConfiguration {

	@Bean(destroyMethod = "close")
	Participants participants() {
		return new Participants();
	}

	@Bean
	DynamicPropertyRegistrar participantUrls(Participants participants) {
		return registry -> {
			registry.add("tripsaga.participants.flight-url", participants.flight::baseUrl);
			registry.add("tripsaga.participants.hotel-url", participants.hotel::baseUrl);
			registry.add("tripsaga.participants.payment-url", participants.payment::baseUrl);
		};
	}
}
