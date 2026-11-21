package br.com.aquaqe.platform.shared;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ID de correlação (SSD 1.2): vem do cabeçalho {@code X-Correlation-Id} ou é gerado; volta na resposta, vai para o log
 * e para os eventos e a auditoria.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class Correlation extends OncePerRequestFilter {

	public static final String HEADER = "X-Correlation-Id";
	private static final String ATTRIBUTE = Correlation.class.getName();
	/** Só valores simples são aceitos do cliente; o resto é substituído por um novo id. */
	private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

	/** O ID de correlação da requisição corrente, se houver. */
	public static Optional<String> current() {
		RequestAttributes request = RequestContextHolder.getRequestAttributes();
		return request == null ? Optional.empty()
				: Optional.ofNullable((String) request.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String informed = request.getHeader(HEADER);
		String id = informed != null && VALID.matcher(informed).matches() ? informed : UUID.randomUUID().toString();
		request.setAttribute(ATTRIBUTE, id);
		response.setHeader(HEADER, id);
		MDC.put("correlationId", id);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove("correlationId");
		}
	}
}
