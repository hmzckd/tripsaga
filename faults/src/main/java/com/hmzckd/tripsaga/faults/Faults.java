package com.hmzckd.tripsaga.faults;

import java.util.concurrent.atomic.AtomicInteger;

public class Faults {

	private volatile long delayMs;
	private final AtomicInteger failNext = new AtomicInteger();

	public FaultSettings settings() {
		return new FaultSettings(delayMs, failNext.get());
	}

	public void apply(FaultSettings settings) {
		delayMs = Math.max(0, settings.delayMs());
		failNext.set(Math.max(0, settings.failNext()));
	}

	long delayMs() {
		return delayMs;
	}

	boolean takeFailure() {
		return failNext.getAndUpdate(left -> left > 0 ? left - 1 : 0) > 0;
	}
}
