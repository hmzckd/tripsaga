package com.hmzckd.tripsaga.booking.temporal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tripsaga.temporal")
public record TemporalProperties(String target, String namespace) {
}
