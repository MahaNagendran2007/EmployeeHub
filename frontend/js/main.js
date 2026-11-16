// App shell: auth gate, hash routing, role-based navigation, topbar, connection status.
import { DOCS_URL, HEALTH_URL, API_ORIGIN, onConnectionChange, onUnauthorized, session, api } from './api.js';
import { $, $$, toast } from './ui.js';
import { loadDashboard } from './dashboard.js';
import { initEmployees, loadEmployees, setSearch } from './employees.js';
import { openCreate, setOnChange, setOnDeleted } from './dialogs.js';
import { onLookups } from './lookups.js';
import { initLogin, applyRoleVisibility } from './auth.js';
import { initNotifications, startPolling, stopPolling, refreshBadge } from './notifications.js';
import { loadProfile, reloadProfile } from './profile.js';
import { initLeave, loadLeave, updatePendingBadge } from './leave.js';
import { loadActivity } from './activity.js';
import { initReports, loadReports } from './reports.js';
import { initUsers, loadUsers } from './users.js';

// route -> { title, roles allowed (client-side convenience only; the API enforces permissions), loader }
const ROUTES = {
  dashboard: { title: 'Dashboard', roles: ['ADMIN', 'HR'], heading: '#dashTitle', load: () => loadDashboard() },
  employees: { title: 'Employee Directory', roles: ['ADMIN', 'HR'], heading: '#empTitle', load: () => loadEmployees() },
  profile: { title: 'Employee Profile', roles: ['ADMIN', 'HR'], heading: '#profTitle', load: (arg) => loadProfile(arg) },
  me: { title: 'My Profile', roles: ['EMPLOYEE', 'HR', 'ADMIN'], heading: '#profTitle', load: () => loadProfile('me') },
  leave: { title: 'Leave', roles: ['ADMIN', 'HR', 'EMPLOYEE'], heading: '#leaveTitle', load: () => loadLeave() },
  reports: { title: 'Reports', roles: ['ADMIN', 'HR'], heading: '#repTitle', load: () => loadReports() },
  activity: { title: 'Activity Log', roles: ['ADMIN', 'HR'], heading: '#actTitle', load: () => loadActivity() },
  users: { title: 'User Accounts', roles: ['ADMIN'], heading: '#usersTitle', load: () => loadUsers() }
};
const VIEW_ID = { profile: 'profile', me: 'profile' };
let current = { name: null, arg: null };
let started = false;

const user = () => session.user;
const defaultRoute = () => (user() && user().role === 'EMPLOYEE' ? 'me' : 'dashboard');

function parseHash() {
  const [name, arg] = location.hash.slice(1).split('/');
  return { name: ROUTES[name] ? name : defaultRoute(), arg: arg ? decodeURIComponent(arg) : null };
}

function route(focusHeading = true) {
  if (!user()) return;
  let { name, arg } = parseHash();
  if (!ROUTES[name].roles.includes(user().role)) { name = defaultRoute(); arg = null; history.replaceState(null, '', `#${name}`); }
  if (name === 'profile' && !arg) { name = 'employees'; history.replaceState(null, '', '#employees'); }
  current = { name, arg };
  const view = VIEW_ID[name] || name;
  $$('main > section').forEach((s) => s.classList.toggle('d-none', s.id !== `view-${view}`));
  const navKey = name === 'profile' ? 'employees' : name;
  $$('[data-route]').forEach((a) => (a.dataset.route === navKey ? a.setAttribute('aria-current', 'page') : a.removeAttribute('aria-current')));
  document.title = `${ROUTES[name].title} · EmployeeHub`;
  $('#crumbCurrent').textContent = ROUTES[name].title;
  closeDrawer();
  ROUTES[name].load(arg);
  if (user().role !== 'EMPLOYEE') updatePendingBadge();
  if (focusHeading) $(ROUTES[name].heading).focus({ preventScroll: true });
}
window.addEventListener('hashchange', () => route());

/** Reload whatever is on screen after a create / update / delete. */
const refresh = () => { if (!user()) return; ROUTES[current.name].load(current.arg); refreshBadge(); updatePendingBadge(); };
setOnChange(() => { (current.name === 'profile' || current.name === 'me') ? reloadProfile() : refresh(); });
setOnDeleted(() => { if (current.name === 'profile') location.hash = '#employees'; else refresh(); });

// ---------- connection status ----------
const banner = $('#offlineBanner');
onConnectionChange((online) => {
  const conn = $('#connStatus');
  conn.classList.toggle('is-online', online);
  conn.classList.toggle('is-offline', !online);
  $('#connText').textContent = online ? 'API connected' : 'API unreachable';
  banner.classList.toggle('d-none', online);
  if (!online) $('#offlineMessage').textContent = 'The Spring Boot API did not respond. Make sure it is running on the configured URL, then retry.';
});
$('#retryBtn').addEventListener('click', refresh);

