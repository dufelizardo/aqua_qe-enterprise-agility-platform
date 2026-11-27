package br.com.aquaqe.platform.organization;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** API pública do módulo {@code organization} para os demais módulos. */
public interface OrgUnits {

	Optional<OrgUnitSummary> find(UUID id);

	/** A unidade e seus ancestrais, da raiz até ela. Vazio se a unidade não existe. */
	List<UUID> lineage(UUID id);

	UUID rootId();

	/** As unidades informadas e todas as que estão abaixo delas na árvore. */
	Set<UUID> subtrees(Collection<UUID> roots);

	/**
	 * Verdadeiro se alguma unidade de {@code from} atende, por vínculo "atende a", a alguma unidade de {@code to} (SSD 2.1,
	 * regra 4).
	 */
	boolean anyServes(Collection<UUID> from, Collection<UUID> to);

	/** Unidades ativas abaixo da unidade, em qualquer profundidade (#35, aviso ao desativar). */
	int activeDescendants(UUID id);

	/** Vínculos "atende a" da unidade, nos dois sentidos (#35). */
	int linkCount(UUID id);

	record OrgUnitSummary(UUID id, String name, String levelKey, String flightLevel, UUID parentId, boolean active) {
	}
}
