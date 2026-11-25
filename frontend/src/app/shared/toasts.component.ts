import { Component, inject } from '@angular/core';
import { Toasts } from '../core/toast.service';
import { IconComponent } from './icon.component';

@Component({
  selector: 'app-toasts',
  imports: [IconComponent],
  template: `
    <div class="fixed bottom-4 right-4 z-50 flex flex-col gap-2 w-80" aria-live="polite" role="status">
      @for (t of toasts.items(); track t.id) {
        <div class="card px-3 py-2 shadow-lg flex items-start gap-2 text-sm"
             [class.border-red-400]="t.kind === 'error'" [class.border-emerald-400]="t.kind === 'ok'">
          <app-icon [name]="t.kind === 'error' ? 'AlertTriangle' : t.kind === 'ok' ? 'Check' : 'Info'"
                    [class.text-red-600]="t.kind === 'error'" [class.text-emerald-600]="t.kind === 'ok'" />
          <span class="flex-1">{{ t.text }}</span>
          <button class="faint hover:opacity-70" (click)="toasts.dismiss(t.id)" aria-label="Close"><app-icon name="X" [size]="14" /></button>
        </div>
      }
    </div>`,
})
export class ToastsComponent {
  protected readonly toasts = inject(Toasts);
}
