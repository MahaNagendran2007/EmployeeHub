// Sign-in form and role-based visibility. NOTE: hiding UI is only a convenience; the server enforces every permission.
import { api, session } from './api.js';
import { $, $$, initials } from './ui.js';

export const currentUser = () => session.user;
export const isStaff = () => ['ADMIN', 'HR'].includes((session.user || {}).role);

export function initLogin(onSuccess) {
  const form = $('#loginForm');
  let busy = false;
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (busy) return;
    const username = $('#loginUser').value.trim();
    const password = $('#loginPass').value;
    const err = $('#loginError');
    err.classList.add('d-none');
    if (!username || !password) { err.textContent = 'Enter your username and password.'; err.classList.remove('d-none'); return; }
    busy = true; $('#loginBtn').disabled = true; $('#loginSpin').classList.remove('d-none'); $('#loginLabel').textContent = 'Signing in…';
    try {
      const res = await api.login(username, password);
      session.save(res);
      $('#loginPass').value = '';
      onSuccess(res.user);
    } catch (error) {
      err.textContent = error.status === 401 ? 'Invalid username or password.' : error.message;
      err.classList.remove('d-none');
    } finally {
      busy = false; $('#loginBtn').disabled = false; $('#loginSpin').classList.add('d-none'); $('#loginLabel').textContent = 'Sign in';
    }
  });
}

export function applyRoleVisibility(user) {
  $$('[data-roles]').forEach((el) => { el.hidden = !el.dataset.roles.split(',').includes(user.role); });
  $('#userName').textContent = user.displayName;
  $('#userRole').textContent = user.role;
  $('#upName').textContent = user.displayName;
  $('#upRole').textContent = user.role;
  $('#userAvatar').textContent = initials(user.displayName);
}
