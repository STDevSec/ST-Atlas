import { ChangeDetectionStrategy, Component, ElementRef, Renderer2, effect, inject, input } from '@angular/core';
import { ICONS } from './icons';

/**
 * Lucide icon rendered with DOM APIs from the package's trusted icon data (no innerHTML).
 * Usage: <app-icon name="FileText" />
 */
@Component({
  selector: 'app-icon',
  template: '',
  host: { class: 'inline-flex shrink-0', 'aria-hidden': 'true' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IconComponent {
  readonly name = input.required<string>();
  readonly size = input(16);
  private readonly el = inject(ElementRef<HTMLElement>);
  private readonly r = inject(Renderer2);

  constructor() {
    effect(() => {
      const host = this.el.nativeElement;
      host.replaceChildren();
      const node = ICONS[this.name()];
      if (!node) return;
      const ns = 'http://www.w3.org/2000/svg';
      const svg = this.r.createElement('svg', ns);
      const attrs: Record<string, string> = { width: String(this.size()), height: String(this.size()), viewBox: '0 0 24 24',
        fill: 'none', stroke: 'currentColor', 'stroke-width': '1.75', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' };
      Object.entries(attrs).forEach(([k, v]) => this.r.setAttribute(svg, k, v));
      for (const [tag, a] of node) {
        const child = this.r.createElement(tag, ns);
        Object.entries(a).forEach(([k, v]) => this.r.setAttribute(child, k, String(v)));
        this.r.appendChild(svg, child);
      }
      this.r.appendChild(host, svg);
    });
  }
}
