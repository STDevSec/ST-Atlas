import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { AssessmentType, Client } from '../core/api/models';
import { I18n } from '../core/i18n/i18n.service';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { Toasts } from '../core/toast.service';
import { PageHeader } from '../shared/ui';

@Component({
  selector: 'app-assessment-form',
  imports: [FormsModule, RouterLink, TPipe, PageHeader],
  template: `
  <app-page-header [title]="(id() ? 'common.edit' : 'assessment.new') | t" />
  <form class="card p-5 grid md:grid-cols-2 gap-4 max-w-4xl" (submit)="save($event)" #f="ngForm">
    <div class="md:col-span-2"><label class="label" for="t">{{ 'assessment.title' | t }}</label>
      <input id="t" class="input" name="title" [(ngModel)]="m.title" required maxlength="300" /></div>
    <div><label class="label" for="type">{{ 'assessment.type' | t }}</label>
      <select id="type" class="input" name="type" [(ngModel)]="m.typeCode" required>
        @for (t of types(); track t.code) { <option [value]="t.code">{{ 'type.' + t.code | t }}</option> }
      </select></div>
    <div><label class="label" for="c">{{ 'common.client' | t }}</label>
      <select id="c" class="input" name="client" [(ngModel)]="m.clientId">
        <option [ngValue]="null">{{ 'assessment.noClient' | t }}</option>
        @for (c of clients(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
      </select></div>
    <div><label class="label" for="sd">{{ 'assessment.startDate' | t }}</label><input id="sd" class="input" type="date" name="sd" [(ngModel)]="m.startDate" /></div>
    <div><label class="label" for="dd">{{ 'assessment.dueDate' | t }}</label><input id="dd" class="input" type="date" name="dd" [(ngModel)]="m.dueDate" /></div>
    <div class="md:col-span-2"><label class="label" for="tags">{{ 'common.tags' | t }}</label>
      <input id="tags" class="input" name="tags" [(ngModel)]="tags" [placeholder]="'assessment.tagsHelp' | t" /></div>
    @for (f of textFields; track f.key) {
      <div class="md:col-span-2"><label class="label" [for]="f.key">{{ f.label | t }}</label>
        <textarea [id]="f.key" class="input min-h-20" [name]="f.key" [(ngModel)]="m[f.key]" maxlength="20000"></textarea></div>
    }
    <div class="md:col-span-2 flex justify-end gap-2">
      <a class="btn" [routerLink]="id() ? ['/assessments', id()] : ['/assessments']">{{ 'common.cancel' | t }}</a>
      <button class="btn btn-primary" [disabled]="busy() || !f.valid">{{ 'common.save' | t }}</button>
    </div>
  </form>`,
})
export class AssessmentFormPage {
  readonly id = input<string>();
  private readonly api = inject(Api);
  private readonly session = inject(Session);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);
  private readonly i18n = inject(I18n);
  protected readonly types = signal<AssessmentType[]>([]);
  protected readonly clients = signal<Client[]>([]);
  protected readonly busy = signal(false);
  protected tags = '';
  protected m: Record<string, string | null> = { title: '', typeCode: 'WEB_PENTEST', clientId: null, startDate: null, dueDate: null,
    description: '', objectives: '', scope: '', outOfScope: '', rulesOfEngagement: '', methodology: '' };
  protected readonly textFields = [
    { key: 'description', label: 'common.description' }, { key: 'objectives', label: 'assessment.objectives' },
    { key: 'scope', label: 'assessment.scope' }, { key: 'outOfScope', label: 'assessment.outOfScope' },
    { key: 'rulesOfEngagement', label: 'assessment.roe' }, { key: 'methodology', label: 'assessment.methodology' },
  ];

  constructor() {
    this.api.assessmentTypes().subscribe((t) => this.types.set(t));
    this.api.clients(this.session.ws(), undefined, 0, 100).subscribe((p) => this.clients.set(p.items));
    queueMicrotask(() => {
      const id = this.id();
      if (id) {
        this.api.assessment(this.session.ws(), id).subscribe((a) => {
          this.m = { title: a.title, typeCode: a.typeCode, clientId: a.clientId, startDate: a.startDate, dueDate: a.dueDate,
            description: a.description, objectives: a.objectives, scope: a.scope, outOfScope: a.outOfScope,
            rulesOfEngagement: a.rulesOfEngagement, methodology: a.methodology };
          this.tags = a.tags.join(', ');
        });
      }
    });
  }

  save(e: Event): void {
    e.preventDefault();
    this.busy.set(true);
    const body = { ...this.m, startDate: this.m['startDate'] || null, dueDate: this.m['dueDate'] || null,
      tags: this.tags.split(',').map((t) => t.trim()).filter(Boolean) };
    this.api.saveAssessment(this.session.ws(), body, this.id()).subscribe({
      next: (a) => { this.toasts.ok(this.i18n.t('common.saved')); void this.router.navigate(['/assessments', a.id]); },
      error: (err) => { this.busy.set(false); this.toasts.error(err, this.i18n.t('common.error')); },
    });
  }
}
