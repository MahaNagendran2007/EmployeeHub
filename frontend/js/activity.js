import { api } from './api.js';
import { $, esc, relTime, renderPager, showBox } from './ui.js';
import { icon } from './icons.js';

let page = 0;
let requestId = 0;
const kind = (a) => (/DELETED|FAILED|REJECTED|DISABLED/.test(a) ? 'bad' : /APPROVED|CREATED|SUCCESS|IMPORTED|ENABLED/.test(a) ? 'good' : /SUBMITTED|UPDATED|EXPORTED/.test(a) ? 'warn' : '');

export async function loadActivity() {
  const ticket = ++requestId;
  showBox($('#actState'), '');
  $('#actRows').innerHTML = Array.from({ length: 6 }, () => '<tr aria-hidden="true"><td colspan="4"><div class="skeleton" style="height:34px"></div></td></tr>').join('');
  try {
    const data = await api.activity({ page, size: 20 });
    if (ticket !== requestId) return;
    $('#actInfo').textContent = `${data.totalElements} recorded event${data.totalElements === 1 ? '' : 's'}`;
    renderPager($('#actPager'), data, (p) => { page = p; loadActivity(); });
    if (!data.content.length) { $('#actRows').innerHTML = ''; showBox($('#actState'), `${icon('clipboard')}<p>No activity has been recorded yet.</p>`); return; }
    $('#actRows').innerHTML = data.content.map((a) => `
      <tr><td class="text-nowrap" title="${esc(new Date(a.timestamp).toLocaleString())}">${esc(relTime(a.timestamp))}<div class="cell-sub">${esc(new Date(a.timestamp).toLocaleString())}</div></td>
        <td><span class="action-chip ${kind(a.action)}">${esc(a.action.replace(/_/g, ' '))}</span></td>
        <td>${esc(a.summary)}</td><td class="col-dept">${esc(a.actor)}</td></tr>`).join('');
  } catch (error) {
    if (ticket !== requestId) return;
    $('#actRows').innerHTML = ''; $('#actPager').replaceChildren(); $('#actInfo').textContent = 'Activity unavailable';
    showBox($('#actState'), `${icon('alert')}<p>${esc(error.message)}</p><button class="btn btn-sm btn-outline-secondary" type="button" id="actRetry">Try again</button>`);
    $('#actRetry').addEventListener('click', loadActivity);
  }
}
