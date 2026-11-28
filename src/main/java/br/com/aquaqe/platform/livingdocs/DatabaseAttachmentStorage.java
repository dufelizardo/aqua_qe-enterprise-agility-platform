package br.com.aquaqe.platform.livingdocs;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Conteúdo dos anexos na tabela {@code livingdocs.attachment_blob} (MVP; #13). */
@Component
class DatabaseAttachmentStorage implements AttachmentStorage {

	private final JdbcClient jdbc;

	DatabaseAttachmentStorage(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void put(String key, byte[] content) {
		jdbc.sql("insert into livingdocs.attachment_blob (storage_key, content) values (:key, :content)")
			.param("key", key)
			.param("content", content)
			.update();
	}

	@Override
	public Optional<byte[]> get(String key) {
		return jdbc.sql("select content from livingdocs.attachment_blob where storage_key = :key")
			.param("key", key)
			.query((rs, n) -> rs.getBytes("content"))
			.optional();
	}
}
