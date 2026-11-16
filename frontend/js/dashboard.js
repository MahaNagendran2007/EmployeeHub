import { api } from './api.js';
import { $, $$, esc, avatar, statusBadge, setState, toast, pct, formatDate } from './ui.js';
import { icon } from './icons.js';
import { refreshLookups } from './lookups.js';
import { barChart, statusDoughnut, lineChart, chartsAvailable, PALETTE } from './charts.js';
import { openDetails, openEdit, confirmDelete } from './dialogs.js';
import { openMenu } from './menu.js';

const cards = { dept: $('#card-dept'), status: $('#card-status'), location: $('#card-location'), recent: $('#card-recent'), share: $('#card-share'), trend: $('#card-trend'), onleave: $('#card-onleave') };
const STATUS_ROWS = [['ACTIVE', 'Active', PALETTE.green], ['ON_LEAVE', 'On leave', PALETTE.amber], ['INACTIVE', 'Inactive', PALETTE.slate]];
let recentRows = [];

function setText(id, text) { $(id).textContent = text; }

function resetNumbers() {
  ['#kpiTotal', '#kpiActive', '#kpiLeave', '#kpiInactive', '#heroTotal', '#heroDepts', '#heroLocs', '#heroRate',
    '#chipActive', '#chipLeave', '#chipInactive', '#snapRate', '#snapDept', '#snapLoc', '#snapLeave', '#snapPending'].forEach((id) => setText(id, '–'));
  ['#kpiTotalNote', '#kpiActiveNote', '#kpiLeaveNote', '#kpiInactiveNote'].forEach((id) => { $(id).innerHTML = '&nbsp;'; });
  ['#barActive', '#barLeave', '#barInactive'].forEach((id) => { $(id).style.width = '0'; });
}

function renderSummary(stats, locations) {
  const total = stats.totalEmployees;
  const s = stats.byStatus;
  const active = s.ACTIVE ?? 0, leave = s.ON_LEAVE ?? 0, inactive = s.INACTIVE ?? 0;
  const plural = (n, w) => `${n} ${w}${n === 1 ? '' : 's'}`;

  setText('#heroTotal', total);
  setText('#heroDepts', stats.byDepartment.length);
  setText('#heroLocs', locations.length);
  setText('#heroRate', total ? `${pct(active, total)}%` : '0%');

  setText('#kpiTotal', total);
  $('#kpiTotalNote').textContent = total ? `${plural(stats.byDepartment.length, 'department')} · ${plural(locations.length, 'location')}` : 'No employees yet';
  [['Active', 'ACTIVE', active, 'currently working'], ['Leave', 'ON_LEAVE', leave, 'away on approved leave'], ['Inactive', 'INACTIVE', inactive, 'no longer active']].forEach(([id, , n, note]) => {
    setText(`#kpi${id}`, n);
    setText(`#chip${id}`, `${pct(n, total)}%`);
    $(`#kpi${id}Note`).textContent = total ? `${note}` : 'No employees yet';
    $(`#bar${id}`).style.width = `${pct(n, total)}%`;
  });

  setText('#snapRate', total ? `${pct(active, total)}%` : '–');
  setText('#snapDept', stats.byDepartment[0] ? `${stats.byDepartment[0].name} (${stats.byDepartment[0].count})` : '–');
  setText('#snapLoc', locations[0] ? `${locations[0].name} (${locations[0].count})` : '–');
  setText('#snapLeave', leave);
}

function renderCharts(stats, locations) {
  const total = stats.totalEmployees;
  const draw = (card, items, fn, subId, noun, singular) => {
    if (!items.length) return setState(card, 'empty');
    if (!chartsAvailable()) return setState(card, 'error');
    setState(card, 'content');                                  // show first so Chart.js measures a visible canvas
    const { shown, total: all } = fn();
    if (subId) $(subId).textContent = all > shown ? `Top ${shown} of ${all} ${noun}` : `Headcount per ${singular}`;
  };
  draw(cards.dept, stats.byDepartment, () => barChart('dept', $('#deptChart'), stats.byDepartment, { color: PALETTE.indigo, horizontal: true }), '#deptSub', 'departments', 'department');
  draw(cards.location, locations, () => barChart('location', $('#locationChart'), locations, { color: PALETTE.sky }), '#locSub', 'locations', 'office');

  if (total === 0) setState(cards.status, 'empty');
  else if (!chartsAvailable()) setState(cards.status, 'error');
  else {
    setState(cards.status, 'content');
    const rows = STATUS_ROWS.map(([key, label, color]) => ({ label, color, value: stats.byStatus[key] ?? 0 }));
    statusDoughnut('status', $('#statusChart'), rows);
    setText('#donutTotal', total);
    $('#statusLegend').innerHTML = rows.map((r) => `
      <li><span class="sw" style="background:${r.color}"></span><span class="l-name">${esc(r.label)}</span>
        <span class="l-val">${r.value}</span><span class="l-pct">${pct(r.value, total)}%</span></li>`).join('');
  }
}

