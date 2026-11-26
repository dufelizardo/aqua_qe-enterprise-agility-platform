package br.com.aquaqe.platform.workitem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Authorization.RoleHolders;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.ArtifactType;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.identity.UserDirectory;
import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.organization.OrgUnits.OrgUnitSummary;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.workitem.ArtifactViews.Person;

/**
 * Responsável padrão do artefato criado sem Owner (#43; SSD 3.4). A regra é por unidade e tipo de artefato: a unidade
 * sem regra própria herda a da unidade acima mais próxima; sem regra na linhagem, o artefato nasce sem Owner.
 * <ul>
 * <li>{@code NONE}: sem Owner;</li>
 * <li>{@code CREATOR}: quem criou;</li>
 * <li>{@code ROLE}: quem tem o papel na unidade do item ou, se ninguém lá o tem, na unidade acima mais próxima. Com
 * mais de uma pessoa, o formulário pede a escolha; pela API sem {@code ownerId}, o artefato nasce sem Owner.</li>
 * </ul>
 */
@Service
class DefaultOwners {

	static final Set<String> MODES = Set.of("NONE", "CREATOR", "ROLE");

	private final JdbcClient jdbc;
	private final ArtifactConfiguration configuration;
	private final OrgUnits orgUnits;
	private final Authorization authorization;
	private final CurrentUser currentUser;
	private final UserDirectory users;
	private final DomainEvents events;

	DefaultOwners(JdbcClient jdbc, ArtifactConfiguration configuration, OrgUnits orgUnits, Authorization authorization,
			CurrentUser currentUser, UserDirectory users, DomainEvents events) {
		this.jdbc = jdbc;
		this.configuration = configuration;
		this.orgUnits = orgUnits;
		this.authorization = authorization;
		this.currentUser = currentUser;
		this.users = users;
		this.events = events;
	}

	// ------------------------------------------------------------------------------------------ configuração

	/** A regra vigente na unidade para cada tipo de artefato, com a unidade de onde ela vem. */
	@Transactional(readOnly = true)
	List<RuleView> rules(UUID unitId) {
		currentUser.require();
		unit(unitId);
		List<UUID> lineage = orgUnits.lineage(unitId);
		Map<UUID, Map<UUID, StoredRule>> byType = new LinkedHashMap<>();
		for (StoredRule rule : stored(lineage, null)) {
			byType.computeIfAbsent(rule.typeId(), k -> new LinkedHashMap<>()).put(rule.orgUnitId(), rule);
		}
		List<RuleView> result = new ArrayList<>();
		for (ArtifactType type : configuration.types()) {
			Effective effective = effective(lineage, byType.getOrDefault(type.id(), Map.of()));
			result.add(view(type, unitId, effective));
		}
		return result;
	}

	/** Grava a regra própria da unidade para o tipo. Só quem gere configuração na unidade. */
	@Transactional
	RuleView save(UUID unitId, String typeKey, Map<String, Object> body) {
		UUID user = requireManage(unitId);
		ArtifactType type = type(typeKey);
		Object modeValue = body == null ? null : body.get("mode");
		if (!(modeValue instanceof String mode) || !MODES.contains(mode)) {
			throw ApiException.validation("mode", "use NONE, CREATOR ou ROLE");
		}
		String role = null;
		if ("ROLE".equals(mode)) {
			Object roleValue = body.get("role");
			if (!(roleValue instanceof String r) || "PLATFORM_ADMIN".equals(r) || !authorization.roleExists(r)) {
				throw ApiException.validation("role", "papel desconhecido");
			}
			role = r;
		}
		jdbc.sql("""
				insert into workitem.default_owner_rule (org_unit_id, artifact_type_id, mode, role, updated_by)
				values (:unit, :type, :mode, :role, :user)
				on conflict (org_unit_id, artifact_type_id)
				do update set mode = excluded.mode, role = excluded.role, updated_by = excluded.updated_by,
				              updated_at = now()
				""")
			.param("unit", unitId).param("type", type.id()).param("mode", mode).param("role", role).param("user", user)
			.update();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("artifactType", type.key());
		payload.put("mode", mode);
		payload.put("role", role);
		events.publish("default_owner_rule.updated", user, unitId, new Aggregate("DEFAULT_OWNER_RULE", unitId, type.key()),
			payload);
		return rules(unitId).stream().filter(r -> r.artifactType().key().equals(type.key())).findFirst().orElseThrow();
	}

	/** Remove a regra própria da unidade: ela volta a herdar a de cima. */
	@Transactional
	void remove(UUID unitId, String typeKey) {
		UUID user = requireManage(unitId);
		ArtifactType type = type(typeKey);
		int removed = jdbc.sql("delete from workitem.default_owner_rule where org_unit_id = :unit and artifact_type_id = :type")
			.param("unit", unitId).param("type", type.id())
			.update();
		if (removed == 0) {
			throw ApiException.notFound();
		}
		events.publish("default_owner_rule.removed", user, unitId, new Aggregate("DEFAULT_OWNER_RULE", unitId, type.key()),
			Map.of("artifactType", type.key()));
	}

