package io.luminar.ledger.api;

import io.luminar.ledger.api.dto.response.ApiErrorResponse;
import io.luminar.ledger.domain.common.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
	// 409 dipilih untuk status lifecycle karena request valid, tapi state resource konflik (frozen/closed).
	@ExceptionHandler(DomainException.class)
	public ResponseEntity<ApiErrorResponse> handleDomain(DomainException ex) {
		String code = ex.code();
		if ("ACCOUNT_FROZEN".equals(code) || "ACCOUNT_CLOSED".equals(code)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ApiErrorResponse(code, ex.getMessage()));
		}
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(new ApiErrorResponse(code, ex.getMessage()));
	}
}
