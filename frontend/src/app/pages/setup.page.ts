import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api } from '../core/api/api.service';
import { I18n } from '../core/i18n/i18n.service';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { Toasts } from '../core/toast.service';

@Component({
  selector: 'app-setup',
  imports: [FormsModule, TPipe],
  template: `
  <div class="min-h-screen flex items-center justify-center p-4">
    <form class="card w-full max-w-lg p-7" (submit)="submit($event)" #f="ngForm">
      <h1 class="text-lg font-semibold">{{ 'setup.title' | t }}</h1>
      <p class="muted text-sm mt-1">{{ 'setup.intro' | t }}</p>
      <p class="text-xs mt-2 font-mono p-2 rounded" style="background:var(--surface-muted)">{{ 'setup.tokenHelp' | t }}</p>
      <div class="grid grid-cols-2 gap-3 mt-5">
        <div class="col-span-2"><label class="label" for="token">{{ 'setup.token' | t }}</label>
          <input id="token" class="input font-mono" name="token" [(ngModel)]="m.token" required autocomplete="off" /></div>
        <div class="col-span-2"><label class="label" for="org">{{ 'setup.organization' | t }}</label>
          <input id="org" class="input" name="org" [(ngModel)]="m.organizationName" required maxlength="200" /></div>
        <div><label class="label" for="dn">{{ 'setup.displayName' | t }}</label>
          <input id="dn" class="input" name="dn" [(ngModel)]="m.displayName" required maxlength="120" /></div>
        <div><label class="label" for="un">{{ 'auth.username' | t }}</label>
          <input id="un" class="input" name="un" [(ngModel)]="m.username" required pattern="[A-Za-z0-9._-]{3,64}" autocomplete="username" /></div>
        <div><label class="label" for="pw">{{ 'auth.password' | t }}</label>
          <input id="pw" class="input" type="password" name="pw" [(ngModel)]="m.password" required minlength="12" autocomplete="new-password" /></div>
        <div><label class="label" for="pw2">{{ 'setup.confirmPassword' | t }}</label>
          <input id="pw2" class="input" type="password" name="pw2" [(ngModel)]="confirm" required autocomplete="new-password"
                 [attr.aria-invalid]="confirm && confirm !== m.password" /></div>
        <p class="col-span-2 faint text-xs -mt-1">{{ 'setup.passwordHelp' | t }}</p>
        <div><label class="label" for="lang">{{ 'setup.language' | t }}</label>
          <select id="lang" class="input" name="lang" [(ngModel)]="m.locale" (ngModelChange)="i18n.setLocale($event)">
            <option value="en">English</option><option value="es">Español</option></select></div>
        <div><label class="label" for="tz">{{ 'setup.timeZone' | t }}</label>
          <select id="tz" class="input" name="tz" [(ngModel)]="m.timeZone">
            @for (z of zones; track z) { <option [value]="z">{{ z }}</option> }</select></div>
        <div class="col-span-2"><label class="label" for="ws">{{ 'setup.workspace' | t }}</label>
          <input id="ws" class="input" name="ws" [(ngModel)]="m.workspaceName" required maxlength="120" /></div>
      </div>
      @if (error()) { <p class="text-sm text-red-600 mt-3" role="alert">{{ error() }}</p> }
      <button class="btn btn-primary w-full justify-center mt-5" [disabled]="busy() || !f.valid || confirm !== m.password">
        {{ 'setup.complete' | t }}</button>
    </form>
  </div>`,
})
export class SetupPage {
  protected readonly i18n = inject(I18n);
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly session = inject(Session);
  private readonly toasts = inject(Toasts);
  protected readonly zones = Intl.supportedValuesOf('timeZone');
  protected m = { token: '', organizationName: '', displayName: '', username: '', password: '', locale: this.i18n.locale(),
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone, workspaceName: 'Main' };
  protected confirm = '';
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  async submit(e: Event): Promise<void> {
    e.preventDefault();
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.completeSetup({ ...this.m, token: this.m.token.trim() }));
      await firstValueFrom(this.api.login({ username: this.m.username, password: this.m.password }));
      await this.session.load();
      await this.router.navigateByUrl('/dashboard');
    } catch (err) {
      this.toasts.error(err, this.i18n.t('common.error'));
      this.error.set(this.i18n.t('common.error'));
    } finally {
      this.busy.set(false);
    }
  }
}
