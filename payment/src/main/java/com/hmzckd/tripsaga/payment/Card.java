package com.hmzckd.tripsaga.payment;

final class Card {

	static final String DECLINED = "tok_declined";
	static final String PENDING = "tok_pending";

	private Card() {
	}

	static ChargeStatus outcome(String cardToken) {
		return switch (cardToken) {
			case DECLINED -> ChargeStatus.DECLINED;
			case PENDING -> ChargeStatus.PENDING;
			default -> ChargeStatus.CAPTURED;
		};
	}
}
