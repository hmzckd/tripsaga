package com.hmzckd.tripsaga.booking;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;

public class Participants implements AutoCloseable {

	public final WireMockServer flight = start();
	public final WireMockServer hotel = start();
	public final WireMockServer payment = start();

	private static WireMockServer start() {
		WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
		server.start();
		return server;
	}

	public void reset() {
		flight.resetAll();
		hotel.resetAll();
		payment.resetAll();
	}

	@Override
	public void close() {
		flight.stop();
		hotel.stop();
		payment.stop();
	}
}
