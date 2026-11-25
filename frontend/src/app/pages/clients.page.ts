import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { Client, Page } from '../core/api/models';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { IconComponent } from '../shared/icon.component';
import { EmptyState, PageHeader, Skeleton } from '../shared/ui';

@Component({
  selector: 'app-clients',
  imports: [RouterLink, FormsModule, DatePipe, TPipe, IconComponent, PageHeader, EmptyState, Skeleton],
  template: `
  <app-page-header [title]="'nav.clients' | t">
    @if (session.canWrite()) { <a class="btn btn-primary" routerLink="/clients/new"><app-icon name="Plus" />{{ 'client.new' | t }}</a> }
  </app-page-header>
  <input class="input max-w-xs mb-4" type="search" [(ngModel)]="q" (ngModelChange)="reload()" [placeholder]="'common.search' | t" [attr.aria-label]="'common.search' | t" />
  @if (page(); as p) {
    @if (p.items.length) {
      <div class="card overflow-hidden"><table class="table">
        <thead><tr><th></th><th>{{ 'common.name' | t }}</th><th>{{ 'client.legalName' | t }}</th><th>{{ 'client.contacts' | t }}</th><th>{{ 'common.updated' | t }}</th></tr></thead>
        <tbody>@for (c of p.items; track c.id) {
          <tr><td class="w-12">@if (c.logoAttachmentId) { <img [src]="api.attachmentUrl(session.ws(), c.logoAttachmentId)" alt="" class="h-7 w-7 object-contain" /> }</td>
            <td><a [routerLink]="['/clients', c.id]" class="font-medium">{{ c.name }}</a></td><td class="muted">{{ c.legalName ?? '—' }}</td>
            <td class="muted">{{ c.contacts.length }}</td><td class="faint">{{ c.updatedAt | date: 'mediumDate' }}</td></tr>
        }</tbody></table></div>
    } @else { <div class="card"><app-empty [text]="'client.empty' | t" /></div> }
  } @else { <div class="card"><app-skeleton /></div> }`,
})
export class ClientsPage {
  protected readonly api = inject(Api);
  protected readonly session = inject(Session);
  protected readonly page = signal<Page<Client> | null>(null);
  protected q = '';
  private debounce?: ReturnType<typeof setTimeout>;
  constructor() { this.load(); }
  reload(): void { clearTimeout(this.debounce); this.debounce = setTimeout(() => this.load(), 250); }
  load(): void { this.api.clients(this.session.ws(), this.q, 0, 100).subscribe((p) => this.page.set(p)); }
}
