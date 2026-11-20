package br.com.aquaqe.platform.organization;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.organization.OrganizationRepository.Level;
import br.com.aquaqe.platform.organization.OrganizationRepository.Unit;
import br.com.aquaqe.platform.organization.OrganizationRepository.UnitRef;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.shared.PageRequest;
import br.com.aquaqe.platform.shared.PageResponse;

/** Regras da estrutura organizacional (SSD, seção 3.2). */
@Service
class OrganizationService {

	static final int NAME_MAX = 120;

	private final OrganizationRepository repository;
	private final ConfigurationGuard guard;

	OrganizationService(OrganizationRepository repository, ConfigurationGuard guard) {
		this.repository = repository;
		this.guard = guard;
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
		guard.requireManage(parent.id());
		if (!parent.active()) {
			throw ApiException.validation("parentId", "unidade desativada");
		}
		if (parent.level().position() >= level.position()) {
			throw ApiException.validation("level", "o nível do pai deve ter posição anterior à do nível da unidade");
		}
		return unit(repository.insertUnit(level.key(), cleanName, parent));
	}

	@Transactional
	Unit update(UUID id, String name, Boolean active, Integer version) {
		Unit current = unit(id);
		guard.requireManage(current.id());
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
	}

	@Transactional
	void unlink(UUID unitId, UUID servesUnitId) {
		Unit unit = unit(unitId);
		guard.requireManage(unit.id());
		repository.deleteLink(unit.id(), servesUnitId);
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
