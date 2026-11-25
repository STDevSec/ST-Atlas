import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { Page, ReportSummary } from '../core/api/models';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { IconComponent } from '../shared/icon.component';
import { EmptyState, PageHeader, Skeleton } from '../shared/ui';

@Component({
  selector: 'app-reports',
  imports: [RouterLink, DatePipe, TPipe, IconComponent, PageHeader, EmptyState, Skeleton],
  template: `
  <app-page-header [title]="'nav.reports' | t" />
  @if (page(); as p) {
    @if (p.items.length) {
      <div class="card overflow-hidden"><table class="table">
        <thead><tr><th>{{ 'common.title' | t }}</th><th>{{ 'report.classification' | t }}</th><th>PDF</th><th>{{ 'common.updated' | t }}</th></tr></thead>
        <tbody>@for (r of p.items; track r.id) {
          <tr><td><a [routerLink]="['/reports', r.id]" class="font-medium">{{ r.title }}</a></td><td class="muted">{{ 'class.' + r.classification | t }}</td>
            <td>@if (r.latestVersion) { <a class="chip" [routerLink]="['/reports', r.id, 'versions', r.latestVersion]"><app-icon name="FileDown" [size]="12" />v{{ r.latestVersion }}</a> } @else { — }</td>
            <td class="faint">{{ r.updatedAt | date: 'short' }}</td></tr>
        }</tbody></table></div>
    } @else { <div class="card"><app-empty [text]="'report.empty' | t" /></div> }
  } @else { <div class="card"><app-skeleton /></div> }`,
})
export class ReportsPage {
  private readonly api = inject(Api);
  private readonly session = inject(Session);
  protected readonly page = signal<Page<ReportSummary> | null>(null);
  constructor() { this.api.reports(this.session.ws()).subscribe((p) => this.page.set(p)); }
}
