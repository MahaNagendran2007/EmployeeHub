// Employee directory: search, filters, sorting, pagination and row actions.
import { api, saveBlob } from './api.js';
import { $, $$, esc, debounce, avatar, statusBadge, formatDate, toast, modal, toCsv } from './ui.js';
import { icon } from './icons.js';
import { openMenu } from './menu.js';
import { onLookups, refreshLookups } from './lookups.js';
import { openDetails, openEdit, confirmDelete } from './dialogs.js';

const state = { page: 0, size: 10, sortBy: 'createdAt', direction: 'desc', search: '', department: '', location: '', status: '', designation: '' };
let rows = [];
let requestId = 0;

const tbody = $('#employeeRows');
const stateBox = $('#tableState');

function filtersActive() { return Boolean(state.search || state.department || state.location || state.status || state.designation); }

// Keep the filter dropdowns in sync with real departments/locations.
function fillSelect(select, names, current) {
  const first = select.firstElementChild;
  select.replaceChildren(first);
  names.forEach((n) => { const o = document.createElement('option'); o.value = n; o.textContent = n; select.appendChild(o); });
  select.value = names.includes(current) ? current : '';
}
onLookups(({ stats, locations }) => {
  fillSelect($('#filterDepartment'), stats.byDepartment.map((d) => d.name).sort((a, b) => a.localeCompare(b)), state.department);
  fillSelect($('#filterLocation'), locations.map((l) => l.name).sort((a, b) => a.localeCompare(b)), state.location);
  state.department = $('#filterDepartment').value;
  state.location = $('#filterLocation').value;
  $('#tabAll').textContent = stats.totalEmployees;
  ['ACTIVE', 'ON_LEAVE', 'INACTIVE'].forEach((k) => { $(`#tab${k}`).textContent = stats.byStatus[k] ?? 0; });
});

function syncTabs() {
  $$('#statusTabs .tab').forEach((t) => {
    const on = t.dataset.status === state.status;
    t.classList.toggle('is-active', on);
    t.setAttribute('aria-pressed', String(on));
  });
}

function renderSortIndicators() {
  $$('th.sortable').forEach((th) => {
    const active = th.dataset.sort === state.sortBy;
    th.setAttribute('aria-sort', active ? (state.direction === 'asc' ? 'ascending' : 'descending') : 'none');
    const name = active ? (state.direction === 'asc' ? 'arrow-up' : 'arrow-down') : 'sort';
    $('use', th).setAttribute('href', `#i-${name}`);
  });
}

function renderSkeleton() {
  stateBox.hidden = true;
  tbody.innerHTML = Array.from({ length: Math.min(state.size, 6) }, () =>
    `<tr aria-hidden="true"><td colspan="7"><div class="skeleton" style="height:38px"></div></td></tr>`).join('');
}

function renderRows() {
  tbody.innerHTML = rows.map((e) => `
    <tr data-id="${esc(e.id)}">
      <td><div class="emp-cell">${avatar(e.fullName)}
        <div class="min-w-0"><button type="button" class="emp-name" data-act="view">${esc(e.fullName)}</button>
        <div class="emp-sub">${esc(e.email)}</div></div></div></td>
      <td class="col-code"><code>${esc(e.employeeCode)}</code></td>
      <td class="col-dept"><div class="cell-main">${esc(e.department)}</div><div class="cell-sub">${esc(e.jobTitle)}</div></td>
      <td class="col-loc"><span class="cell-loc">${icon('pin')}${esc(e.location)}</span></td>
      <td class="col-join text-nowrap">${esc(formatDate(e.joiningDate))}</td>
      <td>${statusBadge(e.employmentStatus)}</td>
      <td class="text-end"><button type="button" class="row-menu-btn" data-act="menu" aria-haspopup="menu" aria-expanded="false" aria-label="Actions for ${esc(e.fullName)}">${icon('more')}</button></td>
    </tr>`).join('');
}

