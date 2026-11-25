import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { Finding, Page, REMEDIATIONS, SEVERITIES } from '../core/api/models';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { EmptyState, PageHeader, SeverityBadge, Skeleton, StatusChip } from '../shared/ui';

type Item = { finding: Finding; assessmentCode: string; assessmentTitle: string };

@Component({
  selector: 'app-findings',
  imports: [RouterLink, FormsModule, TPipe, PageHeader, SeverityBadge, StatusChip, EmptyState, Skeleton],
  template: `
  <app-page-header [title]="'nav.findings' | t" />
  <div class="flex flex-wrap gap-2 mb-4">
    <input class="input max-w-xs" type="search" [(ngModel)]="q" (ngModelChange)="reload()" [placeholder]="'common.search' | t" [attr.aria-label]="'common.search' | t" />
    <select class="input w-auto" [(ngModel)]="severity" (ngModelChange)="reload()" [attr.aria-label]="'finding.severity' | t">
      <option value="">{{ 'finding.severity' | t }}: {{ 'common.all' | t }}</option>
      @for (s of severities; track s) { <option [value]="s">{{ 'sev.' + s | t }}</option> }</select>
    <select class="input w-auto" [(ngModel)]="status" (ngModelChange)="reload()" [attr.aria-label]="'finding.remediation' | t">
      <option value="">{{ 'finding.remediation' | t }}: {{ 'common.all' | t }}</option>
      @for (s of remediations; track s) { <option [value]="s">{{ 'rem.' + s | t }}</option> }</select>
  </div>
  @if (page(); as p) {
    @if (p.items.length) {
      <div class="card overflow-hidden"><table class="table">
        <thead><tr><th>ID</th><th>{{ 'common.title' | t }}</th><th>{{ 'nav.assessments' | t }}</th><th>{{ 'finding.severity' | t }}</th><th>CVSS</th><th>{{ 'finding.remediation' | t }}</th></tr></thead>
        <tbody>@for (i of p.items; track i.finding.id) {
          <tr><td class="font-mono text-xs faint">{{ i.finding.code }}</td>
            <td><a [routerLink]="['/findings', i.finding.id]" class="font-medium">{{ i.finding.title }}</a></td>
            <td><a [routerLink]="['/assessments', i.finding.assessmentId]" class="muted">{{ i.assessmentCode }} · {{ i.assessmentTitle }}</a></td>
            <td><app-severity [value]="i.finding.severity" /></td><td class="tabular-nums">{{ i.finding.cvss31Score ?? i.finding.cvss40Score ?? '—' }}</td>
            <td><app-status [value]="i.finding.status" prefix="rem." /></td></tr>
        }</tbody></table></div>
      @if (p.total > p.size) {
        <div class="flex justify-end gap-2 mt-3"><button class="btn btn-sm" [disabled]="p.page === 0" (click)="go(p.page - 1)">{{ 'common.previous' | t }}</button>
          <button class="btn btn-sm" [disabled]="(p.page + 1) * p.size >= p.total" (click)="go(p.page + 1)">{{ 'common.next' | t }}</button></div>
      }
    } @else { <div class="card"><app-empty [text]="'finding.empty' | t" /></div> }
  } @else { <div class="card"><app-skeleton /></div> }`,
})
export class FindingsPage {
  private readonly api = inject(Api);
  private readonly session = inject(Session);
  protected readonly page = signal<Page<Item> | null>(null);
  protected readonly severities = SEVERITIES;
  protected readonly remediations = REMEDIATIONS;
  protected q = '';
  protected severity = '';
  protected status = '';
  private debounce?: ReturnType<typeof setTimeout>;

  constructor() { this.go(0); }

  reload(): void { clearTimeout(this.debounce); this.debounce = setTimeout(() => this.go(0), 250); }

  go(page: number): void {
    this.api.allFindings(this.session.ws(), { q: this.q, severity: this.severity, status: this.status, page }).subscribe((p) => this.page.set(p));
  }
}
