import { Node, mergeAttributes } from '@tiptap/core';

/**
 * Editor nodes that must match the backend allow-list (DocumentValidator / ReportRenderer).
 * Images are stored by attachment id only; the URL is derived at render time (ADR 0004).
 */
export function atlasImage(urlFor: (attachmentId: string) => string) {
  return Node.create({
    name: 'image',
    group: 'block',
    atom: true,
    draggable: true,
    addAttributes() {
      return {
        attachmentId: { default: null },
        alt: { default: '' },
        caption: { default: '' },
        width: { default: 100 },
      };
    },
    parseHTML() { return [{ tag: 'figure[data-attachment-id]' }]; },
    renderHTML({ HTMLAttributes }) {
      const { attachmentId, alt, caption, width } = HTMLAttributes as Record<string, string>;
      return ['figure', { 'data-attachment-id': attachmentId, class: 'atlas-figure' },
        ['img', { src: urlFor(attachmentId), alt: alt ?? '', style: `width:${width ?? 100}%` }],
        ['figcaption', {}, caption ?? '']];
    },
    addNodeView() {
      return ({ node, getPos, editor }) => {
        const fig = document.createElement('figure');
        fig.className = 'atlas-figure my-3 text-center';
        const img = document.createElement('img');
        img.src = urlFor(node.attrs['attachmentId']);
        img.alt = node.attrs['alt'] ?? '';
        img.style.width = `${node.attrs['width'] ?? 100}%`;
        img.className = 'inline-block border rounded';
        img.style.borderColor = 'var(--border)';
        const controls = document.createElement('div');
        controls.className = 'flex items-center justify-center gap-2 mt-1';
        const caption = document.createElement('input');
        caption.className = 'input text-center text-sm italic max-w-md';
        caption.placeholder = 'Caption';
        caption.value = node.attrs['caption'] ?? '';
        const width = document.createElement('select');
        width.className = 'input w-24 text-sm';
        width.setAttribute('aria-label', 'Width');
        for (const w of [25, 50, 75, 100]) {
          const o = document.createElement('option');
          o.value = String(w); o.textContent = `${w}%`;
          o.selected = Number(node.attrs['width'] ?? 100) === w;
          width.appendChild(o);
        }
        const update = (attrs: Record<string, unknown>) => {
          const pos = typeof getPos === 'function' ? getPos() : null;
          if (pos == null) return;
          editor.chain().command(({ tr }) => { tr.setNodeMarkup(pos, undefined, { ...node.attrs, ...attrs }); return true; }).run();
        };
        caption.addEventListener('change', () => update({ caption: caption.value }));
        width.addEventListener('change', () => update({ width: Number(width.value) }));
        controls.append(caption, width);
        fig.append(img, controls);
        return { dom: fig, stopEvent: (e) => e.target === caption || e.target === width, ignoreMutation: () => true };
      };
    },
  });
}

/** Explicit page break; rendered as a CSS break in the PDF. */
export const PageBreak = Node.create({
  name: 'pageBreak',
  group: 'block',
  atom: true,
  parseHTML() { return [{ tag: 'div[data-page-break]' }]; },
  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, { 'data-page-break': '', class: 'atlas-page-break' }), '— page break —'];
  },
});

/** Full finding block, resolved from live data at render time and frozen in each version. */
export function findingRef(label: (id: string) => string) {
  return Node.create({
    name: 'findingRef',
    group: 'block',
    atom: true,
    draggable: true,
    addAttributes() { return { findingId: { default: null } }; },
    parseHTML() { return [{ tag: 'div[data-finding-id]' }]; },
    renderHTML({ HTMLAttributes }) {
      const id = (HTMLAttributes as Record<string, string>)['findingId'];
      return ['div', { 'data-finding-id': id, class: 'atlas-block' }, label(id)];
    },
  });
}

/** Summary table of all findings of the assessment. */
export const FindingsTable = Node.create({
  name: 'findingsTable',
  group: 'block',
  atom: true,
  parseHTML() { return [{ tag: 'div[data-findings-table]' }]; },
  renderHTML() { return ['div', { 'data-findings-table': '', class: 'atlas-block' }, '▦ Findings summary table']; },
});
