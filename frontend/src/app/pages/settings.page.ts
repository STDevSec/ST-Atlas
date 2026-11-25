import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api/api.service';
import { MemberView, Role } from '../core/api/models';
import { I18n } from '../core/i18n/i18n.service';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { ThemeService } from '../core/theme.service';
import { Toasts } from '../core/toast.service';
import { PageHeader } from '../shared/ui';
import { IconComponent } from '../shared/icon.component';

const ROLES: Role[] = ['MANAGER', 'ANALYST', 'REVIEWER', 'VIEWER'];

@Component({
  selector: 'app-settings',
  imports: [FormsModule, TPipe, PageHeader, IconComponent],
  template: `
  <app-page-header [title]="'nav.settings' | t" />
  <div class="grid lg:grid-cols-2 gap-4 items-start">
    <!-- Profile -->
    <section class="card p-5 space-y-3">
      <h2 class="font-medium flex items-center gap-2"><app-icon name="User" />{{ 'settings.profile' | t }}</h2>
      <p class="text-sm">{{ session.me()?.user?.displayName }} <span class="faint">({{ session.me()?.user?.username }})</span></p>
      <div class="grid sm:grid-cols-2 gap-3">
        <label class="text-sm">{{ 'settings.language' | t }}
          <select class="input" [ngModel]="locale()" (ngModelChange)="setLocale($event)"><option value="en">English</option><option value="es">Español</option></select></label>
        <label class="text-sm">{{ 'settings.theme' | t }}
          <button class="input flex items-center justify-between" (click)="theme.toggle()" type="button">
            {{ (theme.theme() === 'dark' ? 'settings.dark' : 'settings.light') | t }}<app-icon [name]="theme.theme() === 'dark' ? 'Moon' : 'Sun'" /></button></label>
      </div>
      <label class="text-sm block">{{ 'settings.timeZone' | t }}
        <div class="flex gap-2"><input class="input" [(ngModel)]="timeZone" placeholder="America/Mexico_City" />
          <button class="btn" (click)="saveTz()">{{ 'common.save' | t }}</button></div></label>
    </section>

    <!-- Password -->
    <section class="card p-5 space-y-3">
      <h2 class="font-medium flex items-center gap-2"><app-icon name="KeyRound" />{{ 'settings.password' | t }}</h2>
      <form class="space-y-2" (submit)="savePassword($event)" #pf="ngForm">
        <input class="input" type="password" name="cur" [(ngModel)]="pw.current" [placeholder]="'settings.current' | t" autocomplete="current-password" required />
        <input class="input" type="password" name="new" [(ngModel)]="pw.next" [placeholder]="'settings.new' | t" autocomplete="new-password" required minlength="12" />
        <button class="btn btn-primary" [disabled]="busy() || !pf.valid">{{ 'common.save' | t }}</button>
      </form>
    </section>

    <!-- MFA -->
    <section class="card p-5 space-y-3">
      <h2 class="font-medium flex items-center gap-2"><app-icon name="Shield" />{{ 'settings.mfa' | t }}
        <span class="chip ml-auto">{{ (session.me()?.user?.mfaEnabled ? 'settings.mfaOn' : 'settings.mfaOff') | t }}</span></h2>
      @if (session.me()?.user?.mfaEnabled) {
        <form class="flex gap-2" (submit)="disableMfa($event)">
          <input class="input" type="password" name="p" [(ngModel)]="mfaPassword" [placeholder]="'settings.password' | t" autocomplete="current-password" required />
          <button class="btn btn-danger">{{ 'settings.mfaDisable' | t }}</button>
        </form>
      } @else if (mfaSecret()) {
        <p class="text-sm faint">{{ 'settings.mfaSecret' | t }}</p>
        <code class="block input font-mono text-sm break-all">{{ mfaSecret() }}</code>
        <form class="flex gap-2" (submit)="enableMfa($event)">
          <input class="input" name="c" [(ngModel)]="mfaCode" inputmode="numeric" placeholder="000000" required />
          <button class="btn btn-primary">{{ 'settings.mfaEnable' | t }}</button>
        </form>
        @if (recoveryCodes().length) {
          <div><p class="text-sm font-medium">{{ 'settings.recoveryCodes' | t }}</p>
            <div class="grid grid-cols-2 gap-1 font-mono text-xs mt-1">@for (c of recoveryCodes(); track c) { <span class="input !py-1">{{ c }}</span> }</div></div>
        }
      } @else {
        <button class="btn" (click)="startMfa()"><app-icon name="Shield" />{{ 'settings.mfaSetup' | t }}</button>
      }
    </section>

    <!-- Members (managers) -->
    @if (session.canManage()) {
      <section class="card p-5 space-y-3">
        <h2 class="font-medium flex items-center gap-2"><app-icon name="Users" />{{ 'settings.members' | t }}</h2>
        <div class="space-y-1.5">
          @for (m of members(); track m.userId) {
            <div class="flex items-center gap-2 text-sm py-1 border-b hairline last:border-0">
              <span class="flex-1 truncate">{{ m.displayName }} <span class="faint">({{ m.username }})</span></span>
              <select class="input w-auto py-1" [ngModel]="m.role" (ngModelChange)="setRole(m, $event)" [attr.aria-label]="'settings.role' | t">
                @for (r of roles; track r) { <option [value]="r">{{ title(r) }}</option> }</select>
              <button class="btn btn-ghost btn-sm" (click)="removeMember(m)" [attr.aria-label]="'common.remove' | t"><app-icon name="X" [size]="14" /></button>
            </div>
          }
        </div>
        <form class="flex gap-2" (submit)="addMember($event)">
          <input class="input flex-1" name="u" [(ngModel)]="newMember.username" [placeholder]="'common.name' | t" required />
          <select class="input w-auto" name="r" [(ngModel)]="newMember.role" [attr.aria-label]="'settings.role' | t">@for (r of roles; track r) { <option [value]="r">{{ title(r) }}</option> }</select>
          <button class="btn btn-primary"><app-icon name="Plus" />{{ 'settings.addMember' | t }}</button>
        </form>
        <form class="flex gap-2 pt-2 border-t hairline" (submit)="addWorkspace($event)">
          <input class="input flex-1" name="w" [(ngModel)]="newWorkspace" [placeholder]="'settings.newWorkspace' | t" required />
          <button class="btn"><app-icon name="Plus" />{{ 'settings.newWorkspace' | t }}</button>
        </form>
      </section>
    }

    <!-- Admin: create user -->
    @if (session.me()?.user?.admin) {
      <section class="card p-5 space-y-3">
        <h2 class="font-medium flex items-center gap-2"><app-icon name="User" />{{ 'settings.createUser' | t }}</h2>
        <form class="grid sm:grid-cols-2 gap-2" (submit)="createUser($event)" #uf="ngForm">
          <input class="input" name="un" [(ngModel)]="user.username" [placeholder]="'common.name' | t" required />
          <input class="input" name="dn" [(ngModel)]="user.displayName" [placeholder]="'settings.profile' | t" required />
          <input class="input sm:col-span-2" type="password" name="up" [(ngModel)]="user.password" [placeholder]="'settings.password' | t" autocomplete="new-password" required minlength="12" />
          <button class="btn btn-primary sm:col-span-2 justify-center" [disabled]="busy() || !uf.valid"><app-icon name="Plus" />{{ 'settings.createUser' | t }}</button>
        </form>
      </section>
    }
  </div>`,
})
export class SettingsPage {
  protected readonly session = inject(Session);
  protected readonly theme = inject(ThemeService);
  private readonly api = inject(Api);
  private readonly i18n = inject(I18n);
  private readonly toasts = inject(Toasts);
  protected readonly locale = signal(this.session.me()?.user.locale ?? 'en');
  protected readonly members = signal<MemberView[]>([]);
  protected readonly mfaSecret = signal('');
  protected readonly recoveryCodes = signal<string[]>([]);
  protected readonly busy = signal(false);
  protected readonly roles = ROLES;
  protected timeZone = this.session.me()?.user.timeZone ?? '';
  protected pw = { current: '', next: '' };
  protected mfaPassword = '';
  protected mfaCode = '';
  protected newMember = { username: '', role: 'ANALYST' as Role };
  protected newWorkspace = '';
  protected user = { username: '', displayName: '', password: '' };

