package br.com.aquaqe.platform.privacy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.identity.UserDirectory;
import br.com.aquaqe.platform.identity.UserDirectory.Account;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.shared.PersonalData;

/**
 * Exportar e anonimizar os dados de uma pessoa (#32; REV-14; SSD 2.2). Só o administrador da plataforma na unidade raiz,
 * como a trilha de auditoria. As duas ações vão para a trilha sem o nome nem o e-mail da pessoa: só o id e as seções.
 */
@Service
class PrivacyService {

	static final int DIRECTORY_LIMIT = 50;

	private final List<PersonalData> holders;
	private final UserDirectory users;
	private final CurrentUser currentUser;
	private final Authorization authorization;
	private final OrgUnits orgUnits;
	private final DomainEvents events;

	PrivacyService(List<PersonalData> holders, UserDirectory users, CurrentUser currentUser, Authorization authorization,
			OrgUnits orgUnits, DomainEvents events) {
		this.holders = holders.stream().sorted(Comparator.comparingInt(PersonalData::order)
			.thenComparing(PersonalData::section)).toList();
		this.users = users;
		this.currentUser = currentUser;
		this.authorization = authorization;
		this.orgUnits = orgUnits;
		this.events = events;
	}

	@Transactional(readOnly = true)
	List<Account> directory(String query) {
		requireAdmin();
		return users.directory(query, DIRECTORY_LIMIT);
	}

	@Transactional
	Map<String, Object> export(UUID personId) {
		UUID admin = requireAdmin();
		users.anonymized(personId).orElseThrow(ApiException::notFound);
		Map<String, Object> sections = new LinkedHashMap<>();
		for (PersonalData holder : holders) {
			sections.put(holder.section(), holder.export(personId));
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("personId", personId);
		result.put("exportedAt", Instant.now());
		result.put("sections", sections);
		events.publish("person.data_exported", admin, orgUnits.rootId(), new Aggregate("PERSON", personId, null),
			Map.of("sections", List.copyOf(sections.keySet())));
		return result;
	}

	@Transactional
	Map<String, Object> anonymize(UUID personId) {
		UUID admin = requireAdmin();
		boolean already = users.anonymized(personId).orElseThrow(ApiException::notFound);
		if (personId.equals(admin)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CANNOT_ANONYMIZE_SELF",
				"Você não pode anonimizar a própria conta.");
		}
		if (already) {
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ANONYMIZED", "Esta conta já foi anonimizada.");
		}
		for (PersonalData holder : holders) {
			holder.anonymize(personId);
		}
		events.publish("person.anonymized", admin, orgUnits.rootId(), new Aggregate("PERSON", personId, null),
			Map.of("sections", holders.stream().map(PersonalData::section).toList()));
		UserDirectory.UserRef ref = users.find(List.of(personId)).get(personId);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", personId);
		result.put("name", ref.name());
		result.put("active", ref.active());
		result.put("anonymized", true);
		return result;
	}

	private UUID requireAdmin() {
		UUID user = currentUser.require().id();
		if (!authorization.can(user, Permission.MANAGE_PERSONAL_DATA, orgUnits.rootId())) {
			throw ApiException.forbidden();
		}
		return user;
	}
}

/** {@code /api/v1/admin/people} (#32): diretório, exportação e anonimização. */
@RestController
@RequestMapping("/api/v1/admin/people")
class PrivacyController {

	private final PrivacyService service;
	private final tools.jackson.databind.ObjectMapper json;

	PrivacyController(PrivacyService service, tools.jackson.databind.ObjectMapper json) {
		this.service = service;
		this.json = json;
	}

	@GetMapping
	Map<String, Object> directory(@RequestParam(required = false) String q) {
		return Map.of("items", service.directory(q));
	}

	/** Baixa os dados da pessoa em JSON, como arquivo. */
	@GetMapping("/{id}/personal-data")
	ResponseEntity<String> export(@PathVariable UUID id) {
		String body = json.writerWithDefaultPrettyPrinter().writeValueAsString(service.export(id));
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_JSON)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
				.filename("dados-pessoais-" + id.toString().substring(0, 8) + ".json", StandardCharsets.UTF_8).build()
				.toString())
			.header("X-Content-Type-Options", "nosniff")
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(body);
	}

	@PostMapping("/{id}/anonymize")
	Map<String, Object> anonymize(@PathVariable UUID id) {
		return service.anonymize(id);
	}
}
