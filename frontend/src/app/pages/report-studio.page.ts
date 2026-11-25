import { Component, ElementRef, OnDestroy, effect, inject, input, signal, viewChild, AfterViewInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { TableKit } from '@tiptap/extension-table';
import CodeBlockLowlight from '@tiptap/extension-code-block-lowlight';
import Highlight from '@tiptap/extension-highlight';
import Subscript from '@tiptap/extension-subscript';
import Superscript from '@tiptap/extension-superscript';
import TextAlign from '@tiptap/extension-text-align';
import { Placeholder } from '@tiptap/extensions';
import { common, createLowlight } from 'lowlight';
import { firstValueFrom } from 'rxjs';
import { Api } from '../core/api/api.service';
import { Assessment, CLASSIFICATIONS, Finding, ReportDetail, ReportVersion, Template } from '../core/api/models';
import { I18n } from '../core/i18n/i18n.service';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { Toasts } from '../core/toast.service';
import { FindingsTable, PageBreak, atlasImage, findingRef } from '../editor/extensions';
import { IconComponent } from '../shared/icon.component';

type SaveState = 'saved' | 'dirty' | 'saving' | 'conflict' | 'error';

@Component({
  selector: 'app-report-studio',
  imports: [FormsModule, RouterLink, DatePipe, TPipe, IconComponent],
  template: `
  <div [class.fixed]="full()" [class.inset-0]="full()" [class.z-40]="full()" [class.overflow-y-auto]="full()" [class.p-6]="full()"
       [style.background]="full() ? 'var(--surface-muted)' : null">
    @if (report(); as r) {
      <div class="flex flex-wrap items-center gap-2 mb-3">
        @if (assessment(); as a) {
          <a [routerLink]="['/assessments', a.id]" class="faint text-sm inline-flex items-center gap-1"><app-icon name="ArrowLeft" [size]="14" />{{ a.code }}</a>
        }
        <input class="input text-lg font-semibold flex-1 min-w-64 !border-transparent hover:!border-[var(--border-strong)]" [(ngModel)]="title"
               (ngModelChange)="markDirty()" [attr.aria-label]="'common.title' | t" [disabled]="!session.canWrite()" />
        <span class="text-xs" [class.text-red-600]="state() === 'conflict' || state() === 'error'" [class.faint]="state() !== 'conflict'">
          @switch (state()) { @case ('saved') { {{ 'common.saved' | t }} } @case ('saving') { {{ 'common.saving' | t }} }
            @case ('dirty') { {{ 'report.unsaved' | t }} } @case ('conflict') { {{ 'report.conflict' | t }} } @default { {{ 'common.error' | t }} } }
        </span>
        <select class="input w-auto" [(ngModel)]="templateId" (ngModelChange)="markDirty()" [attr.aria-label]="'report.template' | t" [disabled]="!session.canWrite()">
          @for (t of templates(); track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }</select>
        <select class="input w-auto" [(ngModel)]="classification" (ngModelChange)="markDirty()" [attr.aria-label]="'report.classification' | t" [disabled]="!session.canWrite()">
          @for (c of classifications; track c) { <option [value]="c">{{ 'class.' + c | t }}</option> }</select>
        <button class="btn" (click)="full.set(!full())" [attr.aria-label]="'report.fullscreen' | t"><app-icon [name]="full() ? 'Minimize2' : 'Maximize2'" /></button>
        @if (session.canWrite()) {
          <button class="btn btn-primary" (click)="render(false)" [disabled]="rendering()"><app-icon name="FileDown" />{{ (rendering() ? 'report.rendering' : 'report.render') | t }}</button>
        }
      </div>

      <div class="grid xl:grid-cols-[1fr_280px] gap-4">
        <div class="card">
          @if (session.canWrite()) {
            <div class="sticky top-0 z-10 flex flex-wrap items-center gap-0.5 p-1.5 border-b hairline rounded-t-lg" style="background:var(--surface-raised)" role="toolbar" aria-label="Formatting">
              @for (b of buttons; track b.title) {
                @if (b.sep) { <span class="w-px h-5 mx-1" style="background:var(--border)"></span> }
                <button type="button" class="btn btn-ghost btn-sm !px-1.5" [title]="b.title" [attr.aria-label]="b.title"
                        [class.!bg-[var(--surface-muted)]]="b.active?.()" (mousedown)="$event.preventDefault()" (click)="b.run()">
                  <app-icon [name]="b.icon" />
                </button>
              }
              <span class="w-px h-5 mx-1" style="background:var(--border)"></span>
              <label class="btn btn-ghost btn-sm !px-1.5 cursor-pointer" title="Image"><app-icon name="Image" />
                <input type="file" accept="image/png,image/jpeg,image/gif,image/webp" class="sr-only" (change)="pickImage($event)" /></label>
              <select class="input w-auto text-xs py-1" (change)="insertFinding($event)" [attr.aria-label]="'report.insertFinding' | t">
                <option value="">{{ 'report.insertFinding' | t }}…</option>
                @for (f of findings(); track f.id) { <option [value]="f.id">{{ f.code }} · {{ f.title }}</option> }
              </select>
              <button class="btn btn-ghost btn-sm text-xs" (mousedown)="$event.preventDefault()" (click)="insertBlock({ type: 'findingsTable' })">{{ 'report.insertFindings' | t }}</button>
              <button class="btn btn-ghost btn-sm text-xs" (mousedown)="$event.preventDefault()" (click)="insertBlock({ type: 'pageBreak' })">{{ 'report.pageBreak' | t }}</button>
            </div>
          }
          <div class="px-10 py-8 max-w-[900px] mx-auto"><div #host></div></div>
        </div>
        <aside class="space-y-4">
          <section class="card p-4">
            <h2 class="font-medium mb-2 flex items-center gap-2"><app-icon name="History" />{{ 'report.versions' | t }}</h2>
            @for (v of versions(); track v.id) {
              <a class="flex justify-between items-center text-sm py-1.5 border-b hairline last:border-0" [routerLink]="['/reports', r.id, 'versions', v.number]">
                <span>v{{ v.number }} @if (v.finalized) { <span class="chip !text-[11px]"><app-icon name="Lock" [size]="11" />{{ 'report.finalized' | t }}</span> }</span>
                <span class="faint text-xs">{{ v.createdAt | date: 'short' }}</span>
              </a>
            } @empty { <p class="muted text-sm">{{ 'report.noVersions' | t }}</p> }
            @if (session.canReview() && versions().length) {
              <button class="btn btn-sm mt-3 w-full justify-center" (click)="render(true)" [disabled]="rendering()"><app-icon name="Lock" />{{ 'report.finalize' | t }}</button>
            }
          </section>
          <section class="card p-4 text-xs muted space-y-2">
            <p>{{ 'report.pasteHint' | t }}</p>
            <p class="font-medium">{{ 'report.variables' | t }}</p>
            <p class="font-mono leading-5">{{ variables }}</p>
          </section>
        </aside>
      </div>
    }
  </div>`,
})
export class ReportStudioPage implements AfterViewInit, OnDestroy {
  readonly id = input.required<string>();
  protected readonly session = inject(Session);
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);
  private readonly i18n = inject(I18n);
  private readonly host = viewChild<ElementRef<HTMLElement>>('host');

  protected readonly report = signal<ReportDetail | null>(null);
  protected readonly assessment = signal<Assessment | null>(null);
  protected readonly findings = signal<Finding[]>([]);
  private readonly findingsLoaded = signal(false);
  protected readonly templates = signal<Template[]>([]);
  protected readonly versions = signal<ReportVersion[]>([]);
  protected readonly state = signal<SaveState>('saved');
  protected readonly rendering = signal(false);
  protected readonly full = signal(false);
  protected readonly classifications = CLASSIFICATIONS;
  protected readonly variables = '{{client.name}} {{organization.name}} {{assessment.title}} {{assessment.type}} {{assessment.startDate}} {{assessment.endDate}} {{report.author}} {{report.version}} {{report.classification}}';
  protected title = '';
  protected templateId: string | null = null;
  protected classification = 'CONFIDENTIAL';
  protected editor: Editor | null = null;
  private revision = 0;
  private timer?: ReturnType<typeof setTimeout>;
  private readonly beforeUnload = (e: BeforeUnloadEvent) => { if (this.state() !== 'saved') e.preventDefault(); };

  protected readonly buttons: { icon: string; title: string; run: () => void; active?: () => boolean; sep?: boolean }[] = [
    { icon: 'Undo2', title: 'Undo', run: () => this.cmd().undo().run() },
    { icon: 'Redo2', title: 'Redo', run: () => this.cmd().redo().run() },
    { icon: 'Heading1', title: 'Heading 1', sep: true, run: () => this.cmd().toggleHeading({ level: 1 }).run(), active: () => !!this.editor?.isActive('heading', { level: 1 }) },
    { icon: 'Heading2', title: 'Heading 2', run: () => this.cmd().toggleHeading({ level: 2 }).run(), active: () => !!this.editor?.isActive('heading', { level: 2 }) },
    { icon: 'Heading3', title: 'Heading 3', run: () => this.cmd().toggleHeading({ level: 3 }).run(), active: () => !!this.editor?.isActive('heading', { level: 3 }) },
    { icon: 'Bold', title: 'Bold', sep: true, run: () => this.cmd().toggleBold().run(), active: () => !!this.editor?.isActive('bold') },
    { icon: 'Italic', title: 'Italic', run: () => this.cmd().toggleItalic().run(), active: () => !!this.editor?.isActive('italic') },
    { icon: 'Underline', title: 'Underline', run: () => this.cmd().toggleUnderline().run(), active: () => !!this.editor?.isActive('underline') },
    { icon: 'Strikethrough', title: 'Strike', run: () => this.cmd().toggleStrike().run(), active: () => !!this.editor?.isActive('strike') },
    { icon: 'Highlighter', title: 'Highlight', run: () => this.cmd().toggleHighlight().run(), active: () => !!this.editor?.isActive('highlight') },
    { icon: 'Code', title: 'Inline code', run: () => this.cmd().toggleCode().run(), active: () => !!this.editor?.isActive('code') },
    { icon: 'Link', title: 'Link', run: () => this.link(), active: () => !!this.editor?.isActive('link') },
    { icon: 'List', title: 'Bullet list', sep: true, run: () => this.cmd().toggleBulletList().run(), active: () => !!this.editor?.isActive('bulletList') },
    { icon: 'ListOrdered', title: 'Numbered list', run: () => this.cmd().toggleOrderedList().run(), active: () => !!this.editor?.isActive('orderedList') },
    { icon: 'Quote', title: 'Quote', run: () => this.cmd().toggleBlockquote().run(), active: () => !!this.editor?.isActive('blockquote') },
    { icon: 'SquareCode', title: 'Code block', run: () => this.cmd().toggleCodeBlock().run(), active: () => !!this.editor?.isActive('codeBlock') },
    { icon: 'Table', title: 'Table', run: () => this.cmd().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run() },
    { icon: 'Minus', title: 'Separator', run: () => this.cmd().setHorizontalRule().run() },
  ];

  // The editor host only exists once the report has rendered (zoneless: not synchronously).
  private readonly mountEditor = effect(() => {
    const host = this.host();
    const r = this.report();
    if (host && r && this.findingsLoaded() && !this.editor) this.createEditor(r, host.nativeElement);
  });

  private cmd() { return this.editor!.chain().focus(); }

  ngAfterViewInit(): void {
    window.addEventListener('beforeunload', this.beforeUnload);
    const ws = this.session.ws();
    this.api.report(ws, this.id()).subscribe({
      next: (r) => {
        this.report.set(r);
        this.title = r.title;
        this.templateId = r.templateId;
        this.classification = r.classification;
        this.revision = r.revision;
        this.api.assessment(ws, r.assessmentId).subscribe((a) => this.assessment.set(a));
        this.api.findings(ws, r.assessmentId).subscribe((f) => { this.findings.set(f); this.findingsLoaded.set(true); });
      },
      error: () => void this.router.navigateByUrl('/reports'),
    });
    this.api.templates(ws).subscribe((t) => this.templates.set(t));
    this.loadVersions();
  }

  private createEditor(r: ReportDetail, element: HTMLElement): void {
    const ws = this.session.ws();
    const lowlight = createLowlight(common);
    this.editor = new Editor({
      element,
      editable: this.session.canWrite(),
      content: JSON.parse(r.document),
      extensions: [
        StarterKit.configure({ codeBlock: false, heading: { levels: [1, 2, 3] },
          link: { openOnClick: false, autolink: true, protocols: ['http', 'https', 'mailto'] } }),
        CodeBlockLowlight.configure({ lowlight }),
        TableKit.configure({ table: { resizable: false } }),
        Highlight, Subscript, Superscript, TextAlign.configure({ types: ['heading', 'paragraph'] }),
        Placeholder.configure({ placeholder: this.i18n.t('report.pasteHint') }),
        atlasImage((id) => this.api.attachmentUrl(ws, id)),
        PageBreak, FindingsTable,
        findingRef((id) => {
          const f = this.findings().find((x) => x.id === id);
          return f ? `◆ ${f.code} · ${f.title} (${this.i18n.t('sev.' + f.severity)})` : `◆ ${id}`;
        }),
      ],
      editorProps: {
        handlePaste: (_view, event) => this.handleFiles(event.clipboardData?.files),
        handleDrop: (_view, event) => this.handleFiles((event as DragEvent).dataTransfer?.files),
      },
      onUpdate: () => this.markDirty(),
    });
  }

  /** Clipboard/drag images are uploaded first, then inserted by reference (never Base64). */
  private handleFiles(files: FileList | undefined | null): boolean {
    const images = Array.from(files ?? []).filter((f) => f.type.startsWith('image/'));
    if (!images.length || !this.session.canWrite()) return false;
    for (const file of images) this.uploadAndInsert(file);
    return true;
  }

  private uploadAndInsert(file: File): void {
    this.toasts.show(this.i18n.t('report.uploading'));
    const name = file.name && file.name !== 'image.png' ? file.name : `screenshot-${new Date().toISOString().slice(0, 19)}.png`;
    this.api.upload(this.session.ws(), file, name).subscribe({
      next: (a) => this.insertBlock({ type: 'image', attrs: { attachmentId: a.id, alt: a.originalName, caption: '', width: 100 } }),
      error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }

  protected pickImage(e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.uploadAndInsert(file);
    input.value = '';
  }

  protected insertFinding(e: Event): void {
    const select = e.target as HTMLSelectElement;
    if (select.value) this.insertBlock({ type: 'findingRef', attrs: { findingId: select.value } });
    select.value = '';
  }

  /** Inserts after the selection, so a selected block (e.g. the previous finding) is never replaced. */
  protected insertBlock(content: Record<string, unknown>): void {
    if (!this.editor) return;
    this.editor.chain().focus().insertContentAt(this.editor.state.selection.to, content).run();
  }

  private link(): void {
    const previous = this.editor?.getAttributes('link')['href'] as string | undefined;
    const url = prompt('URL (https://, http://, mailto:)', previous ?? 'https://');
    if (url === null) return;
    if (url === '') { this.cmd().unsetLink().run(); return; }
    if (!/^(https?:\/\/|mailto:)/i.test(url)) { this.toasts.show('Only http(s) and mailto links are allowed', 'error'); return; }
    this.cmd().extendMarkRange('link').setLink({ href: url }).run();
  }

  protected markDirty(): void {
    if (this.state() === 'conflict') return;
    this.state.set('dirty');
    clearTimeout(this.timer);
    this.timer = setTimeout(() => void this.save(), 1500);
  }

  private async save(): Promise<void> {
    if (!this.editor || this.state() === 'conflict') return;
    this.state.set('saving');
    try {
      const r = await firstValueFrom(this.api.saveReport(this.session.ws(), this.id(), { title: this.title || 'Untitled',
        document: JSON.stringify(this.editor.getJSON()), revision: this.revision, templateId: this.templateId,
        classification: this.classification }));
      this.revision = r.revision;
      if (this.state() === 'saving') this.state.set('saved');
    } catch (err) {
      if (err instanceof HttpErrorResponse && err.status === 409) this.state.set('conflict');
      else { this.state.set('error'); this.toasts.error(err, this.i18n.t('common.error')); }
    }
  }

  protected async render(finalized: boolean): Promise<void> {
    clearTimeout(this.timer);
    if (this.state() === 'dirty' || this.state() === 'error') await this.save();
    if (this.state() === 'conflict') return;
    this.rendering.set(true);
    this.api.render(this.session.ws(), this.id(), finalized).subscribe({
      next: (v) => { this.rendering.set(false); this.loadVersions(); void this.router.navigate(['/reports', this.id(), 'versions', v.number]); },
      error: (err) => {
        this.rendering.set(false);
        this.toasts.error(err, this.i18n.t(err instanceof HttpErrorResponse && err.status === 409 ? 'report.rendererDown' : 'common.error'));
      },
    });
  }

  private loadVersions(): void {
    this.api.versions(this.session.ws(), this.id()).subscribe((v) => this.versions.set(v));
  }

  ngOnDestroy(): void {
    if (this.state() === 'dirty') void this.save();
    clearTimeout(this.timer);
    window.removeEventListener('beforeunload', this.beforeUnload);
    this.editor?.destroy();
  }
}
