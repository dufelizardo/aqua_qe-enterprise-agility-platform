package br.com.aquaqe.platform.shared;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/** Erro de negócio com código estável (SSD, seção 1.6). O {@link ApiExceptionHandler} o converte em Problem Details. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final List<FieldError> errors;
	private final Map<String, Object> properties;

	public ApiException(HttpStatus status, String code, String detail, List<FieldError> errors) {
		super(detail);
		this.status = status;
		this.code = code;
		this.errors = List.copyOf(errors);
		this.properties = Map.of();
	}

	/** Erro com campos adicionais no corpo, como {@code failedRules} do {@code GATE_BLOCKED} (SSD 5.3). */
	public ApiException(HttpStatus status, String code, String detail, Map<String, Object> properties) {
		super(detail);
		this.status = status;
		this.code = code;
		this.errors = List.of();
		this.properties = Map.copyOf(properties);
	}

	public ApiException(HttpStatus status, String code, String detail) {
		this(status, code, detail, List.of());
	}

	/** Recurso inexistente ou fora do escopo de visibilidade do usuário: as duas situações respondem igual. */
	public static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso não encontrado.");
	}

	/** O usuário vê o recurso, mas não pode executar a ação. */
	public static ApiException forbidden() {
		return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Você não tem permissão para esta ação.");
	}

	public static ApiException validation(String field, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Dados inválidos.",
			List.of(new FieldError(field, message)));
	}

	public static ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
			"O recurso foi alterado por outra pessoa. Recarregue e tente de novo.");
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public List<FieldError> errors() {
		return errors;
	}

	public Map<String, Object> properties() {
		return properties;
	}

	public record FieldError(String field, String message) {
	}
}
