package br.com.aquaqe.platform.livingdocs;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.aquaqe.platform.shared.ApiException;

/**
 * API pública do módulo {@code livingdocs} para quem só precisa saber se uma página do Wiki existe para o usuário
 * corrente (Home, "Continuar de onde parou", #83). Aplica as mesmas regras de leitura de {@code GET /pages/{id}}.
 */
@Service
public class Pages {

	private final PageService service;

	Pages(PageService service) {
		this.service = service;
	}

	/** A página, se o usuário corrente pode lê-la; vazio se não existe ou não é legível (SSD 2.1). */
	public Optional<PageRef> readable(UUID id) {
		try {
			return Optional.of(service.ref(id));
		} catch (ApiException notReadable) {
			return Optional.empty();
		}
	}

	/** Página legível: id, título, unidade e tipo ({@code PAGE} ou {@code REGIMENTO}). */
	public record PageRef(UUID id, String title, UUID orgUnitId, String type) {
	}
}