function showState(kind, message) {
  tbody.innerHTML = '';
  stateBox.hidden = false;
  if (kind === 'empty-filtered') {
    stateBox.innerHTML = `${icon('search')}<p>No employees match your search or filters.</p>
      <button class="btn btn-sm btn-outline-secondary" type="button" data-act="reset">Clear filters</button>`;
  } else if (kind === 'empty') {
    stateBox.innerHTML = `${icon('inbox')}<p>No employees yet. Add your first employee to get started.</p>
      <button class="btn btn-sm btn-primary" type="button" data-action="add">${icon('plus')} Add Employee</button>`;
  } else {
    stateBox.innerHTML = `${icon('alert')}<p>${esc(message)}</p>
      <button class="btn btn-sm btn-outline-secondary" type="button" data-act="retry">Try again</button>`;
  }
}

function renderPagination(data) {
  const { totalElements: total, totalPages } = data;
  const from = total === 0 ? 0 : state.page * state.size + 1;
  const to = Math.min(total, (state.page + 1) * state.size);
  $('#pageInfo').textContent = total === 0 ? '0 employees' : `Showing ${from}–${to} of ${total} employee${total === 1 ? '' : 's'}`;

  const ul = $('#pagination');
  ul.replaceChildren();
  if (totalPages <= 1) return;
  const add = (label, page, { disabled = false, current = false, aria = '' } = {}) => {
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = label;
    btn.disabled = disabled;
    if (aria) btn.setAttribute('aria-label', aria);
    if (current) btn.setAttribute('aria-current', 'page');
    btn.addEventListener('click', () => { state.page = page; loadEmployees(); });
    li.appendChild(btn);
    ul.appendChild(li);
  };
  add('‹', state.page - 1, { disabled: state.page === 0, aria: 'Previous page' });
  const start = Math.max(0, Math.min(state.page - 2, totalPages - 5));
  for (let p = start; p < Math.min(totalPages, start + 5); p++) add(String(p + 1), p, { current: p === state.page, aria: `Page ${p + 1}` });
  add('›', state.page + 1, { disabled: state.page >= totalPages - 1, aria: 'Next page' });
}

export async function loadEmployees() {
  const ticket = ++requestId;                      // ignore responses that arrive out of order
  renderSortIndicators();
  syncTabs();
  renderSkeleton();
  try {
    const data = await api.list(state);
    if (ticket !== requestId) return;
    if (data.content.length === 0 && data.totalPages > 0 && state.page > 0) {   // e.g. last row of last page deleted
      state.page = data.totalPages - 1;
      return loadEmployees();
    }
    rows = data.content;
    renderPagination(data);
    if (rows.length === 0) showState(filtersActive() ? 'empty-filtered' : 'empty');
    else { stateBox.hidden = true; renderRows(); }
    refreshLookups().catch(() => {});             // keep department/location filters in sync with the database
    api.designations().then((names) => { fillSelect($('#filterDesignation'), names, state.designation); state.designation = $('#filterDesignation').value; }).catch(() => {});
  } catch (error) {
    if (ticket !== requestId) return;
    rows = [];
    $('#pageInfo').textContent = 'Employees unavailable';
    $('#pagination').replaceChildren();
    showState('error', error.message);
    if (error.status !== 0) toast.error(error.message);
  }
}

/** Used by the topbar search: jump to the directory with a search term. */
export function setSearch(term) {
  state.search = term.trim();
  state.page = 0;
  $('#searchInput').value = state.search;
}

function resetFilters() {
  Object.assign(state, { search: '', department: '', location: '', status: '', designation: '', page: 0 });
  $('#searchInput').value = '';
  ['#filterDepartment', '#filterLocation', '#filterDesignation'].forEach((s) => { $(s).value = ''; });
  loadEmployees();
}

