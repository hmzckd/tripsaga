package com.hmzckd.tripsaga.faults;

import java.util.concurrent.atomic.AtomicInteger;

public class Faults {

	private volatile long delayMs;
	private final AtomicInteger failNext = new AtomicInteger();
	private final AtomicInteger loseNext = new AtomicInteger();

	public FaultSettings settings() {
		return new FaultSettings(delayMs, failNext.get(), loseNext.get());
	}

	public void apply(FaultSettings settings) {
		delayMs = settings.delayMs();
		failNext.set(settings.failNext());
		loseNext.set(settings.loseNext());
	}

	long delayMs() {
		return delayMs;
	}

	boolean takeFailure() {
		return take(failNext);
	}

	boolean takeLostAnswer() {
		return take(loseNext);
	}

	private static boolean take(AtomicInteger counter) {
		return counter.getAndUpdate(left -> left > 0 ? left - 1 : 0) > 0;
	}
}