  constructor() {
    if (this.session.canManage()) this.api.members(this.session.ws()).subscribe((m) => this.members.set(m));
  }

  title(r: string): string { return r.charAt(0) + r.slice(1).toLowerCase(); }

  private refreshMe(): void { this.api.me().subscribe((me) => this.session.me.set(me)); }

  setLocale(loc: string): void {
    this.locale.set(loc);
    this.api.preferences({ locale: loc }).subscribe({ next: () => { this.i18n.setLocale(loc); this.refreshMe(); }, error: (e) => this.toasts.error(e, this.i18n.t('common.error')) });
  }

  saveTz(): void {
    this.api.preferences({ timeZone: this.timeZone }).subscribe({ next: () => { this.toasts.ok(this.i18n.t('common.saved')); this.refreshMe(); }, error: (e) => this.toasts.error(e, this.i18n.t('common.error')) });
  }

  savePassword(e: Event): void {
    e.preventDefault();
    this.busy.set(true);
    this.api.changePassword(this.pw.current, this.pw.next).subscribe({
      next: () => { this.busy.set(false); this.pw = { current: '', next: '' }; this.toasts.ok(this.i18n.t('common.saved')); },
      error: (err) => { this.busy.set(false); this.toasts.error(err, this.i18n.t('common.error')); },
    });
  }

