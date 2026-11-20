package br.com.aquaqe.platform.workitem;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.aquaqe.platform.configuration.ArtifactConfiguration.Status;
import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.organization.OrgUnits.OrgUnitSummary;

/** Formatos de resposta do módulo {@code workitem} (SSD 3.4). */
public final class ArtifactViews {

	private ArtifactViews() {
	}

	public record ArtifactView(UUID id, String key, String type, String level, String title, String description,
			ParentRef parent, UnitRef orgUnit, StatusRef status, Person owner, Person technicalLead, Person qaOwner,
			Person assignee, String size, Map<String, Object> customFields, long childCount, boolean archived,
			int version, Instant createdAt, Instant updatedAt) {
	}

	public record ParentRef(UUID id, String key, String type, String title) {
	}

	public record UnitRef(UUID id, String name, String level) {

		static UnitRef of(OrgUnitSummary unit) {
			return unit == null ? null : new UnitRef(unit.id(), unit.name(), unit.levelKey());
		}
	}

	public record StatusRef(String key, String name, String phase, String category) {

		static StatusRef of(Status status) {
			return status == null ? null : new StatusRef(status.key(), status.name(), status.phase(), status.category());
		}
	}

	public record Person(UUID id, String name) {

		static Person of(UserRef user) {
			return user == null ? null : new Person(user.id(), user.name());
		}
	}

	/** Contexto herdado: resumo dos ancestrais, da raiz até o pai, e as RNs aplicáveis. */
	public record ContextView(List<Ancestor> ancestors, List<Object> businessRules) {

		public record Ancestor(String key, String type, String title, String status, String summary) {
		}
	}
}
