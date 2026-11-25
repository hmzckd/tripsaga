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

	boolean insert(UUID id, String idempotencyKey, ChargeRequest request, ChargeStatus status) {
		return jdbc.sql("""
				INSERT INTO charges (id, idempotency_key, customer, amount_kurus, card_token, status)
				VALUES (:id, :key, :customer, :amount, :cardToken, :status)
				ON CONFLICT (idempotency_key) DO NOTHING
				""")
				.param("id", id)
				.param("key", idempotencyKey)
				.param("customer", request.customer())
				.param("amount", request.amountKurus())
				.param("cardToken", request.cardToken())
				.param("status", status.name())
				.update() == 1;
	}

	boolean insertTombstone(UUID id, String idempotencyKey) {
		return jdbc.sql("""
				INSERT INTO charges (id, idempotency_key, status)
				VALUES (:id, :key, 'CANCELLED')
				ON CONFLICT (idempotency_key) DO NOTHING
				""")
				.param("id", id)
				.param("key", idempotencyKey)
				.update() == 1;
	}

	Optional<ChargeRow> findByKey(String idempotencyKey) {
		return jdbc.sql("""
				SELECT id, customer, amount_kurus, card_token, status FROM charges
				WHERE idempotency_key = :key
				""")
				.param("key", idempotencyKey)
				.query(ChargeRow.class)
				.optional();
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

	record ChargeRow(UUID id, String customer, Long amountKurus, String cardToken, ChargeStatus status) {

		Charge charge() {
			return new Charge(id, customer, amountKurus, status);
		}

		boolean isTombstone() {
			return customer == null;
		}

		boolean sameRequestAs(ChargeRequest request) {
			return customer.equals(request.customer()) && amountKurus == request.amountKurus()
					&& cardToken.equals(request.cardToken());
		}
	}
}
