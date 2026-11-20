package br.com.aquaqe.platform.workitem;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.aquaqe.platform.configuration.ArtifactConfiguration.ArtifactType;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.workitem.ArtifactRepository.Row;
import br.com.aquaqe.platform.workitem.ArtifactService.Caller;
import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;

/**
 * API pública do módulo {@code workitem} para os módulos que agem sobre artefatos sem ser donos deles (transições,
 * gates, documentos). Aplica as mesmas regras de leitura e edição dos endpoints de artefatos (SSD 2 e 2.1).
 */
@Service
public class Artifacts {

	private final ArtifactService service;
	private final ArtifactRepository repository;

	Artifacts(ArtifactService service, ArtifactRepository repository) {
		this.service = service;
		this.repository = repository;
	}

	/** O artefato, se o usuário corrente pode lê-lo; senão {@code 404 NOT_FOUND} (SSD 2.1, regra 7). */
	public ArtifactRef readable(String ref) {
		Caller caller = service.caller();
		Row row = service.readable(ref, caller);
		ArtifactType type = service.typeOf(row);
		return new ArtifactRef(row.id(), row.key(), type.key(), type.level(), row.orgUnitId(), row.statusId(),
			row.version(), row.archived(), caller.userId());
	}

	/** O usuário corrente pode editar o artefato no nível dele (matriz do SSD 2)? */
	public boolean canEdit(ArtifactRef artifact) {
		try {
			requireEdit(artifact);
			return true;
		} catch (ApiException forbidden) {
			return false;
		}
	}

	/** @throws ApiException {@code 403 FORBIDDEN} se o usuário lê, mas não edita */
	public void requireEdit(ArtifactRef artifact) {
		Row row = repository.find(artifact.id()).orElseThrow(ApiException::notFound);
		service.requireEdit(service.caller(), row, service.typeOf(row));
	}

	/** Grava o novo status. @return false se a versão não confere */
	public boolean changeStatus(UUID artifactId, UUID statusId, int expectedVersion) {
		return repository.changeStatus(artifactId, statusId, expectedVersion);
	}

	/** O artefato no formato da API (SSD 3.4). */
	public ArtifactView view(UUID artifactId) {
		return service.views(List.of(repository.find(artifactId).orElseThrow(ApiException::notFound))).getFirst();
	}

	/** Referência a um artefato legível pelo usuário corrente ({@code actorId}). */
	public record ArtifactRef(UUID id, String key, String type, String level, UUID orgUnitId, UUID statusId, int version,
			boolean archived, UUID actorId) {
	}
}
