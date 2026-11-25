import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api/api.service';
import { Classification, Template, TemplateConfig } from '../core/api/models';
import { I18n } from '../core/i18n/i18n.service';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { Toasts } from '../core/toast.service';
import { EmptyState, PageHeader, Skeleton } from '../shared/ui';
import { IconComponent } from '../shared/icon.component';

const CLASSIFICATIONS: Classification[] = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'];

function blankConfig(): TemplateConfig {
  return { title: '{{assessment.title}}', subtitle: '{{client.name}}', paperSize: 'A4', orientation: 'PORTRAIT',
    margins: { topMm: 20, rightMm: 18, bottomMm: 20, leftMm: 18 }, primaryColor: '#1e3a5f', accentColor: '#2563eb',
    fontFamily: 'SANS', baseFontSizePt: 10, showCover: true, showOrganizationLogo: true, showClientLogo: true,
    organizationLogoAttachmentId: null, logoPosition: 'LEFT', headerText: null, footerText: null, pageNumbers: true,
    classification: 'CONFIDENTIAL', watermarkText: null, showFindingsSummary: true, showCvss: true };
}

@Component({
  selector: 'app-templates',
  imports: [FormsModule, TPipe, PageHeader, EmptyState, Skeleton, IconComponent],
  template: `
  <app-page-header [title]="'nav.templates' | t">
    @if (session.canWrite()) {
      <label class="btn"><app-icon name="Upload" />{{ 'template.import' | t }}<input type="file" accept="application/json" class="sr-only" (change)="import($event)" /></label>
      <button class="btn btn-primary" (click)="newTemplate()"><app-icon name="Plus" />{{ 'template.new' | t }}</button>
    }
  </app-page-header>
  @if (templates(); as list) {
    <div class="grid lg:grid-cols-[280px_1fr] gap-4 items-start">
      <div class="card overflow-hidden">
        @for (t of list; track t.id) {
          <button class="w-full text-left px-4 py-2.5 border-b hairline last:border-0 flex items-center gap-2"
                  [class.bg-[var(--surface-muted)]]="sel()?.id === t.id" (click)="select(t)">
            <app-icon name="Palette" [size]="15" class="faint" />
            <span class="flex-1 truncate">{{ t.name }}</span>
            @if (t.builtIn) { <span class="chip !text-[11px]">{{ 'template.builtIn' | t }}</span> }
          </button>
        } @empty { <app-empty [text]="'common.none' | t" /> }
      </div>

      @if (sel(); as t) {
        <div class="card p-5 space-y-4">
          <div class="flex items-center gap-2">
            <input class="input text-lg font-medium flex-1" [(ngModel)]="name" [disabled]="t.builtIn || !session.canWrite()" [attr.aria-label]="'common.name' | t" />
            @if (t.builtIn) { <span class="chip">{{ 'template.readonly' | t }}</span> }
          </div>

          <div class="grid sm:grid-cols-2 gap-3">
            <label class="text-sm">{{ 'template.title' | t }}<input class="input" [(ngModel)]="cfg.title" [disabled]="ro()" /></label>
            <label class="text-sm">{{ 'template.subtitle' | t }}<input class="input" [ngModel]="cfg.subtitle ?? ''" (ngModelChange)="cfg.subtitle = $event || null" [disabled]="ro()" /></label>
            <label class="text-sm">{{ 'template.paper' | t }}<select class="input" [(ngModel)]="cfg.paperSize" [disabled]="ro()"><option value="A4">A4</option><option value="LETTER">Letter</option></select></label>
            <label class="text-sm">{{ 'template.orientation' | t }}<select class="input" [(ngModel)]="cfg.orientation" [disabled]="ro()"><option value="PORTRAIT">{{ title('PORTRAIT') }}</option><option value="LANDSCAPE">{{ title('LANDSCAPE') }}</option></select></label>
            <label class="text-sm">{{ 'template.font' | t }}<select class="input" [(ngModel)]="cfg.fontFamily" [disabled]="ro()"><option value="SANS">Sans</option><option value="SERIF">Serif</option></select></label>
            <label class="text-sm">{{ 'template.fontSize' | t }}<input class="input" type="number" min="8" max="14" step="0.5" [(ngModel)]="cfg.baseFontSizePt" [disabled]="ro()" /></label>
            <label class="text-sm flex flex-col">{{ 'template.primary' | t }}<input class="input h-10 p-1" type="color" [(ngModel)]="cfg.primaryColor" [disabled]="ro()" /></label>
            <label class="text-sm flex flex-col">{{ 'template.accent' | t }}<input class="input h-10 p-1" type="color" [(ngModel)]="cfg.accentColor" [disabled]="ro()" /></label>
            <label class="text-sm">{{ 'report.classification' | t }}<select class="input" [(ngModel)]="cfg.classification" [disabled]="ro()">@for (c of classifications; track c) { <option [value]="c">{{ 'class.' + c | t }}</option> }</select></label>
            <label class="text-sm">{{ 'template.logoPosition' | t }}<select class="input" [(ngModel)]="cfg.logoPosition" [disabled]="ro()"><option value="LEFT">{{ title('LEFT') }}</option><option value="CENTER">{{ title('CENTER') }}</option><option value="RIGHT">{{ title('RIGHT') }}</option></select></label>
            <label class="text-sm">{{ 'template.header' | t }}<input class="input" [ngModel]="cfg.headerText ?? ''" (ngModelChange)="cfg.headerText = $event || null" [disabled]="ro()" /></label>
            <label class="text-sm">{{ 'template.footer' | t }}<input class="input" [ngModel]="cfg.footerText ?? ''" (ngModelChange)="cfg.footerText = $event || null" [disabled]="ro()" /></label>
            <label class="text-sm sm:col-span-2">{{ 'template.watermark' | t }}<input class="input" [ngModel]="cfg.watermarkText ?? ''" (ngModelChange)="cfg.watermarkText = $event || null" [disabled]="ro()" /></label>
          </div>

          <div class="flex flex-wrap gap-4 text-sm">
            @for (t of toggles; track t.key) {
              <label class="flex items-center gap-1.5"><input type="checkbox" [(ngModel)]="cfg[t.key]" [disabled]="ro()" />{{ t.label | t }}</label>
            }
          </div>

          <div class="flex flex-wrap gap-2 pt-2 border-t hairline">
            @if (!t.builtIn && session.canWrite()) { <button class="btn btn-primary" [disabled]="busy()" (click)="save()"><app-icon name="Check" />{{ 'common.save' | t }}</button> }
            @if (session.canWrite()) { <button class="btn" (click)="duplicate(t)"><app-icon name="Copy" />{{ 'template.duplicate' | t }}</button> }
            <button class="btn" (click)="exportJson(t)"><app-icon name="Download" />{{ 'template.export' | t }}</button>
            @if (!t.builtIn && session.canWrite()) { <button class="btn btn-danger ml-auto" (click)="remove(t)"><app-icon name="Trash2" />{{ 'common.delete' | t }}</button> }
          </div>
        </div>
      } @else { <div class="card"><app-empty [text]="'template.preview' | t" /></div> }
    </div>
  } @else { <div class="card"><app-skeleton [rows]="6" /></div> }`,
})
export class TemplatesPage {
  protected readonly session = inject(Session);
  private readonly api = inject(Api);
  private readonly i18n = inject(I18n);
  private readonly toasts = inject(Toasts);
  protected readonly templates = signal<Template[] | null>(null);
  protected readonly sel = signal<Template | null>(null);
  protected readonly busy = signal(false);
  protected readonly classifications = CLASSIFICATIONS;
  protected name = '';
  protected cfg: TemplateConfig = blankConfig();
  protected readonly toggles = [
    { key: 'showCover', label: 'template.cover' }, { key: 'showFindingsSummary', label: 'template.findingsSummary' },
    { key: 'showCvss', label: 'template.showCvss' }, { key: 'pageNumbers', label: 'template.pageNumbers' },
    { key: 'showOrganizationLogo', label: 'template.orgLogo' }, { key: 'showClientLogo', label: 'template.clientLogo' },
  ] as const satisfies readonly { key: keyof TemplateConfig; label: string }[];

