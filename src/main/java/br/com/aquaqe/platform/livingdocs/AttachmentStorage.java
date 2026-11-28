package br.com.aquaqe.platform.livingdocs;

import java.util.Optional;

/**
 * Onde fica o conteúdo dos anexos (#13). A chave é opaca: o MVP grava no banco ({@link DatabaseAttachmentStorage}); o
 * armazenamento de objetos do SAD entra como outra implementação, sem mudar a API.
 */
interface AttachmentStorage {

	void put(String key, byte[] content);

	Optional<byte[]> get(String key);
}
