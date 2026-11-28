package br.com.aquaqe.platform.livingdocs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.aquaqe.platform.shared.ApiException;

/** Anexos dos artefatos (SSD 3.7; #13, REV-08). */
@RestController
class AttachmentController {

	private final AttachmentService service;

	AttachmentController(AttachmentService service) {
		this.service = service;
	}

	@GetMapping("/api/v1/artifacts/{artifact}/attachments")
	Map<String, Object> list(@PathVariable String artifact) {
		return service.list(artifact);
	}

	@PostMapping(path = "/api/v1/artifacts/{artifact}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	Map<String, Object> upload(@PathVariable String artifact,
			@RequestParam(name = "file", required = false) MultipartFile file) throws IOException {
		if (file == null) {
			throw ApiException.validation("file", "obrigatório");
		}
		return service.upload(artifact, file.getOriginalFilename(), file.getContentType(), file.getBytes());
	}

	/**
	 * Sempre como download ({@code attachment}) e com {@code nosniff}: o navegador não abre o arquivo como página da
	 * plataforma, mesmo que o tipo informado no envio seja HTML ou SVG.
	 */
	@GetMapping("/api/v1/attachments/{id}/content")
	ResponseEntity<byte[]> content(@PathVariable UUID id) {
		AttachmentService.Content content = service.content(id);
		ContentDisposition disposition = ContentDisposition.attachment()
			.filename(content.fileName(), StandardCharsets.UTF_8)
			.build();
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(content.contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
			.header("X-Content-Type-Options", "nosniff")
			.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
			.body(content.bytes());
	}

	@DeleteMapping("/api/v1/attachments/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@PathVariable UUID id) {
		service.remove(id);
	}
}
