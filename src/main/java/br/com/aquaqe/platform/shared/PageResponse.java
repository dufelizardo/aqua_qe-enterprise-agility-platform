package br.com.aquaqe.platform.shared;

import java.util.List;

/** Resposta de listagem (SSD, seção 1.4). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, long totalPages) {

	public static <T> PageResponse<T> of(List<T> items, PageRequest request, long totalItems) {
		long pages = totalItems == 0 ? 0 : (totalItems + request.size() - 1) / request.size();
		return new PageResponse<>(items, request.page(), request.size(), totalItems, pages);
	}
}
