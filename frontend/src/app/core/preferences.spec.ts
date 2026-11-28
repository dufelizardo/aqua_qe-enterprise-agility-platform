import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DEFAULT_PREFERENCES, PreferencesService, resolveTheme } from './preferences.service';

describe('Preferências do usuário (#15)', () => {
  afterEach(() => {
    document.documentElement.removeAttribute('data-theme');
    localStorage.removeItem('aquaqe.theme');
  });

  it('o tema do sistema segue o sistema; claro e escuro são fixos', () => {
    expect(resolveTheme('SYSTEM', true)).toBe('dark');
    expect(resolveTheme('SYSTEM', false)).toBe('light');
    expect(resolveTheme('DARK', false)).toBe('dark');
    expect(resolveTheme('LIGHT', true)).toBe('light');
  });

  it('grava no servidor, aplica o tema e guarda a cópia no navegador', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(PreferencesService);
    const http = TestBed.inject(HttpTestingController);
    const next = { theme: 'DARK' as const, notifications: { mention: false, reply: true } };

    const saving = service.save(next);
    const request = http.expectOne('/api/v1/me/preferences');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(next);
    request.flush(next);
    await saving;

    expect(service.preferences()).toEqual(next);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem('aquaqe.theme')).toBe('DARK');

    service.stop();
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
    http.verify();
  });

  it('sem resposta do servidor, ficam os padrões', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(PreferencesService);
    const http = TestBed.inject(HttpTestingController);

    const loading = service.load();
    http.expectOne('/api/v1/me/preferences').flush(null, { status: 500, statusText: 'Erro' });
    await loading;

    expect(service.preferences()).toEqual(DEFAULT_PREFERENCES);
    http.verify();
  });
});
