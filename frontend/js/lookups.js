// Fetches department/location lists once per refresh and shares them with filters and form suggestions.
import { api } from './api.js';

const subscribers = [];
export const onLookups = (fn) => subscribers.push(fn);

function fillDatalist(id, names) {
  const list = document.getElementById(id);
  list.innerHTML = '';
  names.forEach((name) => { const o = document.createElement('option'); o.value = name; list.appendChild(o); });
}

export async function refreshLookups() {
  const [stats, locations] = await Promise.all([api.stats(), api.locations()]);
  const data = { stats, locations };
  fillDatalist('departmentOptions', stats.byDepartment.map((d) => d.name).sort((a, b) => a.localeCompare(b)));
  fillDatalist('locationOptions', locations.map((l) => l.name).sort((a, b) => a.localeCompare(b)));
  subscribers.forEach((fn) => fn(data));
  return data;
}
