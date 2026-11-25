import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18n } from './i18n.service';

/** `{{ 'nav.dashboard' | t }}` — impure so it follows language changes. */
@Pipe({ name: 't', pure: false })
export class TPipe implements PipeTransform {
  private readonly i18n = inject(I18n);
  transform(key: string, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }
}
