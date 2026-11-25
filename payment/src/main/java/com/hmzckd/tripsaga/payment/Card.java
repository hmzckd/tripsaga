package com.hmzckd.tripsaga.payment;

final class Card {

	static final String DECLINED = "tok_declined";

	private Card() {
	}

	static ChargeStatus outcome(String cardToken) {
		return DECLINED.equals(cardToken) ? ChargeStatus.DECLINED : ChargeStatus.CAPTURED;
	}
}
