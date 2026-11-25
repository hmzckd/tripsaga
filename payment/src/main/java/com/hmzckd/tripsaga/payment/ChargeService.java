package com.hmzckd.tripsaga.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hmzckd.tripsaga.payment.web.ConflictException;
import com.hmzckd.tripsaga.payment.web.NotFoundException;
import com.hmzckd.tripsaga.payment.ChargeRepository.ChargeRow;
import com.hmzckd.tripsaga.payment.web.PaymentDeclinedException;
import com.hmzckd.tripsaga.payment.web.UnprocessableException;

@Service
public class ChargeService {

	private final ChargeRepository repository;

	ChargeService(ChargeRepository repository) {
		this.repository = repository;
	}

	@Transactional(noRollbackFor = PaymentDeclinedException.class)
	public ChargeResult charge(String idempotencyKey, ChargeRequest request) {
		Optional<ChargeRow> earlier = repository.findByKey(idempotencyKey);
		if (earlier.isPresent()) {
			return replay(earlier.get(), request);
		}
		UUID id = UUID.randomUUID();
		if (!repository.insert(id, idempotencyKey, request, Card.outcome(request.cardToken()))) {
			return replay(repository.findByKey(idempotencyKey).orElseThrow(), request);
		}
		return new ChargeResult(answer(get(id)), false);
	}

	private static ChargeResult replay(ChargeRow earlier, ChargeRequest request) {
		if (earlier.isTombstone()) {
			throw new ConflictException("The charge for this Idempotency-Key was cancelled before it arrived");
		}
		if (!earlier.sameRequestAs(request)) {
			throw new UnprocessableException("This Idempotency-Key was already used for a different charge");
		}
		return new ChargeResult(answer(earlier.charge()), true);
	}

	public Charge get(UUID id) {
		return repository.find(id).orElseThrow(() -> new NotFoundException("Charge " + id + " does not exist"));
	}

	@Transactional
	public Charge refund(UUID id) {
		repository.changeStatus(id, ChargeStatus.CAPTURED, ChargeStatus.REFUNDED);
		return get(id);
	}

	@Transactional
	public Charge refundByKey(String idempotencyKey) {
		if (repository.insertTombstone(UUID.randomUUID(), idempotencyKey)) {
			return repository.findByKey(idempotencyKey).orElseThrow().charge();
		}
		return refund(repository.findByKey(idempotencyKey).orElseThrow().id());
	}

	static Charge answer(Charge charge) {
		if (charge.status() == ChargeStatus.DECLINED) {
			throw new PaymentDeclinedException(charge);
		}
		return charge;
	}
}