function renderShare(stats) {
  if (!stats.byDepartment.length) return setState(cards.share, 'empty');
  const total = stats.totalEmployees;
  $('#deptShare').innerHTML = stats.byDepartment.slice(0, 8).map((d) => `
    <li class="share-row"><div class="share-top"><span class="s-name">${esc(d.name)}</span>
      <span class="s-val"><b>${d.count}</b> · ${pct(d.count, total)}%</span></div>
      <div class="share-track"><span style="width:${Math.max(3, pct(d.count, total))}%"></span></div></li>`).join('');
  setState(cards.share, 'content');
}

function renderRecent(page) {
  recentRows = page.content;
  if (!recentRows.length) return setState(cards.recent, 'empty');
  $('#recentRows').innerHTML = recentRows.map((e) => `
    <tr data-id="${esc(e.id)}">
      <td><div class="emp-cell">${avatar(e.fullName)}<div class="min-w-0">
        <button type="button" class="emp-name" data-act="view">${esc(e.fullName)}</button>
        <div class="emp-sub">${esc(e.employeeCode)} · Joined ${esc(formatDate(e.joiningDate))}</div></div></div></td>
      <td><div class="cell-main">${esc(e.department)}</div><div class="cell-sub">${esc(e.jobTitle)} · ${esc(e.location)}</div></td>
      <td>${statusBadge(e.employmentStatus)}</td>
      <td class="text-end"><button type="button" class="row-menu-btn" data-act="menu" aria-haspopup="menu" aria-expanded="false" aria-label="Actions for ${esc(e.fullName)}">${icon('more')}</button></td>
    </tr>`).join('');
  setState(cards.recent, 'content');
}

const monthLabel = (m) => { const [y, mo] = m.split('-').map(Number); return new Date(y, mo - 1, 1).toLocaleDateString(undefined, { month: 'short', year: '2-digit' }); };

function renderTrend(points) {
  if (!points.some((p) => p.count > 0)) { setState(cards.trend, 'empty'); return; }
  if (!chartsAvailable()) { setState(cards.trend, 'error'); return; }
  setState(cards.trend, 'content');
  lineChart('trend', $('#trendChart'), points.map((p) => ({ label: monthLabel(p.month), value: p.count })));
  $('#trendSub').textContent = `${points.reduce((s, p) => s + p.count, 0)} employees joined in the last ${points.length} months`;
}

function renderOnLeave(list) {
  if (!list.length) { setState(cards.onleave, 'empty'); return; }
  $('#onLeaveList').innerHTML = list.map((l) => `
    <li><button type="button" class="recent-item" data-emp="${esc(l.employeeId)}" aria-label="Open profile of ${esc(l.employeeName)}">
      ${avatar(l.employeeName)}<span class="recent-main"><span class="recent-name">${esc(l.employeeName)}</span>
      <span class="recent-meta">${esc(l.type.charAt(0) + l.type.slice(1).toLowerCase())} leave · until ${esc(formatDate(l.endDate))}</span></span></button></li>`).join('');
  setState(cards.onleave, 'content');
}

export async function loadDashboard() {
  Object.values(cards).forEach((c) => setState(c, 'loading'));
  resetNumbers();
  try {
    const [{ stats, locations }, recent] = await Promise.all([
      refreshLookups(),
      api.list({ page: 0, size: 6, sortBy: 'createdAt', direction: 'desc' })
    ]);
    renderSummary(stats, locations);
    renderCharts(stats, locations);
    renderShare(stats);
    renderRecent(recent);

    // Leave + trend data load independently so one failure never blanks the whole dashboard.
    const [trend, today, summary] = await Promise.allSettled([api.trends(12), api.onLeaveToday(), api.leaveSummary()]);
    if (trend.status === 'fulfilled') renderTrend(trend.value); else setState(cards.trend, 'error');
    if (today.status === 'fulfilled') {
      renderOnLeave(today.value);
      if (today.value.length) $('#kpiLeaveNote').textContent = `+${today.value.length} on approved leave today`;
    } else setState(cards.onleave, 'error');
    if (summary.status === 'fulfilled') setText('#snapPending', summary.value.byStatus.PENDING ?? 0);
  } catch (error) {
    Object.values(cards).forEach((c) => setState(c, 'error'));
    if (error.status !== 0) toast.error(error.message);   // network failures are explained by the banner
  }
}

$('#recentRows').addEventListener('click', (event) => {
  const tr = event.target.closest('tr[data-id]');
  if (!tr) return;
  const employee = recentRows.find((r) => r.id === tr.dataset.id);
  const act = event.target.closest('[data-act]')?.dataset.act || 'view';
  if (act === 'menu') {
    event.stopPropagation();
    openMenu(event.target.closest('[data-act]'), [
      { label: 'View profile', icon: 'eye', onSelect: () => openDetails(tr.dataset.id) },
      { label: 'Edit employee', icon: 'pencil', onSelect: () => openEdit(tr.dataset.id) },
      { separator: true },
      { label: 'Delete employee', icon: 'trash', danger: true, onSelect: () => employee && confirmDelete(employee) }
    ]);
  } else openDetails(tr.dataset.id);
});

// "Retry" buttons inside failed cards
$$('[data-retry]').forEach((b) => b.addEventListener('click', () => loadDashboard()));

$('#onLeaveList').addEventListener('click', (event) => {
  const item = event.target.closest('[data-emp]');
  if (item) openDetails(item.dataset.emp);
});
