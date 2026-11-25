import { Component, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { Assessment, AssessmentStatus, Finding, ReportSummary, TimeEntry } from '../core/api/models';
import { I18n } from '../core/i18n/i18n.service';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { Toasts } from '../core/toast.service';
import { IconComponent } from '../shared/icon.component';
import { EmptyState, PageHeader, SeverityBadge, Skeleton, StatusChip } from '../shared/ui';

type Tab = 'overview' | 'findings' | 'reports' | 'time';

@Component({
  selector: 'app-assessment-detail',
  imports: [RouterLink, DatePipe, DecimalPipe, TPipe, IconComponent, PageHeader, StatusChip, SeverityBadge, EmptyState, Skeleton],
  template: `
  @if (a(); as a) {
    <app-page-header [title]="a.title" [subtitle]="a.code + ' · ' + ('type.' + a.typeCode | t) + ' · ' + (a.clientName ?? ('assessment.noClient' | t))">
      <a crumbs routerLink="/assessments" class="faint text-sm inline-flex items-center gap-1 mb-1"><app-icon name="ArrowLeft" [size]="14" />{{ 'nav.assessments' | t }}</a>
      <app-status [value]="a.status" />
      @if (session.canWrite()) {
        @for (s of a.nextStatuses; track s) {
          <button class="btn btn-sm" (click)="move(s)" [disabled]="s === 'APPROVED' && !session.canReview()">→ {{ 'status.' + s | t }}</button>
        }
        <a class="btn btn-sm" [routerLink]="['/assessments', a.id, 'edit']"><app-icon name="Pencil" />{{ 'common.edit' | t }}</a>
      }
    </app-page-header>

    <div class="flex gap-1 border-b hairline mb-4" role="tablist">
      @for (t of tabs; track t) {
        <button role="tab" [attr.aria-selected]="tab() === t" (click)="tab.set(t)"
                class="px-3 py-2 -mb-px border-b-2 text-sm" [class.border-[var(--color-accent-600)]]="tab() === t"
                [class.font-medium]="tab() === t" [class.border-transparent]="tab() !== t" [class.muted]="tab() !== t">
          {{ 'assessment.' + t | t }}
          @if (t === 'findings') { <span class="faint">({{ findings().length }})</span> }
        </button>
      }
    </div>

    @switch (tab()) {
      @case ('overview') {
        <div class="grid lg:grid-cols-3 gap-4">
          <div class="card p-5 lg:col-span-2 space-y-4">
            @for (f of fields; track f.key) {
              @if (field(a, f.key)) { <section><h3 class="label">{{ f.label | t }}</h3><p class="whitespace-pre-wrap">{{ field(a, f.key) }}</p></section> }
            }
          </div>
          <div class="card p-5 space-y-3 text-sm h-fit">
            <div class="flex justify-between"><span class="muted">{{ 'assessment.startDate' | t }}</span><span>{{ a.startDate ?? '—' }}</span></div>
            <div class="flex justify-between"><span class="muted">{{ 'assessment.dueDate' | t }}</span><span>{{ a.dueDate ?? '—' }}</span></div>
            <div class="flex justify-between"><span class="muted">{{ 'assessment.completedDate' | t }}</span><span>{{ a.completedDate ?? '—' }}</span></div>
            <div><span class="muted">{{ 'common.tags' | t }}</span>
              <div class="flex flex-wrap gap-1 mt-1">@for (t of a.tags; track t) { <a class="chip" [routerLink]="['/search']" [queryParams]="{ tags: t }">#{{ t }}</a> }</div></div>
          </div>
        </div>
      }
      @case ('findings') {
        <div class="flex justify-end mb-3">
          @if (session.canWrite()) { <a class="btn btn-primary" [routerLink]="['/assessments', a.id, 'findings', 'new']"><app-icon name="Plus" />{{ 'finding.new' | t }}</a> }
        </div>
        @if (findings().length) {
          <div class="card overflow-hidden"><table class="table">
            <thead><tr><th>ID</th><th>{{ 'common.title' | t }}</th><th>{{ 'finding.severity' | t }}</th>@if (a.usesCvss) {<th>CVSS</th>}<th>{{ 'finding.remediation' | t }}</th></tr></thead>
            <tbody>@for (f of findings(); track f.id) {
              <tr><td class="font-mono text-xs faint">{{ f.code }}</td><td><a [routerLink]="['/findings', f.id]" class="font-medium">{{ f.title }}</a></td>
                <td><app-severity [value]="f.severity" /></td>@if (a.usesCvss) {<td class="tabular-nums">{{ f.cvss31Score ?? f.cvss40Score ?? '—' }}</td>}
                <td><app-status [value]="f.status" prefix="rem." /></td></tr>
            }</tbody></table></div>
        } @else { <div class="card"><app-empty [text]="'finding.empty' | t" /></div> }
      }
      @case ('reports') {
        <div class="flex justify-end mb-3">
          @if (session.canWrite()) { <button class="btn btn-primary" (click)="newReport()"><app-icon name="Plus" />{{ 'report.new' | t }}</button> }
        </div>
        @if (reports().length) {
          <div class="card overflow-hidden"><table class="table">
            <thead><tr><th>{{ 'common.title' | t }}</th><th>{{ 'report.classification' | t }}</th><th>{{ 'report.versions' | t }}</th><th>{{ 'common.updated' | t }}</th></tr></thead>
            <tbody>@for (r of reports(); track r.id) {
              <tr><td><a [routerLink]="['/reports', r.id]" class="font-medium">{{ r.title }}</a></td><td class="muted">{{ 'class.' + r.classification | t }}</td>
                <td>@if (r.latestVersion) { <a class="chip" [routerLink]="['/reports', r.id, 'versions', r.latestVersion]"><app-icon name="FileDown" [size]="12" />v{{ r.latestVersion }}</a> } @else { — }</td>
                <td class="faint">{{ r.updatedAt | date: 'short' }}</td></tr>
            }</tbody></table></div>
        } @else { <div class="card"><app-empty [text]="'report.empty' | t" /></div> }
      }
      @case ('time') {
        <div class="flex justify-between items-center mb-3">
          <p class="muted">{{ 'time.total' | t }}: <strong>{{ totalHours() | number: '1.0-2' }} {{ 'common.hours' | t }}</strong></p>
          @if (session.canWrite()) { <button class="btn btn-primary" (click)="startTimer()"><app-icon name="Play" />{{ 'time.start' | t }}</button> }
        </div>
        @if (time().length) {
          <div class="card overflow-hidden"><table class="table">
            <thead><tr><th>{{ 'time.started' | t }}</th><th>{{ 'time.duration' | t }}</th><th>{{ 'time.activity' | t }}</th></tr></thead>
            <tbody>@for (e of time(); track e.id) {
              <tr><td>{{ e.startedAt | date: 'short' }}</td><td class="tabular-nums">{{ e.durationSeconds / 3600 | number: '1.0-2' }} h @if (e.running) {<span class="chip">●</span>}</td><td class="muted">{{ e.description ?? '' }}</td></tr>
            }</tbody></table></div>
        } @else { <div class="card"><app-empty [text]="'time.empty' | t" /></div> }
      }
    }
  } @else { <div class="card"><app-skeleton [rows]="6" /></div> }`,
})
export class AssessmentDetailPage {
  readonly id = input.required<string>();
  protected readonly session = inject(Session);
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);
  private readonly i18n = inject(I18n);
  protected readonly a = signal<Assessment | null>(null);
  protected readonly findings = signal<Finding[]>([]);
  protected readonly reports = signal<ReportSummary[]>([]);
  protected readonly time = signal<TimeEntry[]>([]);
  protected readonly tabs: Tab[] = ['overview', 'findings', 'reports', 'time'];
  // Open the tab named in ?tab= (e.g. coming back from saving a finding), else overview.
  protected readonly tab = signal<Tab>(
    (this.tabs as string[]).includes(inject(ActivatedRoute).snapshot.queryParamMap.get('tab') ?? '')
      ? (inject(ActivatedRoute).snapshot.queryParamMap.get('tab') as Tab)
      : 'overview',
  );
  protected readonly fields = [
    { key: 'description', label: 'common.description' }, { key: 'objectives', label: 'assessment.objectives' },
    { key: 'scope', label: 'assessment.scope' }, { key: 'outOfScope', label: 'assessment.outOfScope' },
    { key: 'rulesOfEngagement', label: 'assessment.roe' }, { key: 'methodology', label: 'assessment.methodology' },
  ];

  constructor() { queueMicrotask(() => this.load()); }

  load(): void {
    const ws = this.session.ws();
    this.api.assessment(ws, this.id()).subscribe((a) => this.a.set(a));
    this.api.findings(ws, this.id()).subscribe((f) => this.findings.set(f));
    this.api.assessmentReports(ws, this.id()).subscribe((r) => this.reports.set(r));
    this.api.timeEntries(ws, { assessmentId: this.id(), from: '2000-01-01' }).subscribe((t) => this.time.set(t));
  }

  field(a: Assessment, key: string): string | null {
    return (a as unknown as Record<string, string | null>)[key];
  }

  totalHours(): number {
    return this.time().reduce((s, e) => s + e.durationSeconds, 0) / 3600;
  }

  move(status: AssessmentStatus): void {
    this.api.transition(this.session.ws(), this.id(), status).subscribe({
      next: (a) => this.a.set(a), error: (e) => this.toasts.error(e, this.i18n.t('common.error')),
    });
  }

  newReport(): void {
    this.api.createReport(this.session.ws(), this.id()).subscribe({
      next: (r) => void this.router.navigate(['/reports', r.id]), error: (e) => this.toasts.error(e, this.i18n.t('common.error')),
    });
  }

  startTimer(): void {
    this.api.startTimer(this.session.ws(), this.id()).subscribe({
      next: () => { this.toasts.ok(this.i18n.t('time.running')); this.load(); },
      error: (e) => this.toasts.error(e, this.i18n.t('common.error')),
    });
  }
}
