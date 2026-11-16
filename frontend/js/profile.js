// Full employee profile page (#profile/<id> for staff, #me for the signed-in employee).
import { api, session } from './api.js';
import { $, esc, avatar, statusBadge, formatDate, formatDateTime, toast } from './ui.js';
import { icon } from './icons.js';
import { openEdit, confirmDelete } from './dialogs.js';

let current = null;

function info(ic, label, value) {
  return `<div class="info">${icon(ic)}<div><dt>${esc(label)}</dt><dd>${esc(value || 'Not provided')}</dd></div></div>`;
}

export async function loadProfile(idOrMe) {
  const isMe = idOrMe === 'me';
  const user = session.user || {};
  const staff = user.role === 'ADMIN' || user.role === 'HR';
  const card = $('#profCard');
  $('#profActions').innerHTML = '';
  $('#profBack').hidden = isMe || !staff;
  card.innerHTML = '<div class="p-4"><div class="skeleton" style="height:140px"></div><div class="skeleton mt-3" style="height:180px"></div></div>';
  try {
    const e = isMe ? await api.myEmployee() : await api.get(idOrMe);
    current = e;
    $('#profTitle').textContent = e.fullName;
    document.title = `${e.fullName} · EmployeeHub`;
    card.classList.add('profile-page');
    card.innerHTML = `
      <div class="profile-banner"></div>
      <div class="profile-head">${avatar(e.fullName, true)}
        <div class="ph-text"><h3>${esc(e.fullName)}</h3><p>${esc(e.jobTitle)} · ${esc(e.department)}</p></div>${statusBadge(e.employmentStatus)}</div>
      <div class="profile-body">
        <dl class="info-grid">
          ${info('badge', 'Employee ID', e.employeeCode)}${info('mail', 'Email', e.email)}${info('phone', 'Phone', e.phone)}
          ${info('building', 'Department', e.department)}${info('briefcase', 'Designation', e.jobTitle)}${info('pin', 'Office location', e.location)}
          ${info('calendar', 'Joining date', formatDate(e.joiningDate))}${info('user', 'Reporting manager', e.reportingManager)}
          ${info('shield', 'Emergency contact', [e.emergencyContactName, e.emergencyContactPhone].filter(Boolean).join(' · '))}
        </dl>
        <p class="meta-line">Created ${esc(formatDateTime(e.createdAt))} · Last updated ${esc(formatDateTime(e.updatedAt))}</p>
      </div>`;
    if (staff) {
      $('#profActions').innerHTML = `
        ${user.role === 'ADMIN' ? `<button class="btn btn-outline-danger" type="button" data-p="delete">${icon('trash')} Delete</button>` : ''}
        <button class="btn btn-primary" type="button" data-p="edit">${icon('pencil')} Edit profile</button>`;
    }
  } catch (error) {
    card.classList.remove('profile-page');
    card.innerHTML = `<div class="state-box">${icon('alert')}<p>${esc(error.message)}</p>
      <a class="btn btn-sm btn-outline-secondary" href="${isMe ? '#me' : '#employees'}">${isMe ? 'Retry' : 'Back to directory'}</a></div>`;
    if (error.status !== 0 && error.status !== 404) toast.error(error.message);
  }
}

export const reloadProfile = () => (current ? loadProfile(location.hash.startsWith('#me') ? 'me' : current.id) : Promise.resolve());

$('#profActions').addEventListener('click', (e) => {
  const act = e.target.closest('[data-p]')?.dataset.p;
  if (!current) return;
  if (act === 'edit') openEdit(current.id);
  if (act === 'delete') confirmDelete(current);
});
