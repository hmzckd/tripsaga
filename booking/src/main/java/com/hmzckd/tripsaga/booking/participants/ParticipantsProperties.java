package com.hmzckd.tripsaga.booking.participants;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tripsaga.participants")
public record ParticipantsProperties(URI flightUrl, URI hotelUrl, URI paymentUrl) {
}
