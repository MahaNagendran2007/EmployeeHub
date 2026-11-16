// Single place that talks to the Spring Boot REST API (adds the Bearer token, handles errors and 401s).
const ORIGIN = ((window.APP_CONFIG && window.APP_CONFIG.API_BASE_URL) || '').replace(/\/+$/, '');
const TIMEOUT_MS = 20000;

export const API_ORIGIN = ORIGIN;
export const DOCS_URL = `${ORIGIN}/swagger-ui/index.html`;
export const HEALTH_URL = `${ORIGIN}/actuator/health`;

export class ApiError extends Error {
  constructor(message, status, fieldErrors = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;          // 0 = network failure / timeout
    this.fieldErrors = fieldErrors;
  }
}

// ---------- session (token lives in sessionStorage: cleared when the tab closes) ----------
const TOKEN_KEY = 'eh.token';
const USER_KEY = 'eh.user';
const read = (k) => { try { return sessionStorage.getItem(k); } catch { return null; } };
export const session = {
  get token() { return read(TOKEN_KEY); },
  get user() { try { return JSON.parse(read(USER_KEY) || 'null'); } catch { return null; } },
  save(login) {
    try { sessionStorage.setItem(TOKEN_KEY, login.token); sessionStorage.setItem(USER_KEY, JSON.stringify(login.user)); } catch { /* storage unavailable */ }
  },
  clear() { try { sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(USER_KEY); } catch { /* ignore */ } }
};
const unauthorizedListeners = new Set();
export const onUnauthorized = (fn) => unauthorizedListeners.add(fn);

// ---------- connection state derived from real request outcomes ----------
const connectionListeners = new Set();
let connected = null;
export function onConnectionChange(fn) { connectionListeners.add(fn); }
function setConnected(value) {
  if (connected !== value) { connected = value; connectionListeners.forEach((fn) => fn(value)); }
}

async function send(path, { method = 'GET', body, form, blob = false, auth = true } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  const headers = { Accept: blob ? '*/*' : 'application/json' };
  if (body) headers['Content-Type'] = 'application/json';
  if (auth && session.token) headers.Authorization = `Bearer ${session.token}`;
  let response;
  try {
    response = await fetch(`${ORIGIN}${path}`, { method, headers, body: form || (body ? JSON.stringify(body) : undefined), signal: controller.signal });
  } catch (err) {
    setConnected(false);
    throw new ApiError(err.name === 'AbortError' ? 'The server took too long to respond.'
      : 'Cannot reach the server. Check that the backend is running and the API URL is correct.', 0);
  } finally {
    clearTimeout(timer);
  }
  setConnected(true);

  if (response.status === 204) return null;
  if (response.ok && blob) return response.blob();
  let data = null;
  try { data = await response.json(); } catch { /* empty or non-JSON body */ }
  if (!response.ok) {
    if (response.status === 401 && auth && path !== '/api/auth/login') {
      session.clear();
      unauthorizedListeners.forEach((fn) => fn());
      throw new ApiError('Your session has expired. Please sign in again.', 401);
    }
    throw new ApiError((data && data.message) || `Request failed (HTTP ${response.status})`, response.status, (data && data.fieldErrors) || []);
  }
  return data;
}

const qs = (params = {}) => {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== '' && v !== null && v !== undefined) q.set(k, v); });
  const s = q.toString();
  return s ? `?${s}` : '';
};
const E = '/api/employees';
const id = encodeURIComponent;

export const api = {
  // auth
  login: (username, password) => send('/api/auth/login', { method: 'POST', body: { username, password }, auth: false }),
  me: () => send('/api/auth/me'),
  myEmployee: () => send('/api/me/employee'),
  // employees
  list: (params) => send(`${E}${qs(params)}`),
  get: (employeeId) => send(`${E}/${id(employeeId)}`),
  create: (payload) => send(E, { method: 'POST', body: payload }),
  update: (employeeId, payload) => send(`${E}/${id(employeeId)}`, { method: 'PUT', body: payload }),
  remove: (employeeId) => send(`${E}/${id(employeeId)}`, { method: 'DELETE' }),
  stats: () => send(`${E}/stats`),
  locations: () => send(`${E}/locations`),
  designations: () => send(`${E}/designations`),
  trends: (months = 12) => send(`${E}/trends${qs({ months })}`),
  exportCsv: (params) => send(`${E}/export${qs(params)}`, { blob: true }),
  importCsv: (file) => { const f = new FormData(); f.append('file', file); return send(`${E}/import`, { method: 'POST', form: f }); },
  // leave
  applyLeave: (payload) => send('/api/leaves', { method: 'POST', body: payload }),
  leaves: (params) => send(`/api/leaves${qs(params)}`),
  myLeaves: (params) => send(`/api/leaves/mine${qs(params)}`),
  decideLeave: (leaveId, approve, note) => send(`/api/leaves/${id(leaveId)}/${approve ? 'approve' : 'reject'}`, { method: 'POST', body: { note: note || null } }),
  onLeaveToday: () => send('/api/leaves/on-leave-today'),
  leaveSummary: () => send('/api/leaves/summary'),
  // activity + notifications
  activity: (params) => send(`/api/activity${qs(params)}`),
  notifications: () => send('/api/notifications?size=20'),
  unreadCount: () => send('/api/notifications/unread-count'),
  markRead: (nid) => send(`/api/notifications/${id(nid)}/read`, { method: 'POST' }),
  markAllRead: () => send('/api/notifications/read-all', { method: 'POST' }),
  // users
  users: () => send('/api/users'),
  createUser: (payload) => send('/api/users', { method: 'POST', body: payload }),
  setUserEnabled: (uid, enabled) => send(`/api/users/${id(uid)}/enabled`, { method: 'PATCH', body: { enabled } })
};

/** Triggers a browser download of a Blob. */
export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = filename;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
