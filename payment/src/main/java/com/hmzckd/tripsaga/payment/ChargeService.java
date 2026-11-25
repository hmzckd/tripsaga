package com.hmzckd.tripsaga.payment;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hmzckd.tripsaga.payment.web.NotFoundException;
import com.hmzckd.tripsaga.payment.web.PaymentDeclinedException;

@Service
public class ChargeService {

	private final ChargeRepository repository;

	ChargeService(ChargeRepository repository) {
		this.repository = repository;
	}

	@Transactional(noRollbackFor = PaymentDeclinedException.class)
	public Charge charge(ChargeRequest request) {
		UUID id = UUID.randomUUID();
		repository.insert(id, request, Card.outcome(request.cardToken()));
		return answer(get(id));
	}

	public Charge get(UUID id) {
		return repository.find(id).orElseThrow(() -> new NotFoundException("Charge " + id + " does not exist"));
	}

	@Transactional
	public Charge refund(UUID id) {
		repository.changeStatus(id, ChargeStatus.CAPTURED, ChargeStatus.REFUNDED);
		return get(id);
	}

	static Charge answer(Charge charge) {
		if (charge.status() == ChargeStatus.DECLINED) {
			throw new PaymentDeclinedException(charge);
		}
		return charge;
	}
}
