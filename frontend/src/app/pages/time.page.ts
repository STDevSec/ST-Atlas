import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api/api.service';
import { Assessment, TimeEntry } from '../core/api/models';
import { I18n } from '../core/i18n/i18n.service';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { Toasts } from '../core/toast.service';
import { EmptyState, PageHeader, Skeleton } from '../shared/ui';
import { IconComponent } from '../shared/icon.component';

function today(): string { return new Date().toISOString().slice(0, 10); }
function daysAgo(n: number): string { return new Date(Date.now() - n * 864e5).toISOString().slice(0, 10); }
function localIso(date: string, time: string): string { return new Date(`${date}T${time}`).toISOString(); }

@Component({
  selector: 'app-time',
  imports: [FormsModule, DatePipe, DecimalPipe, TPipe, PageHeader, EmptyState, Skeleton, IconComponent],
  template: `
  <app-page-header [title]="'nav.time' | t" />
  <div class="grid lg:grid-cols-3 gap-4">
    <div class="lg:col-span-2 space-y-4">
      @if (session.canWrite()) {
        <section class="card p-4">
          <h2 class="font-medium mb-3 flex items-center gap-2"><app-icon name="Clock" />{{ (running() ? 'time.running' : 'time.start') | t }}</h2>
          @if (running(); as r) {
            <div class="flex items-center justify-between gap-3 flex-wrap">
              <div><p class="font-medium">{{ r.assessmentCode }} · {{ r.assessmentTitle }}</p>
                <p class="faint text-sm">{{ 'time.started' | t }} {{ r.startedAt | date: 'short' }} · {{ elapsed() }}</p></div>
              <button class="btn btn-danger" (click)="stop()"><app-icon name="Square" />{{ 'time.stop' | t }}</button>
            </div>
          } @else {
            <div class="flex gap-2 flex-wrap">
              <select class="input flex-1 min-w-48" [(ngModel)]="timerAssessment" [attr.aria-label]="'nav.assessments' | t">
                <option value="">—</option>
                @for (a of assessments(); track a.id) { <option [value]="a.id">{{ a.code }} · {{ a.title }}</option> }
              </select>
              <input class="input flex-1 min-w-32" [(ngModel)]="timerDesc" [placeholder]="'common.description' | t" [attr.aria-label]="'common.description' | t" />
              <button class="btn btn-primary" [disabled]="!timerAssessment" (click)="start()"><app-icon name="Play" />{{ 'time.start' | t }}</button>
            </div>
          }
        </section>

        <section class="card p-4">
          <h2 class="font-medium mb-3">{{ 'time.manual' | t }}</h2>
          <form class="grid sm:grid-cols-2 gap-2" (submit)="addManual($event)" #f="ngForm">
            <select class="input sm:col-span-2" name="a" [(ngModel)]="m.assessmentId" required [attr.aria-label]="'nav.assessments' | t">
              <option value="">—</option>
              @for (a of assessments(); track a.id) { <option [value]="a.id">{{ a.code }} · {{ a.title }}</option> }
            </select>
            <label class="text-sm">{{ 'common.date' | t }}<input class="input" type="date" name="d" [(ngModel)]="m.date" required /></label>
            <label class="text-sm">{{ 'time.billable' | t }}
              <select class="input" name="b" [(ngModel)]="m.billable"><option [ngValue]="true">{{ 'common.yes' | t }}</option><option [ngValue]="false">{{ 'common.no' | t }}</option></select></label>
            <label class="text-sm">{{ 'time.started' | t }}<input class="input" type="time" name="s" [(ngModel)]="m.start" required /></label>
            <label class="text-sm">{{ 'time.ended' | t }}<input class="input" type="time" name="e" [(ngModel)]="m.end" required /></label>
            <input class="input sm:col-span-2" name="desc" [(ngModel)]="m.description" [placeholder]="'common.description' | t" [attr.aria-label]="'common.description' | t" />
            <button class="btn btn-primary sm:col-span-2 justify-center" [disabled]="busy() || !f.valid"><app-icon name="Plus" />{{ 'common.add' | t }}</button>
          </form>
        </section>
      }
    </div>

    <section class="card p-4 h-fit">
      <div class="flex items-center justify-between mb-3">
        <h2 class="font-medium">{{ 'time.activity' | t }}</h2>
        <a class="btn btn-sm" [href]="csvUrl()"><app-icon name="Download" />{{ 'time.export' | t }}</a>
      </div>
      <div class="flex gap-2 mb-3">
        <label class="text-xs flex-1">{{ 'common.from' | t }}<input class="input" type="date" [(ngModel)]="from" (ngModelChange)="load()" /></label>
        <label class="text-xs flex-1">{{ 'common.to' | t }}<input class="input" type="date" [(ngModel)]="to" (ngModelChange)="load()" /></label>
      </div>
      @if (entries(); as list) {
        @if (list.length) {
          <p class="text-sm mb-2">{{ 'time.total' | t }}: <strong>{{ totalHours() | number: '1.1-2' }} h</strong></p>
          <div class="space-y-1.5">
            @for (e of list; track e.id) {
              <div class="flex items-start justify-between gap-2 text-sm py-1.5 border-b hairline last:border-0">
                <div class="min-w-0"><p class="font-mono text-xs faint">{{ e.assessmentCode }}</p>
                  <p class="truncate">{{ e.description || e.assessmentTitle }}</p>
                  <p class="faint text-xs">{{ e.startedAt | date: 'short' }} · {{ e.durationSeconds / 3600 | number: '1.1-2' }} h @if (!e.billable) {· {{ 'time.billable' | t }}: {{ 'common.no' | t }}}</p></div>
                @if (session.canWrite() && !e.running) { <button class="btn btn-ghost btn-sm" (click)="remove(e)" [attr.aria-label]="'common.delete' | t"><app-icon name="Trash2" [size]="14" /></button> }
              </div>
            }
          </div>
        } @else { <app-empty [text]="'time.empty' | t" /> }
      } @else { <app-skeleton [rows]="4" /> }
    </section>
  </div>`,
})
export class TimePage {
  protected readonly session = inject(Session);
  private readonly api = inject(Api);
  private readonly toasts = inject(Toasts);
  private readonly i18n = inject(I18n);
  protected readonly assessments = signal<Assessment[]>([]);
  protected readonly entries = signal<TimeEntry[] | null>(null);
  protected readonly running = signal<TimeEntry | null>(null);
  protected readonly busy = signal(false);
  protected readonly now = signal(Date.now());
  protected from = daysAgo(30);
  protected to = today();
  protected timerAssessment = '';
  protected timerDesc = '';
  protected m = { assessmentId: '', date: today(), start: '09:00', end: '10:00', billable: true, description: '' };

