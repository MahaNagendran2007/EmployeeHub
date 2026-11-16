// Add/edit form, details view and delete confirmation.
import { api, ApiError } from './api.js';
import { $, todayLocalISO, toast, modal } from './ui.js';

let onChange = () => {};
let onDeleted = null;
export const setOnChange = (fn) => { onChange = fn; };
export const setOnDeleted = (fn) => { onDeleted = fn; };

// ---------------- Form validation (mirrors EmployeeRequest on the backend) ----------------
const RULES = {
  employeeCode: { label: 'Employee code', required: true, pattern: /^[A-Za-z0-9-]{2,20}$/, patternMsg: 'Use 2–20 letters, digits or hyphens (e.g. EMP-1001).' },
  fullName: { label: 'Full name', required: true, max: 100 },
  email: { label: 'Email', required: true, max: 254, pattern: /^[^@\s]+@[^@\s]+\.[^@\s]+$/, patternMsg: 'Enter a valid email address.' },
  phone: { label: 'Phone', pattern: /^$|^[+0-9()\-\s]{7,20}$/, patternMsg: 'Use 7–20 digits, spaces, +, - or parentheses.' },
  department: { label: 'Department', required: true, max: 100 },
  jobTitle: { label: 'Job title', required: true, max: 100 },
  location: { label: 'Location', required: true, max: 100 },
  joiningDate: { label: 'Joining date', required: true },
  employmentStatus: { label: 'Status', required: true },
  reportingManager: { label: 'Reporting manager', max: 100 },
  emergencyContactName: { label: 'Emergency contact', max: 100 },
  emergencyContactPhone: { label: 'Emergency phone', pattern: /^$|^[+0-9()\-\s]{7,20}$/, patternMsg: 'Use 7–20 digits, spaces, +, - or parentheses.' }
};
const FIELDS = Object.keys(RULES);

const form = $('#employeeForm');
let editingId = null;
let initialValues = {};
let submitting = false;

function validate(name, value) {
  const rule = RULES[name];
  const v = value.trim();
  if (rule.required && !v) return `${rule.label} is required.`;
  if (rule.max && v.length > rule.max) return `${rule.label} must be at most ${rule.max} characters.`;
  if (rule.pattern && !rule.pattern.test(v)) return rule.patternMsg;
  return '';
}
function showError(name, message) {
  const input = form.elements[name];
  const out = document.getElementById(`err-${name}`);
  input.classList.toggle('is-invalid', Boolean(message));
  input.setAttribute('aria-invalid', message ? 'true' : 'false');
  out.textContent = message || '';
}
function clearErrors() {
  FIELDS.forEach((n) => showError(n, ''));
  $('#formError').classList.add('d-none');
}
function validateAll() {
  let firstInvalid = null;
  FIELDS.forEach((name) => {
    const message = validate(name, form.elements[name].value);
    showError(name, message);
    if (message && !firstInvalid) firstInvalid = form.elements[name];
  });
  return firstInvalid;
}
function setValues(values) { FIELDS.forEach((n) => { form.elements[n].value = values[n] ?? ''; }); }

FIELDS.forEach((name) => {
  const input = form.elements[name];
  input.addEventListener('blur', () => { if (input.value.trim() || RULES[name].required) showError(name, validate(name, input.value)); });
  input.addEventListener('input', () => { if (input.classList.contains('is-invalid')) showError(name, validate(name, input.value)); });
});

function setSubmitting(value) {
  submitting = value;
  $('#saveBtn').disabled = value;
  $('#resetFormBtn').disabled = value;
  $('#cancelFormBtn').disabled = value;
  $('#saveSpinner').classList.toggle('d-none', !value);
  $('#saveLabel').textContent = value ? 'Saving…' : (editingId ? 'Save changes' : 'Create employee');
}
// Do not let the modal close mid-request.
$('#employeeModal').addEventListener('hide.bs.modal', (e) => { if (submitting) e.preventDefault(); });

$('#employeeModal').addEventListener('shown.bs.modal', () => form.elements.employeeCode.focus());



