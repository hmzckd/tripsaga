package com.hmzckd.tripsaga.faults;

public record FaultSettings(Long delayMs, Integer failNext, Integer loseNext) {

	public FaultSettings {
		delayMs = delayMs == null ? 0 : Math.max(0, delayMs);
		failNext = failNext == null ? 0 : Math.max(0, failNext);
		loseNext = loseNext == null ? 0 : Math.max(0, loseNext);
	}
}