  startMfa(): void {
    this.api.mfaSetup().subscribe({ next: (r) => this.mfaSecret.set(r.secret), error: (e) => this.toasts.error(e, this.i18n.t('common.error')) });
  }

  enableMfa(e: Event): void {
    e.preventDefault();
    this.api.mfaEnable(this.mfaCode).subscribe({
      next: (r) => { this.recoveryCodes.set(r.recoveryCodes); this.mfaSecret.set(''); this.mfaCode = ''; this.refreshMe(); this.toasts.ok(this.i18n.t('common.saved')); },
      error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }

  disableMfa(e: Event): void {
    e.preventDefault();
    this.api.mfaDisable(this.mfaPassword).subscribe({
      next: () => { this.mfaPassword = ''; this.recoveryCodes.set([]); this.refreshMe(); this.toasts.ok(this.i18n.t('common.saved')); },
      error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }

  addMember(e: Event): void {
    e.preventDefault();
    this.api.addMember(this.session.ws(), this.newMember.username, this.newMember.role).subscribe({
      next: (m) => { this.members.update((l) => [...l, m]); this.newMember.username = ''; },
      error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }

  setRole(m: MemberView, role: Role): void {
    this.api.changeRole(this.session.ws(), m.userId, role).subscribe({ next: () => (m.role = role), error: (e) => this.toasts.error(e, this.i18n.t('common.error')) });
  }

  removeMember(m: MemberView): void {
    if (!confirm(this.i18n.t('common.confirmDelete'))) return;
    this.api.removeMember(this.session.ws(), m.userId).subscribe({
      next: () => this.members.update((l) => l.filter((x) => x.userId !== m.userId)), error: (e) => this.toasts.error(e, this.i18n.t('common.error')),
    });
  }

  addWorkspace(e: Event): void {
    e.preventDefault();
    this.api.createWorkspace(this.newWorkspace).subscribe({
      next: () => { this.newWorkspace = ''; this.toasts.ok(this.i18n.t('common.saved')); this.refreshMe(); },
      error: (err) => this.toasts.error(err, this.i18n.t('common.error')),
    });
  }

  createUser(e: Event): void {
    e.preventDefault();
    this.busy.set(true);
    this.api.createUser({ ...this.user, locale: this.locale() }).subscribe({
      next: () => { this.busy.set(false); this.user = { username: '', displayName: '', password: '' }; this.toasts.ok(this.i18n.t('common.saved')); },
      error: (err) => { this.busy.set(false); this.toasts.error(err, this.i18n.t('common.error')); },
    });
  }
}
