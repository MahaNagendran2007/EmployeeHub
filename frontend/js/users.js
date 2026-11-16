import { api, session } from './api.js';
import { $, esc, avatar, toast, modal, showBox } from './ui.js';
import { icon } from './icons.js';

const form = $('#userForm');
const names = ['username', 'displayName', 'password', 'role', 'employeeCode'];
const setErr = (n, m) => { $(`#uerr-${n}`).textContent = m || ''; $(`#u-${n}`).classList.toggle('is-invalid', Boolean(m)); };
let saving = false;
let list = [];

export async function loadUsers() {
  showBox($('#userState'), '');
  try {
    list = await api.users();
    const me = (session.user || {}).username;
    $('#userRows').innerHTML = list.map((u) => `
      <tr><td><div class="emp-cell">${avatar(u.displayName)}<div><div class="emp-name">${esc(u.displayName)}</div><div class="emp-sub">@${esc(u.username)}</div></div></div></td>
        <td><span class="role-pill">${esc(u.role)}</span></td>
        <td class="col-dept">${esc(u.employeeCode || '–')}</td>
        <td><span class="badge-status st-${u.enabled ? 'ACTIVE' : 'DISABLED'}">${u.enabled ? 'Enabled' : 'Disabled'}</span></td>
        <td class="text-end">${u.username === me ? '' : `<button type="button" class="btn btn-sm btn-outline-secondary" data-toggle="${esc(u.id)}" data-enable="${u.enabled ? 'false' : 'true'}">${u.enabled ? 'Disable' : 'Enable'}</button>`}</td></tr>`).join('');
    if (!list.length) showBox($('#userState'), `${icon('shield')}<p>No accounts yet.</p>`);
  } catch (error) {
    $('#userRows').innerHTML = '';
    showBox($('#userState'), `${icon('alert')}<p>${esc(error.message)}</p><button class="btn btn-sm btn-outline-secondary" type="button" id="usersRetry">Try again</button>`);
    $('#usersRetry').addEventListener('click', loadUsers);
  }
}

export function initUsers() {
  $('#newUserBtn').addEventListener('click', () => {
    form.reset(); names.forEach((n) => setErr(n, '')); $('#userFormError').classList.add('d-none'); saving = false;
    modal('userModal').show();
  });
  $('#userModal').addEventListener('hide.bs.modal', (e) => { if (saving) e.preventDefault(); });
  $('#u-role').addEventListener('change', () => { $('#u-code-hint').textContent = $('#u-role').value === 'EMPLOYEE' ? '(required for Employee role)' : '(optional)'; });
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (saving) return;
    names.forEach((n) => setErr(n, '')); $('#userFormError').classList.add('d-none');
    const v = { username: $('#u-username').value.trim().toLowerCase(), displayName: $('#u-displayName').value.trim(), password: $('#u-password').value, role: $('#u-role').value, employeeCode: $('#u-employeeCode').value.trim() };
    let bad = false;
    if (!/^[a-z0-9._-]{3,40}$/.test(v.username)) { setErr('username', 'Use 3–40 lower-case letters, digits, dot, underscore or hyphen.'); bad = true; }
    if (!v.displayName) { setErr('displayName', 'Display name is required.'); bad = true; }
    if (v.password.length < 10) { setErr('password', 'Password must be at least 10 characters.'); bad = true; }
    if (v.role === 'EMPLOYEE' && !v.employeeCode) { setErr('employeeCode', 'Employee accounts must be linked to an employee code.'); bad = true; }
    if (bad) return;
    saving = true; $('#userSave').disabled = true; $('#userCancel').disabled = true; $('#userSpin').classList.remove('d-none');
    try {
      await api.createUser({ ...v, employeeCode: v.employeeCode || null });
      saving = false; modal('userModal').hide(); toast.success(`Account ${v.username} created.`); loadUsers();
    } catch (error) {
      (error.fieldErrors || []).forEach(({ field, message }) => { if (names.includes(field)) setErr(field, message); });
      const box = $('#userFormError'); box.textContent = error.message; box.classList.remove('d-none');
    } finally {
      saving = false; $('#userSave').disabled = false; $('#userCancel').disabled = false; $('#userSpin').classList.add('d-none');
    }
  });
  $('#userRows').addEventListener('click', async (e) => {
    const b = e.target.closest('[data-toggle]');
    if (!b) return;
    b.disabled = true;
    try { await api.setUserEnabled(b.dataset.toggle, b.dataset.enable === 'true'); toast.success('Account updated.'); loadUsers(); }
    catch (error) { toast.error(error.message); b.disabled = false; }
  });
}
