package br.com.aquaqe.platform.livingdocs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.identity.UserDirectory;
import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.workitem.Artifacts;
import br.com.aquaqe.platform.workitem.Artifacts.ArtifactRef;

/**
 * Anexos dos artefatos (SSD 3.7; PRD-RF-018; #13, REV-08). Ler e baixar seguem a leitura do artefato; enviar e remover,
 * a edição no nível dele. Remover só marca o anexo, que some da lista e do download; o conteúdo fica para a auditoria.
 */
@Service
class AttachmentService {

	static final int FILE_NAME_MAX = 255;
	private static final int CONTENT_TYPE_MAX = 150;
	private static final String DEFAULT_TYPE = "application/octet-stream";
	private static final String SELECT = """
			select id, artifact_id, file_name, content_type, size_bytes, storage_key, uploaded_by, uploaded_at
			  from livingdocs.attachment
			""";

	private final JdbcClient jdbc;
	private final AttachmentStorage storage;
	private final Artifacts artifacts;
	private final UserDirectory users;
	private final DomainEvents events;
	private final long maxBytes;

	AttachmentService(JdbcClient jdbc, AttachmentStorage storage, Artifacts artifacts, UserDirectory users,
			DomainEvents events, @Value("${aquaqe.attachments.max-size:10MB}") DataSize maxSize) {
		this.jdbc = jdbc;
		this.storage = storage;
		this.artifacts = artifacts;
		this.users = users;
		this.events = events;
		this.maxBytes = maxSize.toBytes();
	}

	record Row(UUID id, UUID artifactId, String fileName, String contentType, long size, String storageKey,
			UUID uploadedBy, Instant uploadedAt) {
	}

	record Content(String fileName, String contentType, byte[] bytes) {
	}

	Map<String, Object> list(String ref) {
		ArtifactRef artifact = artifacts.readable(ref);
		List<Row> rows = jdbc
			.sql(SELECT + " where artifact_id = :artifact and removed_at is null order by uploaded_at desc, id")
			.param("artifact", artifact.id())
			.query(AttachmentService::row)
			.list();
		Map<UUID, UserRef> people = users.find(rows.stream().map(Row::uploadedBy).distinct().toList());
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("artifactKey", artifact.key());
		result.put("canEdit", !artifact.archived() && artifacts.canEdit(artifact));
		result.put("maxSize", maxBytes);
		result.put("items", rows.stream().map(r -> view(r, people)).toList());
		return result;
	}

	@Transactional
	Map<String, Object> upload(String ref, String originalName, String contentType, byte[] bytes) {
		ArtifactRef artifact = editable(ref);
		if (bytes == null || bytes.length == 0) {
			throw ApiException.validation("file", "envie um arquivo com conteúdo");
		}
		if (bytes.length > maxBytes) {
			throw tooLarge(maxBytes);
		}
		String fileName = fileName(originalName);
		String type = contentType(contentType);
		UUID id = UUID.randomUUID();
		String key = "attachments/" + id;
		storage.put(key, bytes);
		jdbc.sql("""
				insert into livingdocs.attachment (id, artifact_id, file_name, content_type, size_bytes, storage_key,
				                                   uploaded_by)
				values (:id, :artifact, :name, :type, :size, :key, :by)
				""")
			.param("id", id)
			.param("artifact", artifact.id())
			.param("name", fileName)
			.param("type", type)
			.param("size", (long) bytes.length)
			.param("key", key)
			.param("by", artifact.actorId())
			.update();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("artifactKey", artifact.key());
		payload.put("fileName", fileName);
		payload.put("contentType", type);
		payload.put("size", bytes.length);
		events.publish("attachment.uploaded", artifact.actorId(), artifact.orgUnitId(), aggregate(id, artifact), payload);
		Row row = find(id).orElseThrow();
		return view(row, users.find(List.of(row.uploadedBy())));
	}

	Content content(UUID id) {
		Row row = find(id).orElseThrow(ApiException::notFound);
		artifacts.readable(row.artifactId().toString());
		byte[] bytes = storage.get(row.storageKey()).orElseThrow(ApiException::notFound);
		return new Content(row.fileName(), row.contentType(), bytes);
	}

	@Transactional
	void remove(UUID id) {
		Row row = find(id).orElseThrow(ApiException::notFound);
		ArtifactRef artifact = editable(row.artifactId().toString());
		jdbc.sql("update livingdocs.attachment set removed_at = now(), removed_by = :by where id = :id and removed_at is null")
			.param("by", artifact.actorId())
			.param("id", id)
			.update();
		events.publish("attachment.removed", artifact.actorId(), artifact.orgUnitId(), aggregate(id, artifact),
			Map.of("artifactKey", artifact.key(), "fileName", row.fileName()));
	}

	static ApiException tooLarge(long maxBytes) {
		return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_TOO_LARGE",
			"Arquivo acima do tamanho permitido (" + (maxBytes / (1024 * 1024)) + " MB).");
	}

	/** Só o nome, sem caminho nem caracteres de controle; até {@link #FILE_NAME_MAX} (mantém o fim, com a extensão). */
	static String fileName(String original) {
		String name = original == null ? "" : original.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
		if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
			throw ApiException.validation("file", "o arquivo precisa de um nome");
		}
		return name.length() > FILE_NAME_MAX ? name.substring(name.length() - FILE_NAME_MAX) : name;
	}

	/** O tipo informado no envio, sem parâmetros; inválido ou ausente vira {@value #DEFAULT_TYPE}. */
	static String contentType(String declared) {
		String type = declared == null ? "" : declared.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
		boolean valid = type.length() <= CONTENT_TYPE_MAX
			&& type.matches("[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*");
		return valid ? type : DEFAULT_TYPE;
	}

	/** O artefato, se o usuário o edita e ele não está arquivado. */
	private ArtifactRef editable(String ref) {
		ArtifactRef artifact = artifacts.readable(ref);
		artifacts.requireEdit(artifact);
		if (artifact.archived()) {
			throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_NOT_EDITABLE", "O artefato está arquivado.");
		}
		return artifact;
	}

	private Optional<Row> find(UUID id) {
		return jdbc.sql(SELECT + " where id = :id and removed_at is null")
			.param("id", id)
			.query(AttachmentService::row)
			.optional();
	}

	private static Row row(ResultSet rs, int n) throws SQLException {
		return new Row(rs.getObject("id", UUID.class), rs.getObject("artifact_id", UUID.class), rs.getString("file_name"),
			rs.getString("content_type"), rs.getLong("size_bytes"), rs.getString("storage_key"),
			rs.getObject("uploaded_by", UUID.class), rs.getTimestamp("uploaded_at").toInstant());
	}

	private static Map<String, Object> view(Row row, Map<UUID, UserRef> people) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("id", row.id());
		item.put("fileName", row.fileName());
		item.put("contentType", row.contentType());
		item.put("size", row.size());
		UserRef by = people.get(row.uploadedBy());
		item.put("uploadedBy", by == null ? null : Map.of("id", by.id(), "name", by.name()));
		item.put("uploadedAt", row.uploadedAt());
		return item;
	}

	private static Aggregate aggregate(UUID id, ArtifactRef artifact) {
		return new Aggregate("ATTACHMENT", id, artifact.key());
	}
}
