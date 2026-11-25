import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Api } from '../core/api/api.service';
import { TimeEntry } from '../core/api/models';
import { I18n } from '../core/i18n/i18n.service';
import { TPipe } from '../core/i18n/t.pipe';
import { Session } from '../core/session.service';
import { ThemeService } from '../core/theme.service';
import { Toasts } from '../core/toast.service';
import { IconComponent } from '../shared/icon.component';

interface NavItem { path: string; icon: string; label: string; manager?: boolean; }

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, IconComponent, TPipe],
  template: `
  <a href="#main" class="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:p-2 focus:bg-white">Skip to content</a>
  <div class="flex h-screen overflow-hidden">
    <aside class="w-60 shrink-0 flex flex-col border-r hairline" style="background:var(--surface)">
      <div class="px-4 h-14 flex items-center gap-2 border-b hairline">
        <span class="font-semibold tracking-tight text-[15px]">ST Atlas</span>
        <span class="faint text-xs">by ST DevSec</span>
      </div>
      <div class="px-3 py-3 border-b hairline">
        <label class="label" for="ws">{{ 'nav.workspace' | t }}</label>
        <select id="ws" class="input py-1.5" [ngModel]="session.workspaceId()" (ngModelChange)="session.selectWorkspace($event)">
          @for (w of session.me()?.workspaces ?? []; track w.id) { <option [value]="w.id">{{ w.name }}</option> }
        </select>
      </div>
      <nav class="flex-1 overflow-y-auto p-2 space-y-0.5" aria-label="Main">
        @for (item of nav(); track item.path) {
          <a [routerLink]="item.path" routerLinkActive="!bg-[var(--surface-muted)] !text-[var(--text)] font-medium"
             class="flex items-center gap-2.5 px-3 py-1.5 rounded-md muted hover:bg-[var(--surface-muted)]">
            <app-icon [name]="item.icon" />{{ item.label | t }}
          </a>
        }
      </nav>
      <div class="p-3 border-t hairline text-sm">
        <div class="flex items-center justify-between">
          <span class="truncate" [title]="session.me()?.user?.username ?? ''">{{ session.me()?.user?.displayName }}</span>
          <button class="btn btn-ghost btn-sm" (click)="session.logout()" [attr.aria-label]="'auth.signOut' | t">
            <app-icon name="LogOut" />
          </button>
        </div>
      </div>
    </aside>
    <div class="flex-1 flex flex-col min-w-0">
      <header class="h-14 flex items-center gap-3 px-5 border-b hairline" style="background:var(--surface)">
        <form class="flex-1 max-w-xl relative" (submit)="search($event)" role="search">
          <app-icon name="Search" class="absolute left-2.5 top-2.5 faint" />
          <input class="input pl-8" type="search" name="q" [(ngModel)]="query" [placeholder]="'search.placeholder' | t"
                 [attr.aria-label]="'common.search' | t" />
        </form>
        @if (timer(); as t) {
          <a routerLink="/time" class="chip !text-[var(--text)] !border-emerald-500" [title]="t.assessmentTitle">
            <span class="h-2 w-2 rounded-full bg-emerald-500 animate-pulse"></span>{{ t.assessmentCode }} · {{ elapsed() }}
          </a>
        }
        <button class="btn btn-ghost btn-sm" (click)="toggleLang()" aria-label="Language">{{ i18n.locale() === 'en' ? 'ES' : 'EN' }}</button>
        <button class="btn btn-ghost btn-sm" (click)="theme.toggle()" [attr.aria-label]="'settings.theme' | t">
          <app-icon [name]="theme.theme() === 'dark' ? 'Sun' : 'Moon'" />
        </button>
      </header>
      <main id="main" class="flex-1 overflow-y-auto">
        @if (session.workspaceId()) {
          <div class="max-w-7xl mx-auto px-6 py-6"><router-outlet /></div>
        }
      </main>
    </div>
  </div>`,
})
export class ShellComponent implements OnDestroy {
  protected readonly session = inject(Session);
  protected readonly theme = inject(ThemeService);
  protected readonly i18n = inject(I18n);
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);
  protected query = '';
  protected readonly timer = signal<TimeEntry | null>(null);
  private readonly now = signal(Date.now());
  private readonly tick = setInterval(() => this.now.set(Date.now()), 1000);
  private readonly poll = setInterval(() => this.refreshTimer(), 30000);

  protected readonly nav = computed<NavItem[]>(() => {
    const items: NavItem[] = [
      { path: '/dashboard', icon: 'LayoutDashboard', label: 'nav.dashboard' },
      { path: '/assessments', icon: 'ClipboardList', label: 'nav.assessments' },
      { path: '/clients', icon: 'Building2', label: 'nav.clients' },
      { path: '/findings', icon: 'Bug', label: 'nav.findings' },
      { path: '/reports', icon: 'FileText', label: 'nav.reports' },
      { path: '/search', icon: 'Search', label: 'nav.search' },
      { path: '/time', icon: 'Clock', label: 'nav.time' },
      { path: '/templates', icon: 'Palette', label: 'nav.templates' },
      { path: '/audit', icon: 'ScrollText', label: 'nav.audit', manager: true },
      { path: '/settings', icon: 'Settings', label: 'nav.settings' },
    ];
    return items.filter((i) => !i.manager || this.session.canManage());
  });

  protected readonly elapsed = computed(() => {
    const t = this.timer();
    if (!t) return '';
    const s = Math.max(0, Math.floor((this.now() - new Date(t.startedAt).getTime()) / 1000));
    return `${Math.floor(s / 3600)}:${String(Math.floor((s % 3600) / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  });

  constructor() {
    this.refreshTimer();
  }

  refreshTimer(): void {
    const ws = this.session.workspaceId();
    if (!ws) return;
    this.api.timer(ws).subscribe({ next: (t) => this.timer.set(t ?? null), error: () => this.timer.set(null) });
  }

  search(e: Event): void {
    e.preventDefault();
    void this.router.navigate(['/search'], { queryParams: { q: this.query || null } });
  }

  toggleLang(): void {
    const next = this.i18n.locale() === 'en' ? 'es' : 'en';
    this.i18n.setLocale(next);
    this.api.preferences({ locale: next }).subscribe({ error: (e) => this.toasts.error(e, this.i18n.t('common.error')) });
  }

  ngOnDestroy(): void {
    clearInterval(this.tick);
    clearInterval(this.poll);
  }
}
