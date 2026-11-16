// Reports: preview from live API data, CSV download, print / save as PDF (browser print dialog).
import { api, saveBlob } from './api.js';
import { $, $$, esc, toast, toCsv, pct, formatDate, showBox } from './ui.js';
import { icon } from './icons.js';
import { refreshLookups } from './lookups.js';

let current = null;       // key of the selected report
let data = null;          // { headers, rows, meta, serverExport? }
let ticket = 0;

const filters = () => ({ department: $('#repDept').value, location: $('#repLoc').value, status: $('#repStatus').value });
const monthLabel = (m) => { const [y, mo] = m.split('-').map(Number); return new Date(y, mo - 1, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' }); };
const share = (rows) => { const total = rows.reduce((s, r) => s + r[1], 0); return rows.map((r) => [r[0], r[1], `${pct(r[1], total)}%`]); };

const REPORTS = {
  directory: {
    title: 'Employee directory report', filters: ['repDept', 'repLoc', 'repStatus'],
    async load() {
      const f = filters();
      const page = await api.list({ page: 0, size: 100, sortBy: 'fullName', direction: 'asc', ...f });
      const active = Object.entries(f).filter(([, v]) => v).map(([k, v]) => `${k}: ${v}`).join(', ') || 'no filters';
      return { headers: ['Code', 'Name', 'Email', 'Department', 'Designation', 'Location', 'Joined', 'Status'],
        rows: page.content.map((e) => [e.employeeCode, e.fullName, e.email, e.department, e.jobTitle, e.location, formatDate(e.joiningDate), e.employmentStatus]),
        meta: `${page.totalElements} employee(s) match (${active}).${page.totalElements > 100 ? ' Preview shows the first 100; the CSV download contains all of them.' : ''}`,
        serverExport: { ...f, sortBy: 'fullName', direction: 'asc' }, name: 'employee-directory.csv' };
    }
  },
  department: { title: 'Headcount by department', async load() {
    const s = await api.stats();
    return { headers: ['Department', 'Employees', 'Share'], rows: share(s.byDepartment.map((d) => [d.name, d.count])), meta: `${s.totalEmployees} employees in total (all employees, filters do not apply).`, name: 'headcount-by-department.csv' }; } },
  location: { title: 'Headcount by location', async load() {
    const [s, l] = await Promise.all([api.stats(), api.locations()]);
    return { headers: ['Location', 'Employees', 'Share'], rows: share(l.map((d) => [d.name, d.count])), meta: `${s.totalEmployees} employees in total (all employees, filters do not apply).`, name: 'headcount-by-location.csv' }; } },
  status: { title: 'Employment status report', async load() {
    const s = await api.stats();
    const label = { ACTIVE: 'Active', ON_LEAVE: 'On leave', INACTIVE: 'Inactive' };
    return { headers: ['Status', 'Employees', 'Share'], rows: share(Object.entries(s.byStatus).map(([k, v]) => [label[k] || k, v])), meta: `${s.totalEmployees} employees in total.`, name: 'employment-status.csv' }; } },
  trends: { title: 'Employee joining trends', filters: ['repMonths'], async load() {
    const months = Number($('#repMonths').value);
    const points = await api.trends(months);
    const total = points.reduce((s, p) => s + p.count, 0);
    return { headers: ['Month', 'Employees joined'], rows: points.map((p) => [monthLabel(p.month), p.count]), meta: `${total} employee(s) joined in the last ${months} months. Employees without a joining date are not counted.`, name: 'joining-trends.csv' }; } },
  leave: { title: 'Leave summary report', async load() {
    const s = await api.leaveSummary();
    const rows = [...Object.entries(s.byStatus).map(([k, v]) => [`Status: ${k}`, v]), ...Object.entries(s.byType).map(([k, v]) => [`Type: ${k}`, v]), ['Approved leave days (total)', s.approvedDays]];
    return { headers: ['Metric', 'Count'], rows, meta: `${s.total} leave request(s) recorded.`, name: 'leave-summary.csv' }; } }
};

function render() {
  const head = $('#reportTable thead'); const body = $('#reportTable tbody');
  head.innerHTML = `<tr>${data.headers.map((h) => `<th scope="col">${esc(h)}</th>`).join('')}</tr>`;
  body.innerHTML = data.rows.map((r) => `<tr>${r.map((c) => `<td>${esc(c)}</td>`).join('')}</tr>`).join('');
  $('#reportMeta').textContent = data.meta;
  showBox($('#reportState'), data.rows.length ? '' : `${icon('inbox')}<p>No data for this report.</p>`);
}

async function run() {
  const t = ++ticket;
  const def = REPORTS[current];
  $('#reportPanel').hidden = false;
  $('#reportTitle').textContent = def.title;
  $('#reportFilters').hidden = !def.filters;
  ['repDept', 'repLoc', 'repStatus', 'repMonths'].forEach((id) => { $(`#${id}`).closest('div').hidden = !(def.filters || []).includes(id); });
  $('#reportMeta').textContent = 'Loading…';
  $('#reportTable tbody').innerHTML = '<tr aria-hidden="true"><td colspan="8"><div class="skeleton" style="height:34px"></div></td></tr>';
  $('#reportCsv').disabled = true; $('#reportPrint').disabled = true;
  try {
    const result = await def.load();
    if (t !== ticket) return;
    data = result; render();
    $('#reportCsv').disabled = false; $('#reportPrint').disabled = false;
  } catch (error) {
    if (t !== ticket) return;
    data = null; $('#reportTable thead').innerHTML = ''; $('#reportTable tbody').innerHTML = ''; $('#reportMeta').textContent = '';
    showBox($('#reportState'), `${icon('alert')}<p>${esc(error.message)}</p><button class="btn btn-sm btn-outline-secondary" type="button" id="reportRetry">Try again</button>`);
    $('#reportRetry').addEventListener('click', run);
  }
}

export async function loadReports() {
  try {
    const { stats, locations } = await refreshLookups();
    const fill = (id, names) => { const s = $(`#${id}`); const keep = s.value; s.replaceChildren(s.firstElementChild); names.forEach((n) => { const o = document.createElement('option'); o.value = n; o.textContent = n; s.appendChild(o); }); s.value = names.includes(keep) ? keep : ''; };
    fill('repDept', stats.byDepartment.map((d) => d.name).sort((a, b) => a.localeCompare(b)));
    fill('repLoc', locations.map((l) => l.name).sort((a, b) => a.localeCompare(b)));
  } catch { /* the report itself will show the error */ }
}

export function initReports() {
  $$('#reportCards .report-card').forEach((c) => c.addEventListener('click', () => {
    current = c.dataset.report;
    $$('#reportCards .report-card').forEach((x) => x.classList.toggle('is-active', x === c));
    run();
  }));
  ['repDept', 'repLoc', 'repStatus', 'repMonths'].forEach((id) => $(`#${id}`).addEventListener('change', () => current && run()));
  $('#reportPrint').addEventListener('click', () => window.print());
  $('#reportCsv').addEventListener('click', async () => {
    if (!data) return;
    try {
      if (data.serverExport) saveBlob(await api.exportCsv(data.serverExport), data.name);
      else saveBlob(new Blob([toCsv(data.headers, data.rows)], { type: 'text/csv;charset=utf-8' }), data.name);
      toast.success('CSV downloaded.');
    } catch (error) { toast.error(error.message); }
  });
}
