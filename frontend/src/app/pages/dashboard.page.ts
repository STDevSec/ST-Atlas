import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { Dashboard, SEVERITIES } from '../core/api/models';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { IconComponent } from '../shared/icon.component';
import { PageHeader, Skeleton, StatusChip } from '../shared/ui';

const SEV_COLOR: Record<string, string> = { CRITICAL: 'var(--color-sev-critical)', HIGH: 'var(--color-sev-high)',
  MEDIUM: 'var(--color-sev-medium)', LOW: 'var(--color-sev-low)', INFORMATIONAL: 'var(--color-sev-info)', NOT_APPLICABLE: '#9ca3af' };

function isoDaysAgo(days: number): string {
  const d = new Date(Date.now() - days * 86400000);
  return d.toISOString().slice(0, 10);
}

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, FormsModule, DatePipe, DecimalPipe, TPipe, IconComponent, PageHeader, Skeleton, StatusChip],
  template: `
  <app-page-header [title]="'nav.dashboard' | t" [subtitle]="session.workspace()?.name ?? null">
    <label class="sr-only" for="range">{{ 'dashboard.range' | t }}</label>
    <select id="range" class="input w-auto" [(ngModel)]="range" (ngModelChange)="load()">
      <option [ngValue]="30">30 d</option><option [ngValue]="90">90 d</option><option [ngValue]="365">365 d</option>
    </select>
    @if (session.canWrite()) {
      <a class="btn" routerLink="/clients/new"><app-icon name="Plus" />{{ 'dashboard.newClient' | t }}</a>
      <a class="btn btn-primary" routerLink="/assessments/new"><app-icon name="Plus" />{{ 'dashboard.newAssessment' | t }}</a>
    }
  </app-page-header>

  @if (data(); as d) {
    <div class="grid grid-cols-2 lg:grid-cols-5 gap-3 mb-5">
      @for (k of kpis(); track k.label) {
        <div class="card p-4"><p class="muted text-xs">{{ k.label | t }}</p><p class="text-2xl font-semibold mt-1">{{ k.value }}</p></div>
      }
    </div>
    <div class="grid lg:grid-cols-3 gap-4">
      <section class="card p-4">
        <h2 class="font-medium mb-3">{{ 'dashboard.bySeverity' | t }}</h2>
        @for (s of severities; track s) {
          <div class="flex items-center gap-2 text-sm mb-1.5">
            <span class="w-24 muted">{{ 'sev.' + s | t }}</span>
            <div class="flex-1 h-2 rounded" style="background:var(--surface-muted)">
              <div class="h-2 rounded" [style.width.%]="pct(d.openFindingsBySeverity[s] ?? 0, d.openFindings)" [style.background]="sevColor[s]"></div>
            </div>
            <span class="w-8 text-right tabular-nums">{{ d.openFindingsBySeverity[s] ?? 0 }}</span>
          </div>
        }
      </section>
      <section class="card p-4">
        <h2 class="font-medium mb-3">{{ 'dashboard.byStatus' | t }}</h2>
        @for (e of entries(d.findingsByStatus); track e[0]) {
          <div class="flex justify-between text-sm py-1 border-b hairline last:border-0"><span class="muted">{{ 'rem.' + e[0] | t }}</span><span class="tabular-nums">{{ e[1] }}</span></div>
        } @empty { <p class="muted text-sm">{{ 'dashboard.empty' | t }}</p> }
      </section>
      <section class="card p-4">
        <h2 class="font-medium mb-3">{{ 'dashboard.byType' | t }}</h2>
        @for (e of entries(d.assessmentsByType); track e[0]) {
          <div class="flex justify-between text-sm py-1 border-b hairline last:border-0"><span class="muted">{{ 'type.' + e[0] | t }}</span><span class="tabular-nums">{{ e[1] }}</span></div>
        } @empty { <p class="muted text-sm">{{ 'dashboard.empty' | t }}</p> }
      </section>
      <section class="card p-4 lg:col-span-2">
        <h2 class="font-medium mb-3">{{ 'dashboard.deadlines' | t }}</h2>
        @for (x of d.upcomingDeadlines; track x.id) {
          <a [routerLink]="['/assessments', x.id]" class="flex items-center gap-3 py-1.5 text-sm border-b hairline last:border-0 hover:opacity-80">
            <span class="font-mono faint w-28">{{ x.code }}</span><span class="flex-1 truncate">{{ x.title }}</span>
            <app-status [value]="x.status" /><span class="tabular-nums w-24 text-right">{{ x.dueDate }}</span>
          </a>
        } @empty { <p class="muted text-sm">{{ 'dashboard.empty' | t }}</p> }
      </section>
      <section class="card p-4">
        <h2 class="font-medium mb-3">{{ 'dashboard.hoursByAssessment' | t }}</h2>
        @for (h of d.hoursByAssessment; track h.assessmentId) {
          <a [routerLink]="['/assessments', h.assessmentId]" class="flex justify-between text-sm py-1 border-b hairline last:border-0">
            <span class="truncate">{{ h.code }} · {{ h.title }}</span><span class="tabular-nums">{{ h.hours | number: '1.0-2' }} {{ 'common.hours' | t }}</span>
          </a>
        } @empty { <p class="muted text-sm">{{ 'dashboard.empty' | t }}</p> }
      </section>
      <section class="card p-4 lg:col-span-2">
        <h2 class="font-medium mb-3">{{ 'dashboard.activity' | t }}</h2>
        @for (a of d.recentActivity; track $index) {
          <div class="flex justify-between text-sm py-1 border-b hairline last:border-0">
            <span>{{ readable(a.action) }} <span class="faint">· {{ a.entityType }}</span></span>
            <span class="faint">{{ a.at | date: 'short' }}</span>
          </div>
        } @empty { <p class="muted text-sm">{{ 'dashboard.empty' | t }}</p> }
      </section>
      <section class="card p-4">
        <h2 class="font-medium mb-3">{{ 'dashboard.reports' | t }}</h2>
        @for (r of d.recentReports; track r.id) {
          <a [routerLink]="['/reports', r.id]" class="flex justify-between gap-2 text-sm py-1 border-b hairline last:border-0">
            <span class="truncate">{{ r.title }}</span><span class="faint shrink-0">{{ r.updatedAt | date: 'shortDate' }}</span>
          </a>
        } @empty { <p class="muted text-sm">{{ 'dashboard.empty' | t }}</p> }
      </section>
    </div>
  } @else { <div class="card"><app-skeleton [rows]="8" /></div> }`,
})
export class DashboardPage {
  protected readonly session = inject(Session);
  private readonly api = inject(Api);
  protected readonly data = signal<Dashboard | null>(null);
  protected readonly severities = SEVERITIES.filter((s) => s !== 'NOT_APPLICABLE');
  protected readonly sevColor = SEV_COLOR;
  protected range = 90;

  /** Turns an audit action code (e.g. ASSESSMENT_STATUS_CHANGED) into readable text. */
  protected readable(code: string): string {
    const s = code.replace(/_/g, ' ').toLowerCase();
    return s.charAt(0).toUpperCase() + s.slice(1);
  }

  protected readonly kpis = computed(() => {
    const d = this.data();
    if (!d) return [];
    return [
      { label: 'dashboard.active', value: d.activeAssessments }, { label: 'dashboard.review', value: d.underReview },
      { label: 'dashboard.completed', value: d.completedAssessments }, { label: 'dashboard.openFindings', value: d.openFindings },
      { label: 'dashboard.hours', value: d.totalHours },
    ];
  });

  constructor() { this.load(); }

  load(): void {
    this.api.dashboard(this.session.ws(), isoDaysAgo(this.range)).subscribe((d) => this.data.set(d));
  }

  entries(m: Record<string, number>): [string, number][] {
    return Object.entries(m).sort((a, b) => b[1] - a[1]);
  }

  pct(v: number, total: number): number {
    return total ? Math.round((v / total) * 100) : 0;
  }
}
