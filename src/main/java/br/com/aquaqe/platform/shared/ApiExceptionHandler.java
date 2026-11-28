package br.com.aquaqe.platform.shared;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Converte erros em Problem Details com {@code code} e {@code errors} (SSD, seção 1.6). */
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> api(ApiException ex) {
		ResponseEntity<ProblemDetail> response = problem(ex.status(), ex.code(), ex.getMessage(), ex.errors());
		ex.properties().forEach(response.getBody()::setProperty);
		return response;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex) {
		return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Corpo da requisição inválido.", List.of());
	}

	/** Arquivo acima do limite do multipart (anexos, #13): o mesmo código da checagem do serviço. */
	@ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
	ResponseEntity<ProblemDetail> tooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
		return problem(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_TOO_LARGE", "Arquivo acima do tamanho permitido.",
			List.of());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ProblemDetail> mismatch(MethodArgumentTypeMismatchException ex) {
		return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Parâmetro inválido.",
			List.of(new ApiException.FieldError(ex.getName(), "valor inválido")));
	}

	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail,
			List<ApiException.FieldError> errors) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
		body.setType(URI.create("https://aqua-qe.example/problems/" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
		body.setTitle(status.getReasonPhrase());
		body.setProperty("code", code);
		body.setProperty("errors", errors);
		Correlation.current().ifPresent(id -> body.setProperty("correlationId", id));
		return ResponseEntity.status(status).body(body);
	}
}
