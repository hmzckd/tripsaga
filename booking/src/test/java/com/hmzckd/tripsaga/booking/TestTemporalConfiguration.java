package com.hmzckd.tripsaga.booking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

import io.temporal.testserver.TestServer;

@TestConfiguration(proxyBeanMethods = false)
public class TestTemporalConfiguration {

	private final int port = freePort();

	@Bean(destroyMethod = "close")
	TestServer.PortBoundTestServer temporalTestServer() {
		return TestServer.createPortBoundServer(port);
	}

	@Bean
	DynamicPropertyRegistrar temporalTarget(TestServer.PortBoundTestServer temporalTestServer) {
		return registry -> registry.add("tripsaga.temporal.target", () -> "localhost:" + port);
	}

	private static int freePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
