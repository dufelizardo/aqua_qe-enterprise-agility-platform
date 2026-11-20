import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';

import { SessionService } from './session.service';

/** Telas internas exigem sessão; sem ela, o usuário é levado ao login do Keycloak (pelo backend). */
export const authGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  if (session.authenticated()) {
    return true;
  }
  session.login();
  return false;
};
