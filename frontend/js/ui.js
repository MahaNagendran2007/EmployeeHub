// Small shared helpers: escaping, formatting, avatars, badges, toasts, modals.
import { icon } from './icons.js';
export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
/** Escape untrusted text before putting it inside an HTML template string. */
export const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => ESCAPES[c]);

export function debounce(fn, ms) {
  let timer;
  return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), ms); };
}

export const STATUS_LABEL = { ACTIVE: 'Active', ON_LEAVE: 'On leave', INACTIVE: 'Inactive', PENDING: 'Pending', APPROVED: 'Approved', REJECTED: 'Rejected', DISABLED: 'Disabled' };
export const statusBadge = (status) =>
  `<span class="badge-status st-${esc(status)}">${esc(STATUS_LABEL[status] || status)}</span>`;

export function initials(name) {
  return String(name || '?').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('') || '?';
}
/** Deterministic colour (0-5) derived from the name, so an employee keeps the same avatar colour. */
export function avatar(name, large = false) {
  let hash = 0;
  for (const ch of String(name)) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return `<span class="avatar avatar-${hash % 6}${large ? ' avatar-lg' : ''}" aria-hidden="true">${esc(initials(name))}</span>`;
}

export const formatDate = (iso) => iso
  ? new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : '–';
export const formatDateTime = (iso) => iso ? new Date(iso).toLocaleString() : '–';
/** Today as yyyy-mm-dd in the user's LOCAL time zone (toISOString would use UTC and can be a day off). */
export function todayLocalISO() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

// ----- Toasts -----
const TOAST_ICON = { success: 'check-circle', error: 'x-circle', warning: 'alert', info: 'info' };
function showToast(type, message) {
  const stack = document.getElementById('toasts');
  const el = document.createElement('div');
  el.className = `toast-item toast-${type}`;
  el.setAttribute('role', type === 'error' ? 'alert' : 'status');
  el.innerHTML = `${icon(TOAST_ICON[type], 't-icon')}<div class="t-msg"></div>
    <button type="button" class="t-close" aria-label="Dismiss notification">${icon('x')}</button>`;
  el.querySelector('.t-msg').textContent = message;          // textContent: never interpreted as HTML
  const remove = () => el.remove();
  el.querySelector('.t-close').addEventListener('click', remove);
  stack.appendChild(el);
  while (stack.children.length > 4) stack.firstElementChild.remove();
  setTimeout(remove, type === 'error' ? 7000 : 4000);
}
export const toast = {
  success: (m) => showToast('success', m),
  error: (m) => showToast('error', m),
  warning: (m) => showToast('warning', m),
  info: (m) => showToast('info', m)
};

// ----- Bootstrap modal cache -----
const modalCache = {};
export function modal(id) {
  if (!modalCache[id]) modalCache[id] = new bootstrap.Modal(document.getElementById(id));
  return modalCache[id];
}

export const pct = (n, total) => (total ? Math.round((n / total) * 100) : 0);

/** Switch a .stateful card between loading | empty | error | content. */
export const setState = (card, state) => { card.dataset.state = state; };

/** "5 min ago" style label for ISO timestamps (falls back to the date for older entries). */
export function relTime(iso) {
  if (!iso) return '–';
  const diff = (Date.now() - new Date(iso).getTime()) / 1000;
  if (diff < 60) return 'just now';
  if (diff < 3600) return `${Math.floor(diff / 60)} min ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} h ago`;
  if (diff < 7 * 86400) return `${Math.floor(diff / 86400)} d ago`;
  return new Date(iso).toLocaleDateString();
}

/** Renders prev / numbered / next pagination buttons into a <ul>. data = PageResponse. */
export function renderPager(ul, data, onPage) {
  ul.replaceChildren();
  if (!data || data.totalPages <= 1) return;
  const add = (label, page, { disabled = false, current = false, aria = '' } = {}) => {
    const li = document.createElement('li');
    const b = document.createElement('button');
    b.type = 'button'; b.textContent = label; b.disabled = disabled;
    if (aria) b.setAttribute('aria-label', aria);
    if (current) b.setAttribute('aria-current', 'page');
    b.addEventListener('click', () => onPage(page));
    li.appendChild(b); ul.appendChild(li);
  };
  add('‹', data.page - 1, { disabled: data.page === 0, aria: 'Previous page' });
  const start = Math.max(0, Math.min(data.page - 2, data.totalPages - 5));
  for (let p = start; p < Math.min(data.totalPages, start + 5); p++) add(String(p + 1), p, { current: p === data.page, aria: `Page ${p + 1}` });
  add('›', data.page + 1, { disabled: data.page >= data.totalPages - 1, aria: 'Next page' });
}

/** Builds a CSV string (with spreadsheet formula protection) from a header array and row arrays. */
export function toCsv(headers, rows) {
  const cell = (v) => {
    let s = v === null || v === undefined ? '' : String(v);
    if (s && '=+-@\t\r'.includes(s[0])) s = `'${s}`;
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  return [headers, ...rows].map((r) => r.map(cell).join(',')).join('\r\n') + '\r\n';
}

/** Show/hide a table "state box" (empty / error) under a table. */
export function showBox(box, html) { box.hidden = !html; box.innerHTML = html || ''; }
