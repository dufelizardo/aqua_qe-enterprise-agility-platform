import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AttachmentsService, attachmentUrl, formatSize } from './attachments.service';

describe('anexos do artefato (#13)', () => {
  let api: AttachmentsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AttachmentsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('o tamanho aparece em B, KB ou MB', () => {
    expect(formatSize(512)).toBe('512 B');
    expect(formatSize(12 * 1024)).toBe('12 KB');
    expect(formatSize(10 * 1024 * 1024)).toBe('10,0 MB');
  });

  it('o download usa o endereço do conteúdo', () => {
    expect(attachmentUrl('a-1')).toBe('/api/v1/attachments/a-1/content');
  });

  it('o envio manda o arquivo no campo file, em multipart', async () => {
    const file = new File(['olá'], 'plano.txt', { type: 'text/plain' });
    const sent = api.upload('INIT-1', file);
    const req = http.expectOne('/api/v1/artifacts/INIT-1/attachments');
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect((body.get('file') as File).name).toBe('plano.txt');
    req.flush({ id: 'a-1', fileName: 'plano.txt' });
    expect((await sent).id).toBe('a-1');
  });

  it('lista e remove pelo id', async () => {
    const listed = api.list('INIT-1');
    http.expectOne('/api/v1/artifacts/INIT-1/attachments').flush({ artifactKey: 'INIT-1', canEdit: true, maxSize: 1, items: [] });
    expect((await listed).items).toEqual([]);
    const removed = api.remove('a-1');
    const req = http.expectOne('/api/v1/attachments/a-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await removed;
  });
});
