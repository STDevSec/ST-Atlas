import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { Assessment, REMEDIATIONS, SEVERITIES } from '../core/api/models';
import { I18n } from '../core/i18n/i18n.service';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { Toasts } from '../core/toast.service';
import { cvss31 } from '../shared/cvss';
import { IconComponent } from '../shared/icon.component';
import { PageHeader, SeverityBadge } from '../shared/ui';

function list(s: string, sep = ','): string[] { return s.split(sep).map((x) => x.trim()).filter(Boolean); }

@Component({
  selector: 'app-finding-form',
  imports: [FormsModule, RouterLink, TPipe, IconComponent, PageHeader, SeverityBadge],
  template: `
  <app-page-header [title]="findingId() ? code() + ' · ' + m.title : ('finding.new' | t)" [subtitle]="assessment()?.title ?? null">
    @if (assessment(); as a) {
      <a crumbs [routerLink]="['/assessments', a.id]" class="faint text-sm inline-flex items-center gap-1 mb-1"><app-icon name="ArrowLeft" [size]="14" />{{ a.code }}</a>
    }
    @if (findingId() && session.canWrite()) { <button class="btn btn-danger" (click)="remove()"><app-icon name="Trash2" />{{ 'common.delete' | t }}</button> }
  </app-page-header>
  <form class="grid lg:grid-cols-3 gap-4" (submit)="save($event)" #f="ngForm">
    <div class="card p-5 lg:col-span-2 space-y-4">
      <div><label class="label" for="t">{{ 'common.title' | t }}</label><input id="t" class="input" name="title" [(ngModel)]="m.title" required maxlength="300" [disabled]="!session.canWrite()" /></div>
      @for (fld of textFields; track fld.key) {
        <div><label class="label" [for]="fld.key">{{ fld.label | t }}</label>
          <textarea [id]="fld.key" class="input min-h-24" [name]="fld.key" [(ngModel)]="m[fld.key]" [disabled]="!session.canWrite()"></textarea></div>
      }
      <div><label class="label" for="refs">{{ 'finding.references' | t }}</label>
        <textarea id="refs" class="input min-h-16 font-mono text-xs" name="refs" [(ngModel)]="refs" [disabled]="!session.canWrite()"></textarea></div>
    </div>
    <div class="card p-5 space-y-4 h-fit">
      @if (assessment()?.usesCvss) {
        <div><label class="label" for="v31">{{ 'finding.cvss31' | t }}</label>
          <input id="v31" class="input font-mono text-xs" name="v31" [(ngModel)]="m.cvss31Vector" placeholder="CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H" [disabled]="!session.canWrite()" />
          @if (preview() !== null) { <p class="text-xs mt-1 faint">{{ 'finding.cvssAuto' | t }}: <strong>{{ preview() }}</strong></p> }
        </div>
        <div class="grid grid-cols-3 gap-2">
          <div class="col-span-2"><label class="label" for="v40">{{ 'finding.cvss40' | t }}</label>
            <input id="v40" class="input font-mono text-xs" name="v40" [(ngModel)]="m.cvss40Vector" [disabled]="!session.canWrite()" /></div>
          <div><label class="label" for="s40">{{ 'finding.cvss40Score' | t }}</label>
            <input id="s40" class="input" type="number" min="0" max="10" step="0.1" name="s40" [(ngModel)]="m.cvss40Score" [disabled]="!session.canWrite()" /></div>
        </div>
      } @else { <p class="faint text-xs">{{ 'finding.noCvss' | t }}</p> }
      <div><label class="label" for="sev">{{ 'finding.severity' | t }}</label>
        <select id="sev" class="input" name="sev" [(ngModel)]="m.severity" [disabled]="!session.canWrite()">
          <option [ngValue]="null">{{ preview() !== null ? '— (CVSS)' : '—' }}</option>
          @for (s of severities; track s) { <option [value]="s">{{ 'sev.' + s | t }}</option> }
        </select>
        @if (m.severity) { <div class="mt-2"><app-severity [value]="'' + m.severity" /></div> }</div>
      <div><label class="label" for="st">{{ 'finding.remediation' | t }}</label>
        <select id="st" class="input" name="st" [(ngModel)]="m.status" [disabled]="!session.canWrite()">
          @for (s of remediations; track s) { <option [value]="s">{{ 'rem.' + s | t }}</option> }
        </select></div>
      <div><label class="label" for="cwe">{{ 'finding.cwe' | t }} <span class="faint">({{ 'finding.listsHelp' | t }})</span></label>
        <input id="cwe" class="input" name="cwe" [(ngModel)]="cwe" placeholder="CWE-79" [disabled]="!session.canWrite()" /></div>
      <div><label class="label" for="cve">{{ 'finding.cve' | t }}</label>
        <input id="cve" class="input" name="cve" [(ngModel)]="cve" placeholder="CVE-2024-12345" [disabled]="!session.canWrite()" /></div>
      <div><label class="label" for="tags">{{ 'common.tags' | t }}</label><input id="tags" class="input" name="tags" [(ngModel)]="tags" [disabled]="!session.canWrite()" /></div>
      <div><label class="label" for="d">{{ 'finding.discovered' | t }}</label><input id="d" class="input" type="date" name="d" [(ngModel)]="m.discoveredOn" [disabled]="!session.canWrite()" /></div>
      @if (session.canWrite()) { <button class="btn btn-primary w-full justify-center" [disabled]="busy() || !f.valid">{{ 'common.save' | t }}</button> }
    </div>
  </form>`,
})
export class FindingFormPage {
  readonly assessmentId = input<string>();
  readonly findingId = input<string>();
  protected readonly session = inject(Session);
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);
  private readonly i18n = inject(I18n);
  protected readonly assessment = signal<Assessment | null>(null);
  protected readonly code = signal('');
  protected readonly busy = signal(false);
  protected readonly severities = SEVERITIES;
  protected readonly remediations = REMEDIATIONS;
  protected m: Record<string, string | number | null> = { title: '', description: '', technicalDetails: '', impact: '', recommendation: '',
    severity: null, cvss31Vector: '', cvss40Vector: '', cvss40Score: null, status: 'OPEN', discoveredOn: null };
  protected refs = '';
  protected cwe = '';
  protected cve = '';
  protected tags = '';
  private readonly vector = signal('');
  protected readonly preview = computed(() => cvss31(this.vector()));
  protected readonly textFields = [
    { key: 'description', label: 'common.description' }, { key: 'technicalDetails', label: 'finding.technical' },
    { key: 'impact', label: 'finding.impact' }, { key: 'recommendation', label: 'finding.recommendation' },
  ];

  constructor() {
    queueMicrotask(() => {
      const ws = this.session.ws();
      const fid = this.findingId();
      if (fid) {
        this.api.finding(ws, fid).subscribe((f) => {
          this.code.set(f.code);
          this.m = { title: f.title, description: f.description, technicalDetails: f.technicalDetails, impact: f.impact,
            recommendation: f.recommendation, severity: f.severity, cvss31Vector: f.cvss31Vector ?? '', cvss40Vector: f.cvss40Vector ?? '',
            cvss40Score: f.cvss40Score, status: f.status, discoveredOn: f.discoveredOn };
          this.refs = f.references.join('\n');
          this.cwe = f.cwe.join(', ');
          this.cve = f.cve.join(', ');
          this.tags = f.tags.join(', ');
          this.vector.set(f.cvss31Vector ?? '');
          this.api.assessment(ws, f.assessmentId).subscribe((a) => this.assessment.set(a));
        });
      } else if (this.assessmentId()) {
        this.api.assessment(ws, this.assessmentId()!).subscribe((a) => this.assessment.set(a));
      }
    });
    setInterval(() => this.vector.set(String(this.m['cvss31Vector'] ?? '')), 300);
  }

  save(e: Event): void {
    e.preventDefault();
    this.busy.set(true);
    const body = { ...this.m, cvss31Vector: this.m['cvss31Vector'] || null, cvss40Vector: this.m['cvss40Vector'] || null,
      cvss40Score: this.m['cvss40Score'] === '' ? null : this.m['cvss40Score'], discoveredOn: this.m['discoveredOn'] || null,
      references: list(this.refs, '\n'), cwe: list(this.cwe), cve: list(this.cve), tags: list(this.tags) };
    const ws = this.session.ws();
    const req = this.findingId() ? this.api.updateFinding(ws, this.findingId()!, body) : this.api.createFinding(ws, this.assessmentId()!, body);
    req.subscribe({
      next: (f) => { this.toasts.ok(this.i18n.t('common.saved')); void this.router.navigate(['/assessments', f.assessmentId], { queryParams: { tab: 'findings' } }); },
      error: (err) => { this.busy.set(false); this.toasts.error(err, this.i18n.t('common.error')); },
    });
  }

  remove(): void {
    if (!confirm(this.i18n.t('common.confirmDelete'))) return;
    const a = this.assessment()?.id;
    this.api.deleteFinding(this.session.ws(), this.findingId()!).subscribe({
      next: () => void this.router.navigate(a ? ['/assessments', a] : ['/findings'], a ? { queryParams: { tab: 'findings' } } : {}),
      error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }
}
