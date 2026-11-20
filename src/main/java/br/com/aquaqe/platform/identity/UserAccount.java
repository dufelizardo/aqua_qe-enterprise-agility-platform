package br.com.aquaqe.platform.identity;

import java.util.UUID;

/** Conta local do usuário, criada no primeiro acesso a partir dos dados do token (SSD, seção 1.2). */
public record UserAccount(UUID id, String subject, String username, String name, String email) {
}
