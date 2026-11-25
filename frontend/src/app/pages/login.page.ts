import { Component, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api } from '../core/api/api.service';
import { Problem } from '../core/api/models';
import { TPipe } from '../core/i18n/t.pipe';
import { I18n } from '../core/i18n/i18n.service';
import { Session } from '../core/session.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, TPipe],
  template: `
  <div class="min-h-screen flex items-center justify-center p-4">
    <form class="card w-full max-w-sm p-7" (submit)="submit($event)">
      <h1 class="text-lg font-semibold">ST Atlas</h1>
      <p class="muted text-sm mb-6">{{ org() || ('app.tagline' | t) }}</p>
      @if (!mfa()) {
        <label class="label" for="u">{{ 'auth.username' | t }}</label>
        <input id="u" class="input mb-3" name="username" [(ngModel)]="username" autocomplete="username" required autofocus />
        <label class="label" for="p">{{ 'auth.password' | t }}</label>
        <input id="p" class="input mb-4" type="password" name="password" [(ngModel)]="password" autocomplete="current-password" required />
      } @else {
        <p class="text-sm mb-3">{{ 'auth.mfaPrompt' | t }}</p>
        @if (!useRecovery()) {
          <label class="label" for="c">{{ 'auth.totp' | t }}</label>
          <input id="c" class="input mb-3 tracking-widest" name="totp" [(ngModel)]="totp" inputmode="numeric"
                 autocomplete="one-time-code" maxlength="6" required autofocus />
        } @else {
          <label class="label" for="r">{{ 'auth.recoveryCode' | t }}</label>
          <input id="r" class="input mb-3" name="recovery" [(ngModel)]="recovery" required />
        }
        <button type="button" class="text-sm text-[var(--color-accent-600)] mb-4" (click)="useRecovery.set(!useRecovery())">
          {{ 'auth.recovery' | t }}
        </button>
      }
      @if (error()) { <p class="text-sm text-red-600 mb-3" role="alert">{{ error() }}</p> }
      <button class="btn btn-primary w-full justify-center" [disabled]="busy()">{{ 'auth.signIn' | t }}</button>
    </form>
  </div>`,
})
export class LoginPage {
  readonly returnUrl = input<string>();
  private readonly api = inject(Api);
  private readonly session = inject(Session);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18n);
  protected username = '';
  protected password = '';
  protected totp = '';
  protected recovery = '';
  protected readonly mfa = signal(false);
  protected readonly useRecovery = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly org = signal('');

  constructor() {
    this.api.setupStatus().subscribe({ next: (s) => {
      if (s.pending) void this.router.navigateByUrl('/setup');
      this.org.set(s.organizationName);
    } });
  }

  async submit(e: Event): Promise<void> {
    e.preventDefault();
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.login({ username: this.username, password: this.password,
        totp: this.mfa() && !this.useRecovery() ? this.totp : undefined,
        recoveryCode: this.mfa() && this.useRecovery() ? this.recovery : undefined }));
      await this.session.load();
      const target = this.returnUrl();
      await this.router.navigateByUrl(target && target.startsWith('/') && !target.startsWith('//') ? target : '/dashboard');
    } catch (err) {
      if (err instanceof HttpErrorResponse && err.status === 401 && (err.error as Problem)?.mfaRequired) {
        this.mfa.set(true);
      } else if (err instanceof HttpErrorResponse && err.status === 429) {
        this.error.set(this.i18n.t('auth.tooMany'));
      } else {
        this.error.set(this.i18n.t('auth.invalid'));
      }
    } finally {
      this.busy.set(false);
    }
  }
}
