package com.hmzckd.tripsaga.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ChargeRepository {

	private final JdbcClient jdbc;

	ChargeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void insert(UUID id, ChargeRequest request, ChargeStatus status) {
		jdbc.sql("""
				INSERT INTO charges (id, customer, amount_kurus, card_token, status)
				VALUES (:id, :customer, :amount, :cardToken, :status)
				""")
				.param("id", id)
				.param("customer", request.customer())
				.param("amount", request.amountKurus())
				.param("cardToken", request.cardToken())
				.param("status", status.name())
				.update();
	}

	Optional<Charge> find(UUID id) {
		return jdbc.sql("SELECT id, customer, amount_kurus, status FROM charges WHERE id = :id")
				.param("id", id)
				.query(Charge.class)
				.optional();
	}

	boolean changeStatus(UUID id, ChargeStatus from, ChargeStatus to) {
		return jdbc.sql("""
				UPDATE charges SET status = :to, updated_at = now()
				WHERE id = :id AND status = :from
				""")
				.param("id", id)
				.param("from", from.name())
				.param("to", to.name())
				.update() == 1;
	}
}