  constructor() { this.load(); }

  ro(): boolean { return (this.sel()?.builtIn ?? true) || !this.session.canWrite(); }
  title(s: string): string { return s.charAt(0) + s.slice(1).toLowerCase(); }

  load(keepId?: string): void {
    this.api.templates(this.session.ws()).subscribe((t) => {
      this.templates.set(t);
      const pick = keepId ? t.find((x) => x.id === keepId) : t[0];
      if (pick) this.select(pick);
    });
  }

  select(t: Template): void {
    this.sel.set(t);
    this.name = t.name;
    this.cfg = structuredClone(t.config);
  }

  newTemplate(): void {
    this.sel.set({ id: '', name: this.i18n.t('template.new'), builtIn: false, config: blankConfig(), updatedAt: '' });
    this.name = this.i18n.t('template.new');
    this.cfg = blankConfig();
  }

  save(): void {
    this.busy.set(true);
    const id = this.sel()?.id || undefined;
    this.api.saveTemplate(this.session.ws(), this.name, this.cfg, id).subscribe({
      next: (t) => { this.busy.set(false); this.toasts.ok(this.i18n.t('common.saved')); this.load(t.id); },
      error: (err) => { this.busy.set(false); this.toasts.error(err, this.i18n.t('common.error')); },
    });
  }

  duplicate(t: Template): void {
    this.api.duplicateTemplate(this.session.ws(), t.id).subscribe({
      next: (n) => this.load(n.id), error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }

  remove(t: Template): void {
    if (!confirm(this.i18n.t('common.confirmDelete'))) return;
    this.api.deleteTemplate(this.session.ws(), t.id).subscribe({
      next: () => { this.sel.set(null); this.load(); }, error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }

  exportJson(t: Template): void {
    const blob = new Blob([JSON.stringify({ name: t.name, config: t.config }, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `${t.name.replace(/[^a-z0-9]+/gi, '-').toLowerCase()}.atlas-template.json`;
    a.click();
    URL.revokeObjectURL(a.href);
  }

  import(e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    file.text().then((txt) => {
      const parsed = JSON.parse(txt) as { name?: string; config: TemplateConfig };
      this.api.saveTemplate(this.session.ws(), parsed.name ?? file.name, { ...blankConfig(), ...parsed.config }).subscribe({
        next: (t) => { this.toasts.ok(this.i18n.t('common.saved')); this.load(t.id); },
        error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
      });
    }).catch(() => this.toasts.show(this.i18n.t('common.error'), 'error'));
  }
}
