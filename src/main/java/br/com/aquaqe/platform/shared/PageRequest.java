package br.com.aquaqe.platform.shared;

/** Parâmetros {@code page} e {@code size} de listagem (SSD, seção 1.4). */
public record PageRequest(int page, int size) {

	public static final int MAX_SIZE = 100;

	public static PageRequest of(Integer page, Integer size) {
		int p = page == null ? 0 : page;
		int s = size == null ? 20 : size;
		if (p < 0) {
			throw ApiException.validation("page", "deve ser maior ou igual a 0");
		}
		if (s < 1 || s > MAX_SIZE) {
			throw ApiException.validation("size", "deve estar entre 1 e " + MAX_SIZE);
		}
		return new PageRequest(p, s);
	}

	public long offset() {
		return (long) page * size;
	}
}