export function openCreate() {
  editingId = null;
  initialValues = { employmentStatus: 'ACTIVE', joiningDate: todayLocalISO() };
  openForm('Add employee', 'Fields marked * are required.');
}
export async function openEdit(id) {
  try {
    const e = await api.get(id);
    editingId = e.id;
    initialValues = { ...e, phone: e.phone || '', reportingManager: e.reportingManager || '', emergencyContactName: e.emergencyContactName || '', emergencyContactPhone: e.emergencyContactPhone || '' };
    openForm('Edit employee', `Editing ${e.fullName} (${e.employeeCode}).`);
  } catch (error) {
    toast.error(error.message);
  }
}
function openForm(title, subtitle) {
  clearErrors();
  setValues(initialValues);
  $('#employeeModalTitle').textContent = title;
  $('#employeeModalSub').textContent = subtitle;
  setSubmitting(false);
  modal('employeeModal').show();
}

$('#resetFormBtn').addEventListener('click', () => { clearErrors(); setValues(initialValues); form.elements.employeeCode.focus(); });

function showFormAlert(message) {
  const box = $('#formError');
  box.textContent = message;
  box.classList.remove('d-none');
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (submitting) return;                       // blocks double-clicks and Enter spam
  $('#formError').classList.add('d-none');
  const firstInvalid = validateAll();
  if (firstInvalid) { firstInvalid.focus(); toast.warning('Please fix the highlighted fields.'); return; }

  const payload = {};
  FIELDS.forEach((n) => { payload[n] = form.elements[n].value.trim(); });
  ['phone', 'reportingManager', 'emergencyContactName', 'emergencyContactPhone'].forEach((k) => { payload[k] = payload[k] || null; });

  const wasEditing = editingId !== null;
  setSubmitting(true);
  try {
    if (wasEditing) await api.update(editingId, payload); else await api.create(payload);
    setSubmitting(false);
    modal('employeeModal').hide();
    toast.success(wasEditing ? 'Employee updated successfully.' : 'Employee created successfully.');   // only after the server confirmed
    onChange();
  } catch (error) {
    setSubmitting(false);
    if (error.status === 0) { showFormAlert(error.message); toast.error(error.message); return; }
    (error.fieldErrors || []).forEach(({ field, message }) => { if (RULES[field]) showError(field, message); });
    showFormAlert(error.fieldErrors?.length ? 'Please correct the highlighted fields.' : error.message);
    const firstInvalid = form.querySelector('.is-invalid');
    if (firstInvalid) firstInvalid.focus();
    toast.error(error.status === 409 ? 'That employee code or email is already in use.' : error.message);
  }
});

// ---------------- Profile navigation ----------------
/** Employee details now live on a full profile page (#profile/<id>). */
export function openDetails(id) { location.hash = `#profile/${encodeURIComponent(id)}`; }

// ---------------- Delete ----------------
let deleting = null;
let deleteBusy = false;
export function confirmDelete(employee) {
  deleting = employee;
  $('#deleteName').textContent = employee.fullName;
  $('#deleteCode').textContent = employee.employeeCode;
  modal('deleteModal').show();
}
document.getElementById('deleteModal').addEventListener('hide.bs.modal', (e) => { if (deleteBusy) e.preventDefault(); });
$('#confirmDeleteBtn').addEventListener('click', async () => {
  if (deleteBusy || !deleting) return;
  deleteBusy = true;
  $('#confirmDeleteBtn').disabled = true;
  $('#cancelDeleteBtn').disabled = true;
  $('#deleteSpinner').classList.remove('d-none');
  const target = deleting;
  let outcome = null;
  try {
    await api.remove(target.id);
    outcome = 'deleted';
  } catch (error) {
    outcome = error instanceof ApiError && error.status === 404 ? 'missing' : error;
  } finally {
    deleteBusy = false;
    $('#confirmDeleteBtn').disabled = false;
    $('#cancelDeleteBtn').disabled = false;
    $('#deleteSpinner').classList.add('d-none');
  }
  modal('deleteModal').hide();
  if (outcome === 'deleted') { toast.success(`${target.fullName} was deleted.`); (onDeleted || onChange)(target); }
  else if (outcome === 'missing') { toast.warning('That employee no longer exists.'); onChange(); }
  else toast.error(outcome.message);
});
