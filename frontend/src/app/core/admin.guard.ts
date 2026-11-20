import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SessionService } from './session.service';

/** A Administração é só do PLATFORM_ADMIN (UX/UI, seção 7.10); os demais voltam para a home. */
export const adminGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  return session.isPlatformAdmin() ? true : inject(Router).createUrlTree(['/home']);
};
