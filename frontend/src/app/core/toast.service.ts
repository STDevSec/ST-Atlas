import { Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

export interface Toast { id: number; kind: 'ok' | 'error' | 'info'; text: string; }

/** Small, non-blocking notifications (aria-live region in the shell). */
@Injectable({ providedIn: 'root' })
export class Toasts {
  readonly items = signal<Toast[]>([]);
  private next = 1;

  show(text: string, kind: Toast['kind'] = 'info', ms = 4000): void {
    const t = { id: this.next++, kind, text };
    this.items.update((l) => [...l, t]);
    setTimeout(() => this.dismiss(t.id), ms);
  }

  ok(text: string): void { this.show(text, 'ok'); }

  /** Shows the server's safe message (problem detail) or a generic one. */
  error(err: unknown, fallback: string): void {
    let text = fallback;
    if (err instanceof HttpErrorResponse && err.error && typeof err.error === 'object') {
      const p = err.error as { detail?: string; errors?: { field: string; message: string }[] };
      text = p.errors?.length ? p.errors.map((e) => `${e.field}: ${e.message}`).join(' · ') : (p.detail ?? fallback);
    }
    this.show(text, 'error', 6000);
  }

  dismiss(id: number): void {
    this.items.update((l) => l.filter((t) => t.id !== id));
  }
}
