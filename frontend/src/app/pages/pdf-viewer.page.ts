import { AfterViewInit, Component, ElementRef, OnDestroy, inject, input, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { ReportVersion } from '../core/api/models';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { IconComponent } from '../shared/icon.component';

/**
 * In-app PDF viewer (PDF.js). The PDF is fetched with the session cookie and handed to PDF.js
 * as bytes; nothing is embedded from other origins. Search uses PDF.js's own find controller.
 */
@Component({
  selector: 'app-pdf-viewer',
  imports: [RouterLink, FormsModule, DatePipe, TPipe, IconComponent],
  template: `
  <div class="flex flex-wrap items-center gap-2 mb-3">
    <a [routerLink]="['/reports', id()]" class="faint text-sm inline-flex items-center gap-1"><app-icon name="ArrowLeft" [size]="14" />{{ 'report.studio' | t }}</a>
    <h1 class="font-semibold">v{{ number() }}</h1>
    @if (version(); as v) {
      <span class="faint text-sm">{{ v.createdAt | date: 'medium' }} · {{ v.pageCount ?? '?' }} pp.</span>
      @if (v.finalized) { <span class="chip"><app-icon name="Lock" [size]="12" />{{ 'report.finalized' | t }}</span> }
    }
    <span class="flex-1"></span>
    <button class="btn btn-sm" (click)="go(page() - 1)" [disabled]="page() <= 1" [attr.aria-label]="'common.previous' | t"><app-icon name="ChevronLeft" /></button>
    <span class="text-sm tabular-nums">{{ 'viewer.page' | t }} {{ page() }} {{ 'viewer.of' | t }} {{ pages() }}</span>
    <button class="btn btn-sm" (click)="go(page() + 1)" [disabled]="page() >= pages()" [attr.aria-label]="'common.next' | t"><app-icon name="ChevronRight" /></button>
    <button class="btn btn-sm" (click)="zoom(-0.2)" [attr.aria-label]="'viewer.zoomOut' | t"><app-icon name="ZoomOut" /></button>
    <span class="text-sm w-12 text-center tabular-nums">{{ (scale() * 100).toFixed(0) }}%</span>
    <button class="btn btn-sm" (click)="zoom(0.2)" [attr.aria-label]="'viewer.zoomIn' | t"><app-icon name="ZoomIn" /></button>
    <button class="btn btn-sm" (click)="fit()">{{ 'viewer.fit' | t }}</button>
    <form (submit)="find($event)" class="flex"><input class="input py-1 w-44" type="search" name="q" [(ngModel)]="query" [placeholder]="'viewer.find' | t" [attr.aria-label]="'viewer.find' | t" /></form>
    @if (version(); as v) {
      <a class="btn btn-sm" [href]="api.attachmentUrl(session.ws(), v.pdfAttachmentId, true)"><app-icon name="Download" />{{ 'common.download' | t }}</a>
    }
    <button class="btn btn-sm" (click)="print()" [disabled]="!blobUrl"><app-icon name="Printer" />{{ 'common.print' | t }}</button>
  </div>
  <div class="relative rounded-lg border hairline" style="height: calc(100vh - 190px); background: #525659">
    <div #container class="absolute inset-0 overflow-auto"><div class="pdfViewer"></div></div>
    @if (error()) { <p class="absolute inset-0 flex items-center justify-center text-white">{{ error() }}</p> }
  </div>`,
})
export class PdfViewerPage implements AfterViewInit, OnDestroy {
  readonly id = input.required<string>();
  readonly number = input.required<string>();
  protected readonly api = inject(Api);
  protected readonly session = inject(Session);
  private readonly container = viewChild<ElementRef<HTMLDivElement>>('container');
  protected readonly version = signal<ReportVersion | null>(null);
  protected readonly page = signal(1);
  protected readonly pages = signal(0);
  protected readonly scale = signal(1);
  protected readonly error = signal('');
  protected query = '';
  protected blobUrl: string | null = null;
  // PDF.js objects (typed loosely: the viewer API is untyped in pdf_viewer.mjs)
  private viewer: any;
  private eventBus: any;
  private doc: any;

  async ngAfterViewInit(): Promise<void> {
    const versions = await new Promise<ReportVersion[]>((res) => this.api.versions(this.session.ws(), this.id()).subscribe(res));
    const v = versions.find((x) => x.number === Number(this.number()));
    if (!v) { this.error.set('Version not found'); return; }
    this.version.set(v);
    const pdfjs = await import('pdfjs-dist');
    // pdf_viewer.mjs reads the core library from this global at import time.
    (globalThis as { pdfjsLib?: unknown }).pdfjsLib = pdfjs;
    const viewerMod = await import('pdfjs-dist/web/pdf_viewer.mjs');
    pdfjs.GlobalWorkerOptions.workerSrc = '/pdfjs/pdf.worker.min.mjs';
    const res = await fetch(this.api.attachmentUrl(this.session.ws(), v.pdfAttachmentId), { credentials: 'same-origin' });
    if (!res.ok) { this.error.set(`HTTP ${res.status}`); return; }
    const bytes = await res.arrayBuffer();
    this.blobUrl = URL.createObjectURL(new Blob([bytes], { type: 'application/pdf' }));
    this.eventBus = new viewerMod.EventBus();
    const linkService = new viewerMod.PDFLinkService({ eventBus: this.eventBus });
    const findController = new viewerMod.PDFFindController({ eventBus: this.eventBus, linkService });
    this.viewer = new viewerMod.PDFViewer({ container: this.container()!.nativeElement, eventBus: this.eventBus, linkService,
      findController, removePageBorders: false });
    linkService.setViewer(this.viewer);
    this.eventBus.on('pagechanging', (e: { pageNumber: number }) => this.page.set(e.pageNumber));
    this.eventBus.on('scalechanging', (e: { scale: number }) => this.scale.set(e.scale));
    this.eventBus.on('pagesinit', () => { this.viewer.currentScaleValue = 'page-width'; });
    this.doc = await pdfjs.getDocument({ data: bytes }).promise;
    this.pages.set(this.doc.numPages);
    this.viewer.setDocument(this.doc);
    linkService.setDocument(this.doc);
  }

  go(n: number): void { if (this.viewer && n >= 1 && n <= this.pages()) this.viewer.currentPageNumber = n; }
  zoom(delta: number): void { if (this.viewer) this.viewer.currentScale = Math.min(4, Math.max(0.3, this.viewer.currentScale + delta)); }
  fit(): void { if (this.viewer) this.viewer.currentScaleValue = 'page-width'; }

  find(e: Event): void {
    e.preventDefault();
    this.eventBus?.dispatch('find', { source: this, type: '', query: this.query, caseSensitive: false, entireWord: false,
      highlightAll: true, findPrevious: false, matchDiacritics: false });
  }

  print(): void {
    if (!this.blobUrl) return;
    const frame = document.createElement('iframe');
    frame.style.display = 'none';
    frame.src = this.blobUrl;
    frame.onload = () => { frame.contentWindow?.focus(); frame.contentWindow?.print(); setTimeout(() => frame.remove(), 60000); };
    document.body.appendChild(frame);
  }

  ngOnDestroy(): void {
    this.doc?.destroy();
    if (this.blobUrl) URL.revokeObjectURL(this.blobUrl);
  }
}
