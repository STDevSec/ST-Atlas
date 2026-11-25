import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Api } from '../core/api/api.service';
import { AuditEvent, Page } from '../core/api/models';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { EmptyState, PageHeader, Skeleton } from '../shared/ui';
import { IconComponent } from '../shared/icon.component';

/** Read-only, append-only audit trail (manager-only in the nav; backend enforces access). */
@Component({
  selector: 'app-audit',
  imports: [DatePipe, TPipe, PageHeader, EmptyState, Skeleton, IconComponent],
  template: `
  <app-page-header [title]="'nav.audit' | t" />
  @if (page(); as p) {
    @if (p.items.length) {
      <div class="card overflow-hidden"><table class="table">
        <thead><tr><th>{{ 'audit.when' | t }}</th><th>{{ 'audit.action' | t }}</th><th>{{ 'audit.entity' | t }}</th><th>{{ 'audit.actor' | t }}</th><th>{{ 'audit.ip' | t }}</th></tr></thead>
        <tbody>@for (e of p.items; track e.id) {
          <tr>
            <td class="faint whitespace-nowrap">{{ e.at | date: 'short' }}</td>
            <td class="font-medium">{{ readable(e.action) }}</td>
            <td class="muted">{{ e.entityType ?? '—' }}<span class="faint font-mono text-xs">{{ e.entityId ? ' · ' + short(e.entityId) : '' }}</span></td>
            <td class="muted">{{ e.actorId ? short(e.actorId) : ('audit.system' | t) }}</td>
            <td class="faint font-mono text-xs">{{ e.ip ?? '—' }}</td>
          </tr>
        }</tbody></table></div>
      @if (p.total > p.size) {
        <div class="flex items-center justify-end gap-2 mt-3">
          <span class="faint text-sm">{{ p.page * p.size + 1 }}–{{ p.page * p.size + p.items.length }} / {{ p.total }}</span>
          <button class="btn btn-sm" [disabled]="p.page === 0" (click)="go(p.page - 1)"><app-icon name="ChevronLeft" />{{ 'common.previous' | t }}</button>
          <button class="btn btn-sm" [disabled]="(p.page + 1) * p.size >= p.total" (click)="go(p.page + 1)">{{ 'common.next' | t }}<app-icon name="ChevronRight" /></button>
        </div>
      }
    } @else { <div class="card"><app-empty [text]="'common.noResults' | t" /></div> }
  } @else { <div class="card"><app-skeleton [rows]="8" /></div> }`,
})
export class AuditPage {
  private readonly api = inject(Api);
  private readonly session = inject(Session);
  protected readonly page = signal<Page<AuditEvent> | null>(null);

  constructor() { this.go(0); }

  go(n: number): void {
    this.page.set(null);
    this.api.audit(this.session.ws(), n).subscribe((p) => this.page.set(p));
  }

  protected readable(code: string): string {
    const s = code.replace(/_/g, ' ').toLowerCase();
    return s.charAt(0).toUpperCase() + s.slice(1);
  }
  protected short(id: string): string { return id.slice(0, 8); }
}
