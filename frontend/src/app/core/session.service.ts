import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api } from './api/api.service';
import { Me, WorkspaceView } from './api/models';
import { I18n } from './i18n/i18n.service';

const WS_KEY = 'atlas.workspace'; // only a non-sensitive UI preference is kept in localStorage

/** Who is signed in and which workspace is active. The session itself is an HttpOnly cookie. */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18n);

  readonly me = signal<Me | null>(null);
  readonly workspaceId = signal<string | null>(null);
  readonly workspace = computed<WorkspaceView | null>(() =>
    this.me()?.workspaces.find((w) => w.id === this.workspaceId()) ?? null);
  readonly canWrite = computed(() => ['MANAGER', 'ANALYST', 'REVIEWER'].includes(this.workspace()?.role ?? ''));
  readonly canReview = computed(() => ['MANAGER', 'REVIEWER'].includes(this.workspace()?.role ?? ''));
  readonly canManage = computed(() => this.workspace()?.role === 'MANAGER');

  /** Loads the current user; resolves false when not signed in. */
  async load(): Promise<boolean> {
    try {
      const me = await firstValueFrom(this.api.me());
      this.me.set(me);
      this.i18n.setLocale(me.user.locale);
      const saved = localStorage.getItem(WS_KEY);
      const ws = me.workspaces.find((w) => w.id === saved) ?? me.workspaces[0] ?? null;
      this.workspaceId.set(ws?.id ?? null);
      return true;
    } catch {
      this.me.set(null);
      return false;
    }
  }

  selectWorkspace(id: string): void {
    this.workspaceId.set(id);
    localStorage.setItem(WS_KEY, id);
    void this.router.navigateByUrl('/dashboard');
  }

  /** Workspace id for API calls; components only render once a workspace is active. */
  ws(): string {
    const id = this.workspaceId();
    if (!id) throw new Error('No active workspace');
    return id;
  }

  async logout(): Promise<void> {
    try { await firstValueFrom(this.api.logout()); } catch { /* already gone */ }
    this.me.set(null);
    this.workspaceId.set(null);
    await this.router.navigateByUrl('/login');
  }
}
