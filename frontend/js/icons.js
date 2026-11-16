// Inline SVG icons (Lucide-style). The <symbol> sprite lives at the top of index.html,
// so icons work offline and never depend on a CDN.
export const icon = (name, cls = '') =>
  `<svg class="i ${cls}" aria-hidden="true" focusable="false"><use href="#i-${name}"/></svg>`;
