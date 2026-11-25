import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { Page, REMEDIATIONS, SEVERITIES, SearchHit, TagCount } from '../core/api/models';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { EmptyState, PageHeader, SeverityBadge, Skeleton, StatusChip } from '../shared/ui';
import { IconComponent } from '../shared/icon.component';

const MARK = '⁣'; // ts_headline delimiter from the backend highlighter
const ENTITIES = ['assessment', 'finding', 'report', 'client'] as const;
const ROUTE: Record<string, string> = { assessment: '/assessments', finding: '/findings', report: '/reports', client: '/clients' };

@Component({
  selector: 'app-search',
  imports: [FormsModule, RouterLink, TPipe, PageHeader, SeverityBadge, StatusChip, EmptyState, Skeleton, IconComponent],
  template: `
  <app-page-header [title]="'nav.search' | t" />
  <div class="card p-4 mb-4 space-y-3">
    <div class="flex flex-wrap gap-2">
      <input class="input flex-1 min-w-64" type="search" [(ngModel)]="q" (ngModelChange)="reload()" [placeholder]="'search.placeholder' | t" [attr.aria-label]="'nav.search' | t" />
      <select class="input w-auto" [(ngModel)]="entityType" (ngModelChange)="run()" [attr.aria-label]="'search.entity' | t">
        <option value="">{{ 'search.entity' | t }}: {{ 'common.all' | t }}</option>
        @for (e of entities; track e) { <option [value]="e">{{ navKey(e) | t }}</option> }
      </select>
      <select class="input w-auto" [(ngModel)]="severity" (ngModelChange)="run()" [attr.aria-label]="'finding.severity' | t">
        <option value="">{{ 'finding.severity' | t }}: {{ 'common.all' | t }}</option>
        @for (s of severities; track s) { <option [value]="s">{{ 'sev.' + s | t }}</option> }
      </select>
      <select class="input w-auto" [(ngModel)]="status" (ngModelChange)="run()" [attr.aria-label]="'finding.remediation' | t">
        <option value="">{{ 'finding.remediation' | t }}: {{ 'common.all' | t }}</option>
        @for (s of remediations; track s) { <option [value]="s">{{ 'rem.' + s | t }}</option> }
      </select>
    </div>
    @if (tags().length) {
      <div class="flex flex-wrap items-center gap-1.5">
        <app-icon name="Tag" [size]="14" class="faint" />
        @for (t of tags(); track t.tag) {
          <button class="chip" [class.!border-[var(--color-accent-500)]]="selectedTags.has(t.tag)"
                  [class.!text-[var(--text)]]="selectedTags.has(t.tag)" (click)="toggleTag(t.tag)">#{{ t.tag }} <span class="faint">{{ t.count }}</span></button>
        }
        @if (selectedTags.size > 1) {
          <button class="chip" (click)="tagMode = tagMode === 'AND' ? 'OR' : 'AND'; run()">{{ 'search.tagMode' | t }}: {{ tagMode }}</button>
        }
      </div>
    }
  </div>
  @if (results(); as p) {
    @if (p.items.length) {
      <p class="faint text-sm mb-2">{{ p.total }} {{ 'search.results' | t }}</p>
      <div class="space-y-2">
        @for (h of p.items; track h.entityType + h.entityId) {
          <a [routerLink]="[route(h)]" class="card p-3 block hover:border-[var(--border-strong)]">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="chip">{{ navKey(h.entityType) | t }}</span>
              <span class="font-medium">{{ h.title }}</span>
              @if (h.severity) { <app-severity [value]="h.severity" /> }
              @if (h.status) { <app-status [value]="h.status" prefix="rem." /> }
              <span class="flex-1"></span>
              @for (tag of h.tags; track tag) { <span class="faint text-xs">#{{ tag }}</span> }
            </div>
            @if (h.snippet) { <p class="muted text-sm mt-1">@for (seg of segments(h.snippet); track $index) {<span [class.bg-[var(--mark)]]="seg.hit" [class.font-medium]="seg.hit">{{ seg.text }}</span>}</p> }
          </a>
        }
      </div>
    } @else { <div class="card"><app-empty [text]="'common.noResults' | t" /></div> }
  } @else if (searched()) { <div class="card"><app-skeleton [rows]="5" /></div> }
  @else { <div class="card"><app-empty [text]="'search.hint' | t" /></div> }`,
  styles: [':host{--mark:rgba(250,204,21,.35)}'],
})
export class SearchPage {
  private readonly api = inject(Api);
  private readonly session = inject(Session);
  protected readonly results = signal<Page<SearchHit> | null>(null);
  protected readonly tags = signal<TagCount[]>([]);
  protected readonly searched = signal(false);
  protected readonly entities = ENTITIES;
  protected readonly severities = SEVERITIES;
  protected readonly remediations = REMEDIATIONS;
  protected q = '';
  protected entityType = '';
  protected severity = '';
  protected status = '';
  protected tagMode: 'AND' | 'OR' = 'AND';
  protected selectedTags = new Set<string>();
  private debounce?: ReturnType<typeof setTimeout>;

  constructor() {
    const qp = inject(ActivatedRoute).snapshot.queryParamMap;
    this.q = qp.get('q') ?? '';
    for (const t of qp.getAll('tags')) this.selectedTags.add(t);
    this.api.tags(this.session.ws()).subscribe((t) => this.tags.set(t));
    if (this.q || this.selectedTags.size) this.run();
  }

  reload(): void { clearTimeout(this.debounce); this.debounce = setTimeout(() => this.run(), 250); }

  run(): void {
    const tags = [...this.selectedTags];
    if (!this.q.trim() && !tags.length && !this.severity && !this.status && !this.entityType) { this.results.set(null); this.searched.set(false); return; }
    this.searched.set(true);
    this.results.set(null);
    this.api.search(this.session.ws(), { q: this.q || undefined, tags: tags.length ? tags : undefined, tagMode: this.tagMode,
      entityType: this.entityType || undefined, severity: this.severity || undefined, status: this.status || undefined })
      .subscribe((p) => this.results.set(p));
  }

  toggleTag(tag: string): void {
    if (this.selectedTags.has(tag)) this.selectedTags.delete(tag); else this.selectedTags.add(tag);
    this.run();
  }

  navKey(entity: string): string { return 'nav.' + (entity === 'client' ? 'clients' : entity === 'assessment' ? 'assessments' : entity === 'finding' ? 'findings' : 'reports'); }
  route(h: SearchHit): string { return `${ROUTE[h.entityType] ?? '/dashboard'}/${h.entityId}`; }

  /** Split a ts_headline snippet on the U+2063 delimiter into plain/highlighted segments (no innerHTML). */
  segments(snippet: string): { text: string; hit: boolean }[] {
    return snippet.split(MARK).map((text, i) => ({ text, hit: i % 2 === 1 })).filter((s) => s.text);
  }
}
