package com.hmzckd.tripsaga.booking.temporal;

import java.util.concurrent.TimeUnit;

import org.springframework.context.SmartLifecycle;

import io.temporal.worker.WorkerFactory;

class WorkerLifecycle implements SmartLifecycle {

	private final WorkerFactory factory;
	private volatile boolean running;

	WorkerLifecycle(WorkerFactory factory) {
		this.factory = factory;
	}

	@Override
	public void start() {
		factory.start();
		running = true;
	}

	@Override
	public void stop() {
		factory.shutdown();
		factory.awaitTermination(10, TimeUnit.SECONDS);
		running = false;
	}

	@Override
	public boolean isRunning() {
		return running;
	}
}
