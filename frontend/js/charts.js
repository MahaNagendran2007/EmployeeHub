// Chart.js wrappers. All data passed in comes from the backend; nothing is hard-coded.
export const PALETTE = { indigo: '#6366f1', sky: '#0ea5e9', green: '#10b981', amber: '#f59e0b', slate: '#94a3b8' };
export const chartsAvailable = () => typeof window.Chart === 'function';
const charts = {};

// Draws the numeric value at the end of every bar (Chart.js has no built-in data labels).
const valueLabels = {
  id: 'valueLabels',
  afterDatasetsDraw(chart) {
    const { ctx } = chart;
    const horizontal = chart.options.indexAxis === 'y';
    ctx.save();
    ctx.font = `700 12px ${Chart.defaults.font.family}`;
    ctx.fillStyle = '#11162a';
    chart.getDatasetMeta(0).data.forEach((bar, i) => {
      const value = chart.data.datasets[0].data[i];
      if (horizontal) { ctx.textAlign = 'left'; ctx.textBaseline = 'middle'; ctx.fillText(value, bar.x + 8, bar.y); }
      else { ctx.textAlign = 'center'; ctx.textBaseline = 'bottom'; ctx.fillText(value, bar.x, bar.y - 6); }
    });
    ctx.restore();
  }
};

function mount(key, canvas, config) {
  if (charts[key]) charts[key].destroy();
  Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
  Chart.defaults.font.size = 12;
  Chart.defaults.color = '#5d6478';
  charts[key] = new Chart(canvas, config);
}
const shorten = (label) => (String(label).length > 20 ? `${String(label).slice(0, 19)}…` : label);

/** items: [{name, count}] sorted by count desc. Shows the top `limit`. Returns {shown, total}. */
export function barChart(key, canvas, items, { color, horizontal = false, limit = 8 } = {}) {
  const shown = items.slice(0, limit);
  mount(key, canvas, {
    type: 'bar',
    plugins: [valueLabels],
    data: { labels: shown.map((i) => i.name), datasets: [{ label: 'Employees', data: shown.map((i) => i.count),
      backgroundColor: color, hoverBackgroundColor: color, borderRadius: 8, borderSkipped: false, maxBarThickness: horizontal ? 24 : 48 }] },
    options: {
      indexAxis: horizontal ? 'y' : 'x', responsive: true, maintainAspectRatio: false,
      layout: { padding: horizontal ? { right: 32 } : { top: 22 } },
      plugins: { legend: { display: false },
        tooltip: { backgroundColor: '#11162a', padding: 10, cornerRadius: 8, displayColors: false,
          callbacks: { title: (t) => t[0].label, label: (c) => `${c.parsed[horizontal ? 'x' : 'y']} employee${c.parsed[horizontal ? 'x' : 'y'] === 1 ? '' : 's'}` } } },
      scales: {
        [horizontal ? 'x' : 'y']: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: '#eeece7' }, border: { display: false } },
        [horizontal ? 'y' : 'x']: { grid: { display: false }, border: { display: false }, ticks: { callback(v) { return shorten(this.getLabelForValue(v)); } } }
      }
    }
  });
  return { shown: shown.length, total: items.length };
}

export function statusDoughnut(key, canvas, rows) {   // rows: [{label, value, color}]
  mount(key, canvas, {
    type: 'doughnut',
    data: { labels: rows.map((r) => r.label), datasets: [{ data: rows.map((r) => r.value), backgroundColor: rows.map((r) => r.color),
      borderWidth: 3, borderColor: '#fff', hoverOffset: 4 }] },
    options: { responsive: true, maintainAspectRatio: false, cutout: '72%',
      plugins: { legend: { display: false }, tooltip: { backgroundColor: '#11162a', padding: 10, cornerRadius: 8 } } }
  });
}

/** Line chart for a monthly series: items = [{label, value}]. */
export function lineChart(key, canvas, items, { color = PALETTE.indigo } = {}) {
  mount(key, canvas, {
    type: 'line',
    data: { labels: items.map((i) => i.label), datasets: [{ label: 'Employees joined', data: items.map((i) => i.value),
      borderColor: color, backgroundColor: 'rgba(99, 102, 241, .12)', fill: true, tension: 0.35, pointRadius: 4, pointBackgroundColor: '#fff',
      pointBorderColor: color, pointBorderWidth: 2 }] },
    options: { responsive: true, maintainAspectRatio: false, layout: { padding: { top: 8, right: 8 } },
      plugins: { legend: { display: false }, tooltip: { backgroundColor: '#11162a', padding: 10, cornerRadius: 8, displayColors: false,
        callbacks: { label: (c) => `${c.parsed.y} joined` } } },
      scales: { y: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: '#eeece7' }, border: { display: false } },
        x: { grid: { display: false }, border: { display: false } } } }
  });
}
