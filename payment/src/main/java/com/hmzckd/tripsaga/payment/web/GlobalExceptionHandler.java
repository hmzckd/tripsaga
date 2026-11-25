package com.hmzckd.tripsaga.payment.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

	@ExceptionHandler(PaymentDeclinedException.class)
	ProblemDetail handleDeclined(PaymentDeclinedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
		problem.setProperty("chargeId", ex.charge().id());
		return problem;
	}

	@ExceptionHandler(UnprocessableException.class)
	ProblemDetail handleUnprocessable(UnprocessableException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}
}
