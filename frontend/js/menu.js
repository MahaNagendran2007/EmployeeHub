// Small accessible action menu. Rendered with position:fixed on <body> so scrolling tables never clip it.
import { icon } from './icons.js';

let open = null;   // { menu, trigger }

export function closeMenu(returnFocus = false) {
  if (!open) return;
  open.menu.remove();
  open.trigger.setAttribute('aria-expanded', 'false');
  if (returnFocus) open.trigger.focus();
  open = null;
}

/** items: [{ label, icon, danger, onSelect }] */
export function openMenu(trigger, items) {
  if (open && open.trigger === trigger) { closeMenu(true); return; }
  closeMenu();
  const menu = document.createElement('div');
  menu.className = 'menu';
  menu.setAttribute('role', 'menu');
  items.forEach((item) => {
    if (item.separator) { menu.appendChild(document.createElement('hr')); return; }
    const b = document.createElement('button');
    b.type = 'button';
    b.setAttribute('role', 'menuitem');
    if (item.danger) b.className = 'danger';
    b.innerHTML = `${icon(item.icon)}<span></span>`;
    b.querySelector('span').textContent = item.label;
    b.addEventListener('click', (e) => { e.stopPropagation(); closeMenu(); item.onSelect(); });
    menu.appendChild(b);
  });
  document.body.appendChild(menu);
  const r = trigger.getBoundingClientRect();
  const w = menu.offsetWidth, h = menu.offsetHeight;
  menu.style.left = `${Math.max(8, Math.min(r.right - w, window.innerWidth - w - 8))}px`;
  menu.style.top = `${r.bottom + 6 + h > window.innerHeight ? Math.max(8, r.top - h - 6) : r.bottom + 6}px`;
  trigger.setAttribute('aria-expanded', 'true');
  open = { menu, trigger };
  menu.querySelector('button').focus();
}

document.addEventListener('click', (e) => { if (open && !open.menu.contains(e.target) && !open.trigger.contains(e.target)) closeMenu(); });
window.addEventListener('resize', () => closeMenu());
window.addEventListener('scroll', () => closeMenu(), true);
document.addEventListener('keydown', (e) => {
  if (!open) return;
  if (e.key === 'Escape') { e.preventDefault(); closeMenu(true); return; }
  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault();
    const btns = Array.from(open.menu.querySelectorAll('button'));
    const i = btns.indexOf(document.activeElement);
    btns[(i + (e.key === 'ArrowDown' ? 1 : -1) + btns.length) % btns.length].focus();
  }
  if (e.key === 'Tab') closeMenu();
});
