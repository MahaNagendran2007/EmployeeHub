// Leave module: apply, history, approve/reject.
import { api, session } from './api.js';
import { $, $$, esc, statusBadge, formatDate, toast, modal, renderPager, showBox, todayLocalISO } from './ui.js';
import { icon } from './icons.js';
import { refreshBadge } from './notifications.js';

const isStaff = () => ['ADMIN', 'HR'].includes((session.user || {}).role);
const state = { scope: 'mine', page: 0, status: '' };
const TYPE_LABEL = { ANNUAL: 'Annual', SICK: 'Sick', CASUAL: 'Casual', UNPAID: 'Unpaid', OTHER: 'Other' };
let rows = [];
let requestId = 0;

function syncTabs() {
  $$('#leaveTabs .tab').forEach((t) => {
    const on = t.dataset.scope === state.scope;
    t.classList.toggle('is-active', on); t.setAttribute('aria-pressed', String(on));
  });
  $('#leaveFilters').hidden = state.scope !== 'all';
}

export async function updatePendingBadge() {
  if (!isStaff()) return;
  try {
    const { byStatus } = await api.leaveSummary();
    const n = byStatus.PENDING || 0;
    [$('#leaveCount'), $('#leavePendingTab')].forEach((el) => { el.textContent = n; el.hidden = n === 0; });
  } catch { /* banner covers connectivity */ }
}

function renderRows() {
  const me = (session.user || {}).username;
  $('#leaveRows').innerHTML = rows.map((r) => `
    <tr>
      <td><div class="cell-main fw-semibold">${esc(r.employeeName)}</div><div class="cell-sub">${esc(r.employeeCode)}</div></td>
      <td>${esc(TYPE_LABEL[r.type] || r.type)}</td>
      <td class="text-nowrap">${esc(formatDate(r.startDate))}<div class="cell-sub">to ${esc(formatDate(r.endDate))}</div></td>
      <td class="col-join">${r.days}</td>
      <td class="col-dept"><div class="leave-reason" title="${esc(r.reason)}">${esc(r.reason)}</div>${r.decisionNote ? `<div class="cell-sub">Note: ${esc(r.decisionNote)}</div>` : ''}</td>
      <td>${statusBadge(r.status)}${r.decidedBy ? `<div class="cell-sub">by ${esc(r.decidedBy)}</div>` : ''}</td>
      <td>${isStaff() && r.status === 'PENDING' && r.requestedBy !== me ? `<div class="mini-actions">
        <button type="button" class="btn btn-sm btn-outline-secondary" data-decide="approve" data-id="${esc(r.id)}">${icon('check')} Approve</button>
        <button type="button" class="btn btn-sm btn-outline-danger" data-decide="reject" data-id="${esc(r.id)}">${icon('x')} Reject</button></div>` : ''}</td>
    </tr>`).join('');
}

export async function loadLeave() {
  const ticket = ++requestId;
  if (!isStaff()) state.scope = 'mine';
  syncTabs();
  showBox($('#leaveState'), '');
  $('#leaveRows').innerHTML = Array.from({ length: 4 }, () => '<tr aria-hidden="true"><td colspan="7"><div class="skeleton" style="height:38px"></div></td></tr>').join('');
  try {
    const data = state.scope === 'all'
      ? await api.leaves({ page: state.page, size: 10, status: state.status })
      : await api.myLeaves({ page: state.page, size: 10 });
    if (ticket !== requestId) return;
    rows = data.content;
    $('#leaveInfo').textContent = `${data.totalElements} request${data.totalElements === 1 ? '' : 's'}`;
    renderPager($('#leavePager'), data, (p) => { state.page = p; loadLeave(); });
    if (!rows.length) {
      $('#leaveRows').innerHTML = '';
      showBox($('#leaveState'), `${icon('calendar-check')}<p>${state.scope === 'all' ? 'No leave requests match.' : 'You have not requested any leave yet.'}</p>
        <button class="btn btn-sm btn-primary" type="button" data-apply>Apply for leave</button>`);
    } else renderRows();
    updatePendingBadge();
  } catch (error) {
    if (ticket !== requestId) return;
    rows = []; $('#leaveRows').innerHTML = ''; $('#leavePager').replaceChildren(); $('#leaveInfo').textContent = 'Leave requests unavailable';
    showBox($('#leaveState'), `${icon('alert')}<p>${esc(error.message)}</p><button class="btn btn-sm btn-outline-secondary" type="button" data-retry-leave>Try again</button>`);
  }
}

// ---------- apply modal ----------
const form = $('#leaveForm');
let applying = false;
const fieldIds = ['employeeCode', 'type', 'startDate', 'endDate', 'reason'];
const setErr = (name, msg) => { const el = $(`#lerr-${name}`); el.textContent = msg || ''; const input = $(`#l-${name}`); if (input) input.classList.toggle('is-invalid', Boolean(msg)); };
function clearLeaveErrors() { fieldIds.forEach((n) => setErr(n, '')); $('#leaveFormError').classList.add('d-none'); }