	// ------------------------------------------------------------------------------------------ aplicação

	/** O que o formulário de criação mostra no campo Owner para o tipo e a unidade. */
	@Transactional(readOnly = true)
	Suggestion suggestion(UUID unitId, String typeKey) {
		UUID caller = currentUser.require().id();
		unit(unitId);
		ArtifactType type = type(typeKey);
		Resolution resolution = resolve(unitId, type, caller);
		Map<UUID, UserRef> found = users.find(resolution.candidates());
		List<Person> candidates = resolution.candidates().stream()
			.map(found::get)
			.filter(u -> u != null && u.active())
			.map(u -> new Person(u.id(), u.name()))
			.toList();
		UUID suggested = candidates.size() == 1 ? candidates.getFirst().id() : null;
		return new Suggestion(resolution.effective().mode(), resolution.effective().role(),
			unitRef(resolution.effective().source()), candidates, suggested);
	}

	/** O Owner do artefato criado sem {@code ownerId}: a pessoa única que a regra indica, ou nulo. */
	UUID ownerFor(UUID unitId, ArtifactType type, UUID creator) {
		List<UUID> candidates = resolve(unitId, type, creator).candidates();
		if (candidates.size() != 1) {
			return null;
		}
		UserRef user = users.find(candidates).get(candidates.getFirst());
		return user != null && user.active() ? user.id() : null;
	}

	private Resolution resolve(UUID unitId, ArtifactType type, UUID creator) {
		List<UUID> lineage = orgUnits.lineage(unitId);
		Map<UUID, StoredRule> own = new LinkedHashMap<>();
		stored(lineage, type.id()).forEach(r -> own.put(r.orgUnitId(), r));
		Effective effective = effective(lineage, own);
		List<UUID> candidates = switch (effective.mode()) {
			case "CREATOR" -> List.of(creator);
			case "ROLE" -> {
				RoleHolders holders = authorization.nearestHolders(effective.role(), unitId);
				yield holders.userIds();
			}
			default -> List.of();
		};
		return new Resolution(effective, candidates);
	}

	// ------------------------------------------------------------------------------------------ apoio

	/** A regra da unidade mais funda da linhagem que tenha uma; sem nenhuma, {@code NONE} sem origem. */
	private static Effective effective(List<UUID> lineage, Map<UUID, StoredRule> byUnit) {
		for (int i = lineage.size() - 1; i >= 0; i--) {
			StoredRule rule = byUnit.get(lineage.get(i));
			if (rule != null) {
				return new Effective(rule.mode(), rule.role(), rule.orgUnitId());
			}
		}
		return new Effective("NONE", null, null);
	}

	private List<StoredRule> stored(List<UUID> lineage, UUID typeId) {
		if (lineage.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				select org_unit_id, artifact_type_id, mode, role from workitem.default_owner_rule
				 where org_unit_id in (:units) and (cast(:type as uuid) is null or artifact_type_id = :type)
				""")
			.param("units", lineage).param("type", typeId)
			.query((rs, n) -> new StoredRule(rs.getObject("org_unit_id", UUID.class),
				rs.getObject("artifact_type_id", UUID.class), rs.getString("mode"), rs.getString("role")))
			.list();
	}

	private RuleView view(ArtifactType type, UUID unitId, Effective effective) {
		return new RuleView(new TypeRef(type.key(), type.name(), type.level()), effective.mode(), effective.role(),
			!unitId.equals(effective.source()), unitRef(effective.source()));
	}

	private UnitRef unitRef(UUID id) {
		return id == null ? null : orgUnits.find(id).map(u -> new UnitRef(u.id(), u.name())).orElse(null);
	}

	private OrgUnitSummary unit(UUID id) {
		return orgUnits.find(id).orElseThrow(ApiException::notFound);
	}

	private ArtifactType type(String key) {
		return Optional.ofNullable(key).flatMap(configuration::type)
			.orElseThrow(() -> ApiException.validation("artifactType", "tipo de artefato inexistente"));
	}

	private UUID requireManage(UUID unitId) {
		UUID user = currentUser.require().id();
		unit(unitId);
		if (!authorization.can(user, Permission.MANAGE_CONFIGURATION, unitId)) {
			throw ApiException.forbidden();
		}
		return user;
	}

	private record StoredRule(UUID orgUnitId, UUID typeId, String mode, String role) {
	}

	private record Effective(String mode, String role, UUID source) {
	}

	private record Resolution(Effective effective, List<UUID> candidates) {
	}

	record TypeRef(String key, String name, String level) {
	}

	record UnitRef(UUID id, String name) {
	}

	/** Regra vigente: {@code inherited} quando vem de outra unidade (ou do padrão, sem origem). */
	record RuleView(TypeRef artifactType, String mode, String role, boolean inherited, UnitRef source) {
	}

	/** Sugestão do formulário: {@code suggestedId} quando há uma pessoa só; com várias, a escolha é de quem cria. */
	record Suggestion(String mode, String role, UnitRef source, List<Person> candidates, UUID suggestedId) {
	}
}