// ---------- sidebar ----------
const sidebar = $('#sidebar');
const scrim = $('#scrim');
const menuBtn = $('#menuBtn');
function openDrawer() { sidebar.classList.add('is-open'); scrim.hidden = false; document.body.classList.add('nav-open'); menuBtn.setAttribute('aria-expanded', 'true'); }
function closeDrawer() { sidebar.classList.remove('is-open'); scrim.hidden = true; document.body.classList.remove('nav-open'); menuBtn.setAttribute('aria-expanded', 'false'); }
menuBtn.addEventListener('click', () => (sidebar.classList.contains('is-open') ? closeDrawer() : openDrawer()));
scrim.addEventListener('click', closeDrawer);
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeDrawer(); });

const collapseBtn = $('#collapseBtn');
function setCollapsed(collapsed) {
  document.body.classList.toggle('sidebar-collapsed', collapsed);
  collapseBtn.setAttribute('aria-pressed', String(collapsed));
  collapseBtn.setAttribute('aria-label', collapsed ? 'Expand sidebar' : 'Collapse sidebar');
  try { localStorage.setItem('eh.sidebarCollapsed', collapsed ? '1' : '0'); } catch { /* UI preference only */ }
}
collapseBtn.addEventListener('click', () => setCollapsed(!document.body.classList.contains('sidebar-collapsed')));
try { if (localStorage.getItem('eh.sidebarCollapsed') === '1') setCollapsed(true); } catch { /* ignore */ }

// ---------- topbar ----------
$('#heroDate').textContent = new Date().toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
$('#refreshBtn').addEventListener('click', async () => {
  const btn = $('#refreshBtn');
  btn.disabled = true; btn.querySelector('svg').classList.add('spinning');
  await loadDashboard();
  btn.querySelector('svg').classList.remove('spinning'); btn.disabled = false;
});
onLookups(({ stats }) => { const b = $('#navCount'); b.textContent = stats.totalEmployees; b.hidden = false; });
$('#connHost').textContent = API_ORIGIN.replace(/^https?:\/\//, '') || location.host;

$('#globalSearch').addEventListener('submit', (e) => {
  e.preventDefault();
  if (!user() || user().role === 'EMPLOYEE') return;
  const input = $('#globalSearchInput');
  setSearch(input.value);
  input.value = '';
  if (location.hash === '#employees') loadEmployees(); else location.hash = '#employees';
});
document.addEventListener('keydown', (e) => {
  const tag = (document.activeElement && document.activeElement.tagName) || '';
  if (e.key === '/' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(tag) && !e.ctrlKey && !e.metaKey && user()) { e.preventDefault(); $('#globalSearchInput').focus(); }
});
document.addEventListener('click', (e) => { if (e.target.closest('[data-action="add"]') && user() && user().role !== 'EMPLOYEE') openCreate(); });

// account menu
const userBtn = $('#userBtn');
const userPanel = $('#userPanel');
userBtn.addEventListener('click', (e) => { e.stopPropagation(); $('#notifPanel').hidden = true; userPanel.hidden = !userPanel.hidden; userBtn.setAttribute('aria-expanded', String(!userPanel.hidden)); });
document.addEventListener('click', (e) => { if (!userPanel.hidden && !userPanel.contains(e.target) && !userBtn.contains(e.target)) { userPanel.hidden = true; userBtn.setAttribute('aria-expanded', 'false'); } });
$('#logoutBtn').addEventListener('click', () => signOut('You have been signed out.'));

$('#docsNav').href = DOCS_URL;
$('#docsTile').href = DOCS_URL;
$('#healthNav').href = HEALTH_URL;

// ---------- auth gate ----------
function showLogin() {
  $('#appShell').hidden = true;
  $('#loginView').hidden = false;
  document.title = 'Sign in · EmployeeHub';
  setTimeout(() => $('#loginUser').focus(), 0);
}
function showApp() {
  $('#loginView').hidden = true;
  $('#appShell').hidden = false;
  applyRoleVisibility(user());
  if (!started) { started = true; initEmployees(); initLeave(); initReports(); initUsers(); initNotifications(); }
  startPolling();
  if (!location.hash || !ROUTES[location.hash.slice(1).split('/')[0]]) history.replaceState(null, '', `#${defaultRoute()}`);
  route(false);
}
/** Signing out reloads the page so no data of the previous user can remain in memory or in the DOM. */
function signOut(message) {
  session.clear(); stopPolling();
  try { if (message) sessionStorage.setItem('eh.flash', message); } catch { /* ignore */ }
  location.replace(location.pathname);
}
onUnauthorized(() => signOut('Your session expired. Please sign in again.'));

initLogin(() => { history.replaceState(null, '', location.pathname); showApp(); });
if (session.token && user()) {
  showApp();
  api.me().then((u) => { try { session.save({ token: session.token, user: u }); } catch { /* ignore */ } applyRoleVisibility(u); }).catch(() => {});
} else {
  showLogin();
  try {
    const flash = sessionStorage.getItem('eh.flash');
    if (flash) { sessionStorage.removeItem('eh.flash'); toast.info(flash); }
  } catch { /* ignore */ }
}
