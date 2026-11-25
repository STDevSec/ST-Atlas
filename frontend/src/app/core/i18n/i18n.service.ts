import { Injectable, computed, signal } from '@angular/core';
import { en, MessageKey } from './en';
import { es } from './es';

export type Locale = 'en' | 'es';
const DICTS: Record<Locale, Record<MessageKey, string>> = { en, es };

/** Runtime i18n (ADR 0007). The language comes from the user's profile; `en` before login. */
@Injectable({ providedIn: 'root' })
export class I18n {
  readonly locale = signal<Locale>((navigator.language || 'en').toLowerCase().startsWith('es') ? 'es' : 'en');
  private readonly dict = computed(() => DICTS[this.locale()]);

  setLocale(locale: string | undefined | null): void {
    if (locale === 'en' || locale === 'es') {
      this.locale.set(locale);
      document.documentElement.lang = locale;
    }
  }

  /** Translate a key; `{name}` placeholders are replaced from params. Unknown keys render as-is. */
  t(key: MessageKey | string, params?: Record<string, string | number>): string {
    let text = (this.dict() as Record<string, string>)[key] ?? key;
    if (params) {
      for (const [k, v] of Object.entries(params)) {
        text = text.replaceAll(`{${k}}`, String(v));
      }
    }
    return text;
  }
}
