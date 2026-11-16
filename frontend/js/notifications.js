import { api } from './api.js';
import { $, esc, relTime, toast } from './ui.js';

let timer = null;
const bell = $('#bellBtn');
const panel = $('#notifPanel');

export async function refreshBadge() {
  try {
    const { count } = await api.unreadCount();
    const badge = $('#bellBadge');
    badge.textContent = count > 99 ? '99+' : count;
    badge.hidden = count === 0;
  } catch { /* the connection banner already reports network problems */ }
}

async function renderList() {
  const list = $('#notifList');
  list.innerHTML = '<li class="notif-empty">Loading…</li>';
  try {
    const items = await api.notifications();
    list.innerHTML = items.length ? items.map((n) => `
      <li><button type="button" class="notif-item${n.read ? '' : ' unread'}" data-id="${esc(n.id)}" data-type="${esc(n.type)}">
        <span class="notif-dot"></span><span><span class="notif-msg d-block">${esc(n.message)}</span><span class="notif-time">${esc(relTime(n.createdAt))}</span></span></button></li>`).join('')
      : '<li class="notif-empty">You are all caught up.</li>';
  } catch (error) {
    list.innerHTML = `<li class="notif-empty">${esc(error.message)}</li>`;
  }
}

function close() { panel.hidden = true; bell.setAttribute('aria-expanded', 'false'); }

export function initNotifications() {
  bell.addEventListener('click', async (e) => {
    e.stopPropagation();
    $('#userPanel').hidden = true;
    if (!panel.hidden) { close(); return; }
    panel.hidden = false; bell.setAttribute('aria-expanded', 'true');
    await renderList();
  });
  $('#notifList').addEventListener('click', async (e) => {
    const item = e.target.closest('.notif-item');
    if (!item) return;
    try { await api.markRead(item.dataset.id); } catch (error) { toast.error(error.message); }
    close(); refreshBadge();
    if (item.dataset.type.startsWith('LEAVE')) location.hash = '#leave';
  });
  $('#markAllRead').addEventListener('click', async () => {
    try { await api.markAllRead(); await renderList(); refreshBadge(); } catch (error) { toast.error(error.message); }
  });
  document.addEventListener('click', (e) => { if (!panel.hidden && !panel.contains(e.target) && !bell.contains(e.target)) close(); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !panel.hidden) { close(); bell.focus(); } });
}

export function startPolling() { stopPolling(); refreshBadge(); timer = setInterval(() => { if (!document.hidden) refreshBadge(); }, 60000); }
export function stopPolling() { if (timer) clearInterval(timer); timer = null; close(); }
