import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

import { authGuard } from './core/auth.guard';
import { LOGIN_URL, SessionService } from './core/session.service';

describe('SessionService e authGuard', () => {
  let session: SessionService;
  let http: HttpTestingController;
  let redirect: ReturnType<typeof vi.spyOn>;

  const runGuard = () =>
    TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    session = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
    redirect = vi.spyOn(session, 'redirect').mockImplementation(() => undefined);
  });

  afterEach(() => http.verify());

  const pm = { id: '1', username: 'robot.pm', name: 'Paula Produto', email: 'robot.pm@aquaqe.local', assignments: [] };
  const nextTick = () => new Promise((resolve) => setTimeout(resolve));

  it('carrega o usuário corrente e calcula as iniciais', async () => {
    const loading = session.load();
    http.expectOne('/api/v1/me').flush(pm);
    await nextTick();
    http.expectOne('/api/v1/dev/personas').flush(null, { status: 404, statusText: 'Not Found' });
    await loading;

    expect(session.devMode()).toBe(false);
    expect(session.authenticated()).toBe(true);
    expect(session.displayName()).toBe('Paula Produto');
    expect(session.initials()).toBe('PP');
    expect(runGuard()).toBe(true);
    expect(redirect).not.toHaveBeenCalled();
  });

  it('sem sessão (401) fica anônimo, e a landing continua acessível', async () => {
    const loading = session.load();
    http.expectOne('/api/v1/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await loading;

    expect(session.authenticated()).toBe(false);
    expect(redirect).not.toHaveBeenCalled();
  });

  it('no ambiente dev carrega as personas e troca de persona', async () => {
    const loading = session.load();
    http.expectOne('/api/v1/me').flush(pm);
    await nextTick();
    http.expectOne('/api/v1/dev/personas').flush({
      current: 'robot.pm',
      personas: [
        { username: 'robot.pm', name: 'Paula Produto', email: 'robot.pm@aquaqe.local', role: 'PRODUCT_MANAGER' },
        { username: 'robot.qa', name: 'Quezia Qualidade', email: 'robot.qa@aquaqe.local', role: 'QA_ENGINEER' },
      ],
    });
    await loading;
    expect(session.devMode()).toBe(true);

    const switching = session.switchPersona('robot.qa');
    const put = http.expectOne('/api/v1/dev/persona');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ username: 'robot.qa' });
    put.flush(null, { status: 204, statusText: 'No Content' });
    await nextTick();
    http.expectOne('/api/v1/me').flush({ ...pm, username: 'robot.qa', name: 'Quezia Qualidade' });
    await nextTick();
    http.expectOne('/api/v1/dev/personas').flush({ current: 'robot.qa', personas: session.devPersonas() });
    await switching;

    expect(session.displayName()).toBe('Quezia Qualidade');
  });

  it('a guarda leva ao login quando não há sessão', () => {
    expect(runGuard()).toBe(false);
    expect(redirect).toHaveBeenCalledWith(LOGIN_URL);
  });

  it('logout abre o endereço de logout devolvido pelo backend', async () => {
    const leaving = session.logout();
    const request = http.expectOne('/logout');
    expect(request.request.method).toBe('POST');
    request.flush({ redirectUrl: 'http://localhost:8180/realms/aquaqe-dev/protocol/openid-connect/logout' });
    await leaving;

    expect(redirect).toHaveBeenCalledWith('http://localhost:8180/realms/aquaqe-dev/protocol/openid-connect/logout');
  });
});
