import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { Assessment, AssessmentType, Page, STATUSES } from '../core/api/models';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { IconComponent } from '../shared/icon.component';
import { EmptyState, PageHeader, Skeleton, StatusChip } from '../shared/ui';

@Component({
  selector: 'app-assessments',
  imports: [RouterLink, FormsModule, DatePipe, TPipe, IconComponent, PageHeader, StatusChip, EmptyState, Skeleton],
  template: `
  <app-page-header [title]="'nav.assessments' | t">
    <div class="flex rounded-md border hairline overflow-hidden" role="group">
      <button class="btn btn-ghost btn-sm rounded-none" [class.!bg-[var(--surface-muted)]]="view() === 'list'" (click)="view.set('list')"
              [attr.aria-label]="'assessment.list' | t"><app-icon name="Rows3" /></button>
      <button class="btn btn-ghost btn-sm rounded-none" [class.!bg-[var(--surface-muted)]]="view() === 'cards'" (click)="view.set('cards')"
              [attr.aria-label]="'assessment.cards' | t"><app-icon name="LayoutGrid" /></button>
    </div>
    @if (session.canWrite()) { <a class="btn btn-primary" routerLink="/assessments/new"><app-icon name="Plus" />{{ 'assessment.new' | t }}</a> }
  </app-page-header>

  <div class="flex flex-wrap gap-2 mb-4">
    <input class="input max-w-xs" type="search" [(ngModel)]="q" (ngModelChange)="reload()" [placeholder]="'common.search' | t" [attr.aria-label]="'common.search' | t" />
    <select class="input w-auto" [(ngModel)]="status" (ngModelChange)="reload()" [attr.aria-label]="'common.status' | t">
      <option value="">{{ 'common.status' | t }}: {{ 'common.all' | t }}</option>
      @for (s of statuses; track s) { <option [value]="s">{{ 'status.' + s | t }}</option> }
    </select>
    <select class="input w-auto" [(ngModel)]="type" (ngModelChange)="reload()" [attr.aria-label]="'common.type' | t">
      <option value="">{{ 'common.type' | t }}: {{ 'common.all' | t }}</option>
      @for (t of types(); track t.code) { <option [value]="t.code">{{ 'type.' + t.code | t }}</option> }
    </select>
    <select class="input w-auto" [(ngModel)]="sort" (ngModelChange)="reload()" aria-label="Sort">
      <option value="updatedAt">{{ 'common.updated' | t }}</option><option value="dueDate">{{ 'assessment.dueDate' | t }}</option>
      <option value="title">{{ 'assessment.title' | t }}</option><option value="code">ID</option>
    </select>
  </div>

  @if (page(); as p) {
    @if (p.items.length === 0) {
      <div class="card"><app-empty [text]="'assessment.empty' | t">
        @if (session.canWrite()) { <a class="btn btn-primary" routerLink="/assessments/new">{{ 'assessment.new' | t }}</a> }
      </app-empty></div>
    } @else if (view() === 'list') {
      <div class="card overflow-hidden"><table class="table">
        <thead><tr><th>ID</th><th>{{ 'assessment.title' | t }}</th><th>{{ 'common.type' | t }}</th><th>{{ 'common.client' | t }}</th>
          <th>{{ 'common.status' | t }}</th><th>{{ 'assessment.dueDate' | t }}</th></tr></thead>
        <tbody>
          @for (a of p.items; track a.id) {
            <tr class="cursor-pointer" [routerLink]="['/assessments', a.id]">
              <td class="font-mono text-xs faint">{{ a.code }}</td>
              <td><a [routerLink]="['/assessments', a.id]" class="font-medium">{{ a.title }}</a>
                <div class="flex gap-1 mt-1">@for (tag of a.tags; track tag) { <span class="chip">#{{ tag }}</span> }</div></td>
              <td class="muted">{{ 'type.' + a.typeCode | t }}</td><td class="muted">{{ a.clientName ?? '—' }}</td>
              <td><app-status [value]="a.status" /></td><td class="tabular-nums muted">{{ a.dueDate ?? '—' }}</td>
            </tr>
          }
        </tbody></table></div>
    } @else {
      <div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
        @for (a of p.items; track a.id) {
          <a class="card p-4 hover:border-[var(--border-strong)]" [routerLink]="['/assessments', a.id]">
            <div class="flex justify-between items-start gap-2"><span class="font-mono text-xs faint">{{ a.code }}</span><app-status [value]="a.status" /></div>
            <h3 class="font-medium mt-2">{{ a.title }}</h3>
            <p class="muted text-sm">{{ 'type.' + a.typeCode | t }} · {{ a.clientName ?? ('assessment.noClient' | t) }}</p>
            <p class="faint text-xs mt-3">{{ 'common.updated' | t }} {{ a.updatedAt | date: 'mediumDate' }}</p>
          </a>
        }
      </div>
    }
    @if (p.total > p.size) {
      <div class="flex justify-end items-center gap-2 mt-4 text-sm">
        <button class="btn btn-sm" [disabled]="p.page === 0" (click)="go(p.page - 1)">{{ 'common.previous' | t }}</button>
        <span class="muted">{{ p.page + 1 }} / {{ pages(p) }}</span>
        <button class="btn btn-sm" [disabled]="p.page + 1 >= pages(p)" (click)="go(p.page + 1)">{{ 'common.next' | t }}</button>
      </div>
    }
  } @else { <div class="card"><app-skeleton [rows]="6" /></div> }`,
})
export class AssessmentsPage {
  protected readonly session = inject(Session);
  private readonly api = inject(Api);
  protected readonly page = signal<Page<Assessment> | null>(null);
  protected readonly types = signal<AssessmentType[]>([]);
  protected readonly view = signal<'list' | 'cards'>('list');
  protected readonly statuses = STATUSES;
  protected q = '';
  protected status = '';
  protected type = '';
  protected sort = 'updatedAt';
  private current = 0;
  private debounce?: ReturnType<typeof setTimeout>;

  constructor() {
    this.api.assessmentTypes().subscribe((t) => this.types.set(t));
    this.go(0);
  }

  reload(): void {
    clearTimeout(this.debounce);
    this.debounce = setTimeout(() => this.go(0), 250);
  }

  go(page: number): void {
    this.current = page;
    this.api.assessments(this.session.ws(), { q: this.q, status: this.status, type: this.type, page, sort: this.sort,
      dir: this.sort === 'title' || this.sort === 'code' || this.sort === 'dueDate' ? 'asc' : 'desc' })
      .subscribe((p) => this.page.set(p));
  }

  pages(p: Page<unknown>): number { return Math.max(1, Math.ceil(p.total / p.size)); }
}
