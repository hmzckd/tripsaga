package com.hmzckd.tripsaga.payment.web;

import com.hmzckd.tripsaga.payment.Charge;

public class PaymentDeclinedException extends RuntimeException {

	private final Charge charge;

	public PaymentDeclinedException(Charge charge) {
		super("The card was declined");
		this.charge = charge;
	}

	public Charge charge() {
		return charge;
	}
}