  protected readonly totalHours = computed(() => (this.entries() ?? []).reduce((s, e) => s + e.durationSeconds, 0) / 3600);
  protected readonly elapsed = computed(() => {
    const r = this.running();
    if (!r) return '';
    const sec = Math.max(0, Math.floor((this.now() - new Date(r.startedAt).getTime()) / 1000));
    return `${Math.floor(sec / 3600)}h ${Math.floor((sec % 3600) / 60)}m`;
  });

  constructor() {
    const ws = this.session.ws();
    this.api.assessments(ws, { size: 200, sort: 'updatedAt,desc' }).subscribe((p) => this.assessments.set(p.items));
    this.api.timer(ws).subscribe((t) => this.running.set(t));
    this.load();
    setInterval(() => this.now.set(Date.now()), 30000);
  }

  load(): void {
    this.entries.set(null);
    this.api.timeEntries(this.session.ws(), { from: this.from, to: this.to }).subscribe((e) => this.entries.set(e));
  }

  csvUrl(): string { return this.api.timesheetUrl(this.session.ws(), this.from, this.to); }

  start(): void {
    this.api.startTimer(this.session.ws(), this.timerAssessment, this.timerDesc || undefined).subscribe({
      next: (t) => { this.running.set(t); this.timerDesc = ''; this.now.set(Date.now()); },
      error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }

  stop(): void {
    this.api.stopTimer(this.session.ws()).subscribe({
      next: () => { this.running.set(null); this.load(); }, error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }

  addManual(e: Event): void {
    e.preventDefault();
    this.busy.set(true);
    this.api.addTime(this.session.ws(), { assessmentId: this.m.assessmentId, startedAt: localIso(this.m.date, this.m.start),
      endedAt: localIso(this.m.date, this.m.end), billable: this.m.billable, description: this.m.description || null }).subscribe({
      next: () => { this.busy.set(false); this.toasts.ok(this.i18n.t('common.saved')); this.m.description = ''; this.load(); },
      error: (err) => { this.busy.set(false); this.toasts.error(err, this.i18n.t('common.error')); },
    });
  }

  remove(e: TimeEntry): void {
    if (!confirm(this.i18n.t('common.confirmDelete'))) return;
    this.api.deleteTime(this.session.ws(), e.id).subscribe({
      next: () => this.load(), error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }
}
