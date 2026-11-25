import { Component, computed, input } from '@angular/core';
import { TPipe } from '../core/i18n/t.pipe';

const SEV_CLASS: Record<string, string> = {
  CRITICAL: 'bg-[var(--color-sev-critical)]', HIGH: 'bg-[var(--color-sev-high)]', MEDIUM: 'bg-[var(--color-sev-medium)]',
  LOW: 'bg-[var(--color-sev-low)]', INFORMATIONAL: 'bg-[var(--color-sev-info)]', NOT_APPLICABLE: 'bg-gray-400',
};

@Component({
  selector: 'app-severity',
  imports: [TPipe],
  template: `<span class="badge {{ cls() }}">{{ 'sev.' + value() | t }}</span>`,
})
export class SeverityBadge {
  readonly value = input.required<string>();
  protected readonly cls = computed(() => SEV_CLASS[this.value()] ?? 'bg-gray-400');
}

const STATUS_DOT: Record<string, string> = {
  DRAFT: 'bg-gray-400', PLANNED: 'bg-sky-400', IN_PROGRESS: 'bg-blue-500', TECHNICAL_REVIEW: 'bg-amber-500',
  APPROVED: 'bg-teal-500', DELIVERED: 'bg-indigo-500', RETESTING: 'bg-violet-500', COMPLETED: 'bg-emerald-600', ARCHIVED: 'bg-gray-300',
};

@Component({
  selector: 'app-status',
  imports: [TPipe],
  template: `<span class="chip"><span class="h-2 w-2 rounded-full {{ dot() }}"></span>{{ prefix() + value() | t }}</span>`,
})
export class StatusChip {
  readonly value = input.required<string>();
  readonly prefix = input('status.');
  protected readonly dot = computed(() => STATUS_DOT[this.value()] ?? 'bg-gray-400');
}

@Component({
  selector: 'app-page-header',
  template: `
    <div class="flex flex-wrap items-end justify-between gap-3 mb-5">
      <div class="min-w-0">
        <ng-content select="[crumbs]" />
        <h1 class="text-xl font-semibold truncate">{{ title() }}</h1>
        @if (subtitle()) { <p class="muted mt-0.5">{{ subtitle() }}</p> }
      </div>
      <div class="flex flex-wrap gap-2"><ng-content /></div>
    </div>`,
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
}

@Component({
  selector: 'app-empty',
  template: `<div class="text-center py-12 px-4"><p class="muted">{{ text() }}</p><div class="mt-4"><ng-content /></div></div>`,
})
export class EmptyState {
  readonly text = input.required<string>();
}

@Component({
  selector: 'app-skeleton',
  template: `<div class="space-y-3 p-4" aria-busy="true">@for (r of rowsArr(); track $index) {<div class="skeleton h-5"></div>}</div>`,
})
export class Skeleton {
  readonly rows = input(4);
  protected readonly rowsArr = computed(() => Array.from({ length: this.rows() }));
}
