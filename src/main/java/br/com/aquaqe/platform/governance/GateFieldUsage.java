package br.com.aquaqe.platform.governance;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.configuration.ArtifactConfiguration;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.TransitionScope;
import br.com.aquaqe.platform.configuration.CustomFieldUsage;

/**
 * Gates que citam um campo customizado (#17): regras {@code REQUIRED_FIELD} com {@code customFields.<chave>} em
 * transições de workflows ativos que valem para o tipo. Impede remover o campo enquanto a regra existir.
 */
@Component
class GateFieldUsage implements CustomFieldUsage {

	private final JdbcClient jdbc;
	private final ArtifactConfiguration configuration;

	GateFieldUsage(JdbcClient jdbc, ArtifactConfiguration configuration) {
		this.jdbc = jdbc;
		this.configuration = configuration;
	}

	@Override
	public List<String> usages(String artifactType, String fieldKey) {
		record Use(UUID transitionId, String gate) {
		}
		return jdbc.sql("""
				select distinct g.transition_id, g.name from governance.gate g
				  join governance.gate_rule r on r.gate_id = g.id
				 where r.type = 'REQUIRED_FIELD' and r.params ->> 'field' = :field
				""")
			.param("field", "customFields." + fieldKey)
			.query((rs, n) -> new Use(rs.getObject("transition_id", UUID.class), rs.getString("name")))
			.list()
			.stream()
			.flatMap(use -> configuration.transition(use.transitionId()).stream()
				.filter(scope -> scope.artifactTypes().contains(artifactType) && active(scope))
				.map(scope -> "gate " + use.gate() + " (" + scope.workflowName() + ")"))
			.distinct()
			.toList();
	}

	private boolean active(TransitionScope scope) {
		return configuration.workflowScope(scope.workflowId()).map(w -> w.active()).orElse(false);
	}
}