export function initEmployees() {
  wireExportImport();
  $('#searchInput').addEventListener('input', debounce((e) => { state.search = e.target.value.trim(); state.page = 0; loadEmployees(); }, 300));
  [['#filterDepartment', 'department'], ['#filterLocation', 'location'], ['#filterDesignation', 'designation']].forEach(([sel, key]) => {
    $(sel).addEventListener('change', (e) => { state[key] = e.target.value; state.page = 0; loadEmployees(); });
  });
  $('#pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 0; loadEmployees(); });
  $('#clearFilters').addEventListener('click', resetFilters);
  $$('#statusTabs .tab').forEach((t) => t.addEventListener('click', () => { state.status = t.dataset.status; state.page = 0; loadEmployees(); }));

  $$('th.sortable').forEach((th) => $('button', th).addEventListener('click', () => {
    if (state.sortBy === th.dataset.sort) state.direction = state.direction === 'asc' ? 'desc' : 'asc';
    else { state.sortBy = th.dataset.sort; state.direction = 'asc'; }
    state.page = 0;
    loadEmployees();
  }));

  stateBox.addEventListener('click', (e) => {
    const act = e.target.closest('[data-act]')?.dataset.act;
    if (act === 'reset') resetFilters();
    if (act === 'retry') loadEmployees();
  });

  tbody.addEventListener('click', (e) => {
    const tr = e.target.closest('tr[data-id]');
    if (!tr) return;
    const employee = rows.find((r) => r.id === tr.dataset.id);
    const trigger = e.target.closest('[data-act]');
    const act = trigger?.dataset.act || 'view';          // clicking anywhere on a row opens the profile
    if (act === 'menu') {
      e.stopPropagation();
      openMenu(trigger, [
        { label: 'View profile', icon: 'eye', onSelect: () => openDetails(tr.dataset.id) },
        { label: 'Edit employee', icon: 'pencil', onSelect: () => openEdit(tr.dataset.id) },
        { separator: true },
        { label: 'Delete employee', icon: 'trash', danger: true, onSelect: () => employee && confirmDelete(employee) }
      ]);
    } else openDetails(tr.dataset.id);
  });
}

// ---------- CSV export / import ----------
function wireExportImport() {
  $('#exportBtn').addEventListener('click', async () => {
    const btn = $('#exportBtn');
    btn.disabled = true;
    try {
      const { search, department, location, status, designation, sortBy, direction } = state;
      const blob = await api.exportCsv({ search, department, location, status, designation, sortBy: sortBy === 'createdAt' ? 'fullName' : sortBy, direction });
      saveBlob(blob, 'employees.csv');
      toast.success(filtersActive() ? 'Exported the employees matching your filters.' : 'Exported all employees.');
    } catch (error) { toast.error(error.message); }
    finally { btn.disabled = false; }
  });

  const box = $('#importResult');
  let running = false;
  $('#importBtn').addEventListener('click', () => { $('#importFile').value = ''; box.hidden = true; box.innerHTML = ''; modal('importModal').show(); });
  $('#importModal').addEventListener('hide.bs.modal', (e) => { if (running) e.preventDefault(); });
  $('#templateBtn').addEventListener('click', () => saveBlob(new Blob([toCsv(
    ['employeeCode', 'fullName', 'email', 'department', 'jobTitle', 'location', 'phone', 'joiningDate', 'employmentStatus', 'reportingManager'],
    [['EMP-2001', 'Sample Person', 'sample.person@example.com', 'Engineering', 'Software Engineer', 'Chennai', '', '2025-01-15', 'ACTIVE', '']])], { type: 'text/csv;charset=utf-8' }), 'employee-import-template.csv'));
  $('#importRun').addEventListener('click', async () => {
    const file = $('#importFile').files[0];
    if (running) return;
    if (!file) { toast.warning('Choose a CSV file first.'); return; }
    if (file.size > 2 * 1024 * 1024) { toast.error('The file is larger than 2 MB.'); return; }
    running = true; $('#importRun').disabled = true; $('#importClose').disabled = true; $('#importSpin').classList.remove('d-none');
    try {
      const r = await api.importCsv(file);
      box.hidden = false;
      box.innerHTML = `<div class="import-summary"><div class="kpis"><div><b>${r.totalRows}</b>rows</div><div><b class="text-success">${r.imported}</b>imported</div><div><b class="${r.skipped ? 'text-danger' : ''}">${r.skipped}</b>skipped</div></div>
        ${r.errors.length ? `<ul class="import-errors">${r.errors.map((x) => `<li>Row ${x.row}: ${esc(x.message)}</li>`).join('')}</ul>${r.errorsTruncated ? '<p class="small text-secondary mb-0">Only the first 100 problems are listed.</p>' : ''}` : '<p class="mb-0 text-success">Every row was imported.</p>'}</div>`;
      (r.imported ? toast.success : toast.warning)(`${r.imported} imported, ${r.skipped} skipped.`);
      if (r.imported) { state.page = 0; loadEmployees(); }
    } catch (error) { toast.error(error.message); }
    finally { running = false; $('#importRun').disabled = false; $('#importClose').disabled = false; $('#importSpin').classList.add('d-none'); }
  });
}