export function openApply() {
  clearLeaveErrors(); form.reset();
  $('#leaveEmployeeField').hidden = !isStaff();
  $('#l-startDate').value = todayLocalISO(); $('#l-endDate').value = todayLocalISO();
  applying = false;
  modal('leaveModal').show();
}
$('#leaveModal').addEventListener('hide.bs.modal', (e) => { if (applying) e.preventDefault(); });
$('#applyLeaveBtn').addEventListener('click', openApply);

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  if (applying) return;
  clearLeaveErrors();
  const v = { type: $('#l-type').value, startDate: $('#l-startDate').value, endDate: $('#l-endDate').value, reason: $('#l-reason').value.trim(), code: $('#l-employeeCode').value.trim() };
  let bad = false;
  if (isStaff() && !v.code) { setErr('employeeCode', 'Employee code is required.'); bad = true; }
  if (!v.startDate) { setErr('startDate', 'Start date is required.'); bad = true; }
  if (!v.endDate) { setErr('endDate', 'End date is required.'); bad = true; }
  if (v.startDate && v.endDate && v.endDate < v.startDate) { setErr('endDate', 'End date cannot be before the start date.'); bad = true; }
  if (v.reason.length < 5) { setErr('reason', 'Please give a reason (at least 5 characters).'); bad = true; }
  if (bad) { toast.warning('Please fix the highlighted fields.'); return; }

  applying = true; $('#leaveSave').disabled = true; $('#leaveCancel').disabled = true; $('#leaveSpin').classList.remove('d-none');
  try {
    let employeeId = null;
    if (isStaff()) {
      const found = await api.list({ search: v.code, size: 10 });
      const match = found.content.find((x) => x.employeeCode.toLowerCase() === v.code.toLowerCase());
      if (!match) { setErr('employeeCode', `No employee found with code ${v.code}.`); return; }
      employeeId = match.id;
    }
    await api.applyLeave({ employeeId, type: v.type, startDate: v.startDate, endDate: v.endDate, reason: v.reason });
    applying = false;
    modal('leaveModal').hide();
    toast.success('Leave request submitted.');
    state.page = 0; loadLeave(); refreshBadge();
  } catch (error) {
    (error.fieldErrors || []).forEach(({ field, message }) => setErr(field, message));
    const box = $('#leaveFormError'); box.textContent = error.message; box.classList.remove('d-none');
    toast.error(error.message);
  } finally {
    applying = false; $('#leaveSave').disabled = false; $('#leaveCancel').disabled = false; $('#leaveSpin').classList.add('d-none');
  }
});

// ---------- decision modal ----------
let decision = null;
let deciding = false;
$('#decisionModal').addEventListener('hide.bs.modal', (e) => { if (deciding) e.preventDefault(); });
function openDecision(id, approve) {
  const r = rows.find((x) => x.id === id);
  if (!r) return;
  decision = { id, approve };
  $('#decisionTitle').textContent = approve ? 'Approve leave?' : 'Reject leave?';
  $('#decisionText').textContent = `${r.employeeName} · ${TYPE_LABEL[r.type]} · ${formatDate(r.startDate)} to ${formatDate(r.endDate)} (${r.days} day${r.days === 1 ? '' : 's'})`;
  $('#decisionLabel').textContent = approve ? 'Approve' : 'Reject';
  $('#decisionConfirm').className = `btn ${approve ? 'btn-primary' : 'btn-danger'}`;
  $('#decisionNote').value = '';
  modal('decisionModal').show();
}
$('#decisionConfirm').addEventListener('click', async () => {
  if (deciding || !decision) return;
  deciding = true; $('#decisionConfirm').disabled = true; $('#decisionCancel').disabled = true; $('#decisionSpin').classList.remove('d-none');
  try {
    await api.decideLeave(decision.id, decision.approve, $('#decisionNote').value.trim());
    deciding = false; modal('decisionModal').hide();
    toast.success(decision.approve ? 'Leave approved.' : 'Leave rejected.');
    loadLeave();
  } catch (error) {
    deciding = false; modal('decisionModal').hide(); toast.error(error.message); loadLeave();
  } finally {
    deciding = false; $('#decisionConfirm').disabled = false; $('#decisionCancel').disabled = false; $('#decisionSpin').classList.add('d-none');
  }
});

export function initLeave() {
  $$('#leaveTabs .tab').forEach((t) => t.addEventListener('click', () => { state.scope = t.dataset.scope; state.page = 0; loadLeave(); }));
  $('#leaveStatusFilter').addEventListener('change', (e) => { state.status = e.target.value; state.page = 0; loadLeave(); });
  $('#leaveRows').addEventListener('click', (e) => {
    const b = e.target.closest('[data-decide]');
    if (b) openDecision(b.dataset.id, b.dataset.decide === 'approve');
  });
  $('#leaveState').addEventListener('click', (e) => {
    if (e.target.closest('[data-apply]')) openApply();
    if (e.target.closest('[data-retry-leave]')) loadLeave();
  });
}
