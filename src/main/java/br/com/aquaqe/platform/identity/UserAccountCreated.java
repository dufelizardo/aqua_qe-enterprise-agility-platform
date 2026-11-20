package br.com.aquaqe.platform.identity;

/** Publicado na mesma transação em que a conta é criada, no primeiro acesso do usuário. */
public record UserAccountCreated(UserAccount account) {
}
