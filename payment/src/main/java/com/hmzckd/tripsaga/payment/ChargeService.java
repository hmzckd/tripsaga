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

	public Charge getByKey(String idempotencyKey) {
		return repository.findByKey(idempotencyKey).map(ChargeRow::charge)
				.orElseThrow(() -> new NotFoundException("No charge has the Idempotency-Key " + idempotencyKey));
	}

	@Transactional
	public Charge refund(UUID id) {
		if (!repository.changeStatus(id, ChargeStatus.CAPTURED, ChargeStatus.REFUNDED)) {
			repository.changeStatus(id, ChargeStatus.PENDING, ChargeStatus.CANCELLED);
		}
		return get(id);
	}

	@Transactional
	public Charge complete(UUID id, CompleteRequest.Outcome outcome) {
		ChargeStatus to = ChargeStatus.valueOf(outcome.name());
		if (repository.changeStatus(id, ChargeStatus.PENDING, to)) {
			return get(id);
		}
		Charge charge = get(id);
		if (charge.status() == to) {
			return charge;
		}
		throw new ConflictException("Charge " + id + " is " + charge.status() + " and cannot be completed");
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
