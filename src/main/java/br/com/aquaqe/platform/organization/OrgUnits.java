package br.com.aquaqe.platform.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** API pública do módulo {@code organization} para os demais módulos. */
public interface OrgUnits {

	Optional<OrgUnitSummary> find(UUID id);

	/** A unidade e seus ancestrais, da raiz até ela. Vazio se a unidade não existe. */
	List<UUID> lineage(UUID id);

	UUID rootId();

	record OrgUnitSummary(UUID id, String name, String levelKey, String flightLevel, UUID parentId, boolean active) {
	}
}
