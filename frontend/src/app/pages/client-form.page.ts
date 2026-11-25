import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api/api.service';
import { Contact } from '../core/api/models';
import { I18n } from '../core/i18n/i18n.service';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { Toasts } from '../core/toast.service';
import { IconComponent } from '../shared/icon.component';
import { PageHeader } from '../shared/ui';

@Component({
  selector: 'app-client-form',
  imports: [FormsModule, RouterLink, TPipe, IconComponent, PageHeader],
  template: `
  <app-page-header [title]="id() ? m.name : ('client.new' | t)">
    <a crumbs routerLink="/clients" class="faint text-sm inline-flex items-center gap-1 mb-1"><app-icon name="ArrowLeft" [size]="14" />{{ 'nav.clients' | t }}</a>
    @if (id() && session.canWrite()) { <button class="btn btn-danger" (click)="remove()"><app-icon name="Trash2" />{{ 'common.delete' | t }}</button> }
  </app-page-header>
  <form class="grid lg:grid-cols-3 gap-4" (submit)="save($event)" #f="ngForm">
    <div class="card p-5 lg:col-span-2 grid md:grid-cols-2 gap-4">
      <div><label class="label" for="n">{{ 'common.name' | t }}</label><input id="n" class="input" name="name" [(ngModel)]="m.name" required maxlength="200" /></div>
      <div><label class="label" for="ln">{{ 'client.legalName' | t }}</label><input id="ln" class="input" name="legal" [(ngModel)]="m.legalName" maxlength="300" /></div>
      <div class="md:col-span-2"><label class="label" for="w">{{ 'client.website' | t }}</label>
        <input id="w" class="input" type="url" name="web" [(ngModel)]="m.website" placeholder="https://" /></div>
      <div class="md:col-span-2"><label class="label" for="no">{{ 'client.notes' | t }}</label>
        <textarea id="no" class="input min-h-24" name="notes" [(ngModel)]="m.notes"></textarea></div>
      <div class="md:col-span-2">
        <div class="flex justify-between items-center mb-2"><h2 class="font-medium">{{ 'client.contacts' | t }}</h2>
          <button type="button" class="btn btn-sm" (click)="contacts.push({ name: '', email: '', phone: '', kind: 'TECHNICAL' })"><app-icon name="Plus" />{{ 'client.addContact' | t }}</button></div>
        @for (c of contacts; track $index; let i = $index) {
          <div class="grid grid-cols-12 gap-2 mb-2">
            <input class="input col-span-3" [name]="'cn' + i" [(ngModel)]="c.name" [placeholder]="'common.name' | t" required [attr.aria-label]="'common.name' | t" />
            <input class="input col-span-3" type="email" [name]="'ce' + i" [(ngModel)]="c.email" [placeholder]="'client.email' | t" [attr.aria-label]="'client.email' | t" />
            <input class="input col-span-2" [name]="'cp' + i" [(ngModel)]="c.phone" [placeholder]="'client.phone' | t" [attr.aria-label]="'client.phone' | t" />
            <select class="input col-span-3" [name]="'ck' + i" [(ngModel)]="c.kind" [attr.aria-label]="'client.kind' | t">
              @for (k of kinds; track k) { <option [value]="k">{{ 'client.kind.' + k | t }}</option> }</select>
            <button type="button" class="btn btn-ghost col-span-1" (click)="contacts.splice(i, 1)" [attr.aria-label]="'common.remove' | t"><app-icon name="X" /></button>
          </div>
        }
      </div>
    </div>
    <div class="card p-5 h-fit space-y-3">
      <h2 class="font-medium">{{ 'client.logo' | t }}</h2>
      @if (m.logoAttachmentId) { <img [src]="api.attachmentUrl(session.ws(), m.logoAttachmentId)" alt="" class="max-h-24 object-contain" /> }
      <label class="btn btn-sm cursor-pointer"><app-icon name="Upload" />{{ 'client.uploadLogo' | t }}
        <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" class="sr-only" (change)="logo($event)" /></label>
      @if (session.canWrite()) { <button class="btn btn-primary w-full justify-center" [disabled]="busy() || !f.valid">{{ 'common.save' | t }}</button> }
    </div>
  </form>`,
})
export class ClientFormPage {
  readonly id = input<string>();
  protected readonly api = inject(Api);
  protected readonly session = inject(Session);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);
  private readonly i18n = inject(I18n);
  protected readonly busy = signal(false);
  protected readonly kinds = ['TECHNICAL', 'ADMINISTRATIVE', 'OTHER'];
  protected m: { name: string; legalName: string | null; website: string | null; notes: string | null; logoAttachmentId: string | null } =
    { name: '', legalName: '', website: '', notes: '', logoAttachmentId: null };
  protected contacts: Contact[] = [];

  constructor() {
    queueMicrotask(() => {
      if (this.id()) this.api.client(this.session.ws(), this.id()!).subscribe((c) => {
        this.m = { name: c.name, legalName: c.legalName, website: c.website, notes: c.notes, logoAttachmentId: c.logoAttachmentId };
        this.contacts = c.contacts.map((x) => ({ ...x }));
      });
    });
  }

  logo(e: Event): void {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.api.upload(this.session.ws(), file, file.name).subscribe({
      next: (a) => (this.m = { ...this.m, logoAttachmentId: a.id }), error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }

  save(e: Event): void {
    e.preventDefault();
    this.busy.set(true);
    const body = { ...this.m, contacts: this.contacts.map((c) => ({ ...c, email: c.email || null, phone: c.phone || null })) };
    this.api.saveClient(this.session.ws(), body, this.id()).subscribe({
      next: () => { this.toasts.ok(this.i18n.t('common.saved')); void this.router.navigateByUrl('/clients'); },
      error: (err) => { this.busy.set(false); this.toasts.error(err, this.i18n.t('common.error')); },
    });
  }

  remove(): void {
    if (!confirm(this.i18n.t('common.confirmDelete'))) return;
    this.api.deleteClient(this.session.ws(), this.id()!).subscribe({
      next: () => void this.router.navigateByUrl('/clients'), error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }
}
