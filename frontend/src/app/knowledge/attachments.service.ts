import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Anexo de um artefato (SSD 3.7; #13, REV-08). */
export interface Attachment {
  id: string;
  fileName: string;
  contentType: string;
  size: number;
  uploadedBy: { id: string; name: string } | null;
  uploadedAt: string;
}

export interface AttachmentList {
  artifactKey: string;
  canEdit: boolean;
  /** Tamanho máximo de cada anexo, em bytes. */
  maxSize: number;
  items: Attachment[];
}

const API = '/api/v1';

/** Endereço do download (sempre como arquivo baixado, com o nome original). */
export function attachmentUrl(id: string): string {
  return `${API}/attachments/${id}/content`;
}

/** "512 B", "12 KB", "3,4 MB". */
export function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}

@Injectable({ providedIn: 'root' })
export class AttachmentsService {
  private readonly http = inject(HttpClient);

  list(artifact: string): Promise<AttachmentList> {
    return firstValueFrom(this.http.get<AttachmentList>(`${API}/artifacts/${artifact}/attachments`));
  }

  upload(artifact: string, file: File): Promise<Attachment> {
    const form = new FormData();
    form.append('file', file, file.name);
    return firstValueFrom(this.http.post<Attachment>(`${API}/artifacts/${artifact}/attachments`, form));
  }

  remove(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${API}/attachments/${id}`));
  }
}
