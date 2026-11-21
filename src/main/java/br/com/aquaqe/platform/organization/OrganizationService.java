package br.com.aquaqe.platform.organization;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.organization.OrganizationRepository.Level;
import br.com.aquaqe.platform.organization.OrganizationRepository.Unit;
import br.com.aquaqe.platform.organization.OrganizationRepository.UnitRef;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.shared.PageRequest;
import br.com.aquaqe.platform.shared.PageResponse;

/** Regras da estrutura organizacional (SSD, seção 3.2). */
@Service
class OrganizationService {

	static final int NAME_MAX = 120;

	private final OrganizationRepository repository;
	private final ConfigurationGuard guard;
	private final DomainEvents events;

	OrganizationService(OrganizationRepository repository, ConfigurationGuard guard, DomainEvents events) {
		this.repository = repository;
		this.guard = guard;
		this.events = events;
	}

	List<Level> levels() {
		return repository.levels();
	}

	PageResponse<Unit> units(UUID parentId, String levelKey, PageRequest page) {
		List<Unit> items = repository.units(parentId, levelKey, page.offset(), page.size());
		return PageResponse.of(items, page, repository.countUnits(parentId, levelKey));
	}

	Unit unit(UUID id) {
		return repository.unit(id).orElseThrow(ApiException::notFound);
	}

	/** Da raiz até o pai da unidade (o caminho exibido no detalhe). */
	List<UnitRef> pathOf(Unit unit) {
		List<UUID> lineage = unit.lineage();
		return repository.refs(lineage.subList(0, lineage.size() - 1));
	}

	Links links(UUID id) {
		unit(id);
		return new Links(repository.serves(id), repository.servedBy(id));
	}

	@Transactional
	Unit create(String name, String levelKey, UUID parentId) {
		String cleanName = requireName(name);
		if (levelKey == null || levelKey.isBlank()) {
			throw ApiException.validation("level", "obrigatório");
		}
		Level level = repository.level(levelKey)
			.orElseThrow(() -> ApiException.validation("level", "nível inexistente"));
		if (parentId == null) {
			throw ApiException.validation("parentId", "obrigatório: a instalação tem uma única raiz");
		}
		Unit parent = repository.unit(parentId)
			.orElseThrow(() -> ApiException.validation("parentId", "unidade inexistente"));
		UUID actor = guard.requireManage(parent.id());
		if (!parent.active()) {
			throw ApiException.validation("parentId", "unidade desativada");
		}
		if (parent.level().position() >= level.position()) {
			throw ApiException.validation("level", "o nível do pai deve ter posição anterior à do nível da unidade");
		}
		Unit created = unit(repository.insertUnit(level.key(), cleanName, parent));
		events.publish("org_unit.created", actor, created.id(), new Aggregate("ORG_UNIT", created.id(), null),
			java.util.Map.of("orgUnitId", created.id().toString(), "level", level.key(), "parentId", parent.id().toString(),
				"name", cleanName),
			null, java.util.Map.of("name", cleanName, "level", level.key(), "parentId", parent.id().toString()));
		return created;
	}

	@Transactional
	Unit update(UUID id, String name, Boolean active, Integer version) {
		Unit current = unit(id);
		UUID actor = guard.requireManage(current.id());
		if (version == null) {
			throw ApiException.validation("version", "obrigatório");
		}
		String newName = name == null ? current.name() : requireName(name);
		boolean newActive = active == null ? current.active() : active;
		if (!newActive && current.parentId() == null) {
			throw ApiException.validation("active", "a unidade raiz não pode ser desativada");
		}
		if (!repository.updateUnit(id, newName, newActive, version)) {
			throw ApiException.versionConflict();
		}
		events.publish("org_unit.updated", actor, id, new Aggregate("ORG_UNIT", id, null),
			java.util.Map.of("orgUnitId", id.toString()),
			java.util.Map.of("name", current.name(), "active", current.active()),
			java.util.Map.of("name", newName, "active", newActive));
		return unit(id);
	}

	@Transactional
	void link(UUID unitId, UUID servesUnitId) {
		Unit unit = unit(unitId);
		UUID actor = guard.requireManage(unit.id());
		Unit target = repository.unit(servesUnitId).orElseThrow(ApiException::notFound);
		if (unit.sameLineAs(target)) {
			throw ApiException.validation("targetId",
				"as unidades estão na mesma linha da árvore; o vínculo só liga ramos diferentes");
		}
		if (!unit.active() || !target.active()) {
			throw ApiException.validation("targetId", "unidade desativada");
		}
		repository.insertLink(unit.id(), target.id(), actor);
		events.publish("org_unit.link_added", actor, unit.id(), new Aggregate("ORG_UNIT", unit.id(), null),
			java.util.Map.of("orgUnitId", unit.id().toString(), "servesOrgUnitId", target.id().toString()), null,
			java.util.Map.of("serves", target.id().toString()));
	}

	@Transactional
	void unlink(UUID unitId, UUID servesUnitId) {
		Unit unit = unit(unitId);
		UUID actor = guard.requireManage(unit.id());
		repository.deleteLink(unit.id(), servesUnitId);
		events.publish("org_unit.link_removed", actor, unit.id(), new Aggregate("ORG_UNIT", unit.id(), null),
			java.util.Map.of("orgUnitId", unit.id().toString(), "servesOrgUnitId", servesUnitId.toString()),
			java.util.Map.of("serves", servesUnitId.toString()), null);
	}

	private static String requireName(String name) {
		String clean = name == null ? "" : name.strip();
		if (clean.isEmpty()) {
			throw ApiException.validation("name", "obrigatório");
		}
		if (clean.length() > NAME_MAX) {
			throw ApiException.validation("name", "no máximo " + NAME_MAX + " caracteres");
		}
		return clean;
	}

	record Links(List<UnitRef> serves, List<UnitRef> servedBy) {
	}
}
