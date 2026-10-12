(() => {
  const $ = (selector, root = document) => root.querySelector(selector);
  const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
  const state = { user: null, doctors: [], selectedDoctor: null };
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  const setStatus = (element, message, kind = '') => {
    if (!element) return;
    element.textContent = message;
    element.className = `status-line ${kind}`.trim();
  };
  const timeText = (value) => String(value || '').slice(0, 5);
  const upcoming = (appointment) => {
    if (appointment.status !== 'BOOKED') return false;
    const time = timeText(appointment.time);
    const target = new Date(`${appointment.date}T${time}:00+05:30`);
    return Number.isFinite(target.getTime()) && target > new Date();
  };

  async function loadDoctors() {
    const grid = $('#doctors');
    try {
      state.doctors = await api.get('/api/doctors');
      if (!state.doctors.length) {
        grid.innerHTML = '<div class="glass empty">No doctors are listed yet.</div>';
        return;
      }
      grid.innerHTML = state.doctors.map((doctor) => `
        <article class="glass doctor-card">
          <span class="pill">${esc(doctor.specialty)}</span>
          <h3>Dr. ${esc(doctor.name)}</h3>
          <p>${esc(doctor.experience)} years of experience · ★ ${esc(doctor.rating)} · ₹${esc(doctor.fee)}</p>
          <div class="card-actions">
            <button class="button secondary" type="button" data-view-doctor="${Number(doctor.id)}">View profile</button>
            ${state.user?.role === 'PATIENT' ? `<button class="button secondary" type="button" data-favorite="${Number(doctor.id)}">Save favorite</button>` : ''}
            ${state.user?.role === 'PATIENT' ? `<a class="button" href="#patient-booking" data-choose-doctor="${Number(doctor.id)}">Book</a>` : ''}
          </div>
        </article>`).join('');
      const select = $('#booking-doctor');
      if (select) select.innerHTML = '<option value="">Choose a doctor</option>' + state.doctors.map((doctor) => `<option value="${Number(doctor.id)}">Dr. ${esc(doctor.name)} · ${esc(doctor.specialty)}</option>`).join('');
    } catch (error) {
      grid.innerHTML = `<div class="glass empty">${esc(error.message || 'Sign in to discover your care team.')}</div>`;
    }
  }

  async function loadAppointments() {
    const container = $('#appointment-list');
    try {
      const appointments = await api.get('/api/appointments');
      const visible = state.user.role === 'DOCTOR' ? appointments.filter(upcoming) : appointments;
      if (!visible.length) {
        container.innerHTML = state.user.role === 'DOCTOR'
          ? '<p class="muted">No upcoming appointments are on your schedule.</p>'
          : '<p class="muted">No appointments yet. Choose a doctor and find a time that works for you.</p>';
        return;
      }
      container.innerHTML = visible.map((appointment) => {
        const person = state.user.role === 'PATIENT' ? `Dr. ${esc(appointment.doctorName)}` : `Patient: ${esc(appointment.patientName)}`;
        const stateClass = esc(String(appointment.status || '').toLowerCase());
        const canChange = state.user.role === 'PATIENT' && upcoming(appointment);
        const canPropose = ['PATIENT', 'DOCTOR'].includes(state.user.role) && upcoming(appointment);
        return `<article class="feature-card">
          <div class="card-actions"><span class="status-badge ${stateClass}">${esc(appointment.status)}</span><small>${esc(appointment.specialty || 'Appointment')}</small></div>
          <h3>${person}</h3><p>${esc(appointment.date)} at ${esc(timeText(appointment.time))} · ${esc(appointment.durationMinutes)} minutes</p>
          ${canChange ? `<form class="request-form" data-reschedule-form="${Number(appointment.id)}"><label>New date<input type="date" data-reschedule-date="${Number(appointment.id)}" min="${today}" required></label><label>New time<input type="time" data-reschedule-time="${Number(appointment.id)}" step="1800" required></label><button class="button secondary" type="submit">Reschedule</button><button class="button secondary" type="button" data-cancel="${Number(appointment.id)}">Cancel booking</button></form>` : ''}
          ${canPropose ? `<form class="request-form" data-proposal-form="${Number(appointment.id)}"><label>Propose date<input type="date" name="date" min="${today}" required></label><label>Propose time<input type="time" name="time" step="1800" required></label><button class="button secondary" type="submit">Request a different time</button></form>` : ''}
          <p class="status-line" data-appointment-message="${Number(appointment.id)}" aria-live="polite"></p>
        </article>`;
      }).join('');
    } catch (error) {
      container.innerHTML = `<p class="muted">${esc(error.message)}</p>`;
    }
  }

  async function loadFavorites() {
    const container = $('#favorite-list');
    try {
      const favorites = await api.get('/api/patient/favorites');
      container.innerHTML = favorites.length ? favorites.map((doctor) => `
        <article class="feature-card"><h3>Dr. ${esc(doctor.name)}</h3><p>${esc(doctor.specialty)} · ${esc(doctor.experience)} years · ${esc(doctor.languages)}</p>
        <p>${esc(doctor.bio)}</p><div class="card-actions"><button class="button secondary" type="button" data-view-doctor="${Number(doctor.id)}">View profile</button><button class="button secondary" type="button" data-unfavorite="${Number(doctor.id)}">Remove favorite</button></div></article>`).join('') : '<p class="muted">No favorites yet. Save a doctor from the directory above.</p>';
    } catch (error) { container.innerHTML = `<p class="muted">${esc(error.message)}</p>`; }
  }

  async function loadTimeRequests() {
    const container = $('#time-request-list');
    try {
      const requests = await api.get('/api/time-requests');
      container.innerHTML = requests.length ? requests.map((request) => {
        const other = state.user.role === 'PATIENT' ? `Dr. ${esc(request.doctorName)}` : `Patient: ${esc(request.patientName)}`;
        const statusClass = esc(String(request.status || '').toLowerCase());
        const outcome = request.status === 'PENDING' ? 'Waiting for the other participant.'
          : request.status === 'ACCEPTED' ? 'Proposal accepted.'
          : request.status === 'DECLINED' ? 'Proposal declined.'
          : `Request status: ${request.status || 'unknown'}.`;
        return `<article class="feature-card"><div class="card-actions"><span class="status-badge ${statusClass}">${esc(request.status)}</span></div>
          <h3>${other}</h3><p>${esc(request.requestedByRole)} proposed ${esc(request.proposedDate)} at ${esc(timeText(request.proposedTime))}</p>
          <small>Current appointment: ${esc(request.appointmentDate)} at ${esc(timeText(request.appointmentTime))}</small>
          ${request.canRespond ? `<div class="card-actions"><button class="button" type="button" data-time-decision="${Number(request.id)}" data-decision="accept">Accept time</button><button class="button secondary" type="button" data-time-decision="${Number(request.id)}" data-decision="decline">Decline</button></div>` : `<p class="muted">${esc(outcome)}</p>`}
          <p class="status-line" data-request-message="${Number(request.id)}" aria-live="polite"></p></article>`;
      }).join('') : '<p class="muted">No time requests yet.</p>';
    } catch (error) { container.innerHTML = `<p class="muted">${esc(error.message)}</p>`; }
  }

  function appointmentStarted(appointment) {
    const start = Date.parse(`${appointment.date}T${timeText(appointment.time)}:00+05:30`);
    return Number.isFinite(start) && start <= Date.now();
  }

  async function loadConsultations() {
    const container = $('#consultation-list');
    try {
      const appointments = await api.get('/api/appointments');
      const visits = appointments.filter((visit) => ['BOOKED', 'COMPLETED'].includes(visit.status));
      if (!visits.length) {
        container.innerHTML = '<p class="muted">No assigned visits are available for consultation entry.</p>';
        return;
      }
      container.innerHTML = visits.map((visit) => {
        const id = Number(visit.id);
        const canComplete = visit.status === 'BOOKED' && appointmentStarted(visit);
        return `<article class="feature-card">
          <div class="card-actions"><span class="status-badge ${esc(String(visit.status).toLowerCase())}">${esc(visit.status)}</span><small>${esc(visit.specialty || 'Appointment')}</small></div>
          <h3>Patient: ${esc(visit.patientName)}</h3>
          <p>${esc(visit.date)} at ${esc(timeText(visit.time))} · ${esc(visit.durationMinutes)} minutes</p>
          ${canComplete ? `<form class="consultation-form" data-consultation-form="${id}">
            <label>Consultation note<textarea name="note" maxlength="4000" required></textarea></label>
            <label>Diagnosis<textarea name="diagnosis" maxlength="1200" required></textarea></label>
            <label>Treatment<textarea name="treatment" maxlength="2000" required></textarea></label>
            <button class="button" type="submit">Complete visit &amp; issue demo bill</button>
            <p class="status-line" data-consultation-message="${id}" aria-live="polite"></p>
          </form>` : `<p class="muted">${visit.status === 'COMPLETED' ? 'This visit is complete; its clinical record and bill are final.' : 'Consultation entry opens after the scheduled appointment time.'}</p>`}
        </article>`;
      }).join('');
    } catch (error) { container.innerHTML = `<p class="muted">${esc(error.message)}</p>`; }
  }

  async function loadPatientHistory() {
    const container = $('#history-list');
    try {
      const history = await api.get('/api/phase3/history');
      if (!history.length) {
        container.innerHTML = '<p class="muted">No completed visits yet. Your history and demo bills will appear here after a doctor records a visit.</p>';
        return;
      }
      container.innerHTML = history.map((visit) => `
        <article class="feature-card">
          <div class="card-actions"><span class="status-badge completed">COMPLETED</span><small>${esc(visit.specialty || 'Consultation')}</small></div>
          <h3>Dr. ${esc(visit.doctorName)}</h3><p>${esc(visit.appointmentDate)} at ${esc(timeText(visit.appointmentTime))}</p>
          <span class="record-label">Diagnosis</span><p class="record-copy">${esc(visit.diagnosis)}</p>
          <span class="record-label">Consultation note</span><p class="record-copy">${esc(visit.note)}</p>
          <span class="record-label">Treatment</span><p class="record-copy">${esc(visit.treatment)}</p>
          <div class="card-actions"><span class="status-badge">${esc(visit.billStatus)}</span><small>${esc(visit.billNumber)} · ${esc(visit.currency)} ${Number(visit.amount || 0).toFixed(2)}</small></div>
          <a class="invoice-action" href="/bills/print.jsp?id=${Number(visit.billId)}">Open printable demo bill</a>
        </article>`).join('');
    } catch (error) { container.innerHTML = `<p class="muted">${esc(error.message)}</p>`; }
  }

  async function loadReports() {
    const container = $('#report-results');
    const from = $('#report-from').value;
    const to = $('#report-to').value;
    if (!from || !to) {
      container.innerHTML = '<p class="muted">Choose both report dates first.</p>';
      return;
    }
    try {
      const report = await api.get(`/api/phase3/reports?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
      const summary = report.summary || {};
      const specialties = report.bySpecialty || [];
      const amount = (value) => `₹${Number(value || 0).toFixed(2)}`;
      container.innerHTML = `
        <p class="muted">Reporting range: ${esc(report.from)} through ${esc(report.to)} · Aggregate counts only.</p>
        <div class="report-metrics">
          <div class="report-metric"><small>Appointments</small><strong>${Number(summary.appointments || 0)}</strong></div>
          <div class="report-metric"><small>Completed consultations</small><strong>${Number(summary.completedConsultations || 0)}</strong></div>
          <div class="report-metric"><small>Issued invoices</small><strong>${Number(summary.issuedInvoices || 0)}</strong></div>
          <div class="report-metric"><small>Issued amount</small><strong>${amount(summary.issuedAmount)}</strong></div>
        </div>
        <h3>By specialty</h3>
        ${specialties.length ? `<div class="table-wrap"><table class="report-table"><thead><tr><th>Specialty</th><th>Appointments</th><th>Consultations</th><th>Invoices</th><th>Amount</th></tr></thead><tbody>${specialties.map((row) => `<tr><td>${esc(row.specialty)}</td><td>${Number(row.appointments || 0)}</td><td>${Number(row.completedConsultations || 0)}</td><td>${Number(row.issuedInvoices || 0)}</td><td>${amount(row.issuedAmount)}</td></tr>`).join('')}</tbody></table></div>` : '<p class="muted">No activity was recorded in this date range.</p>'}`;
    } catch (error) { container.innerHTML = `<p class="status-line error">${esc(error.message)}</p>`; }
  }

  async function loadDoctorWorkspace() {
    try {
      const profile = await api.get('/api/doctor/profile');
      $('#doctor-bio').value = profile.bio || '';
      $('#doctor-languages').value = profile.languages || '';
      const availability = await api.get('/api/doctor/availability');
      $('#availability-list').innerHTML = availability.length ? availability.map((window) => `
        <article class="feature-card"><div class="card-actions"><span class="status-badge">${esc(window.day)}</span><small>${esc(timeText(window.startTime))}–${esc(timeText(window.endTime))} · ${esc(window.slotMinutes)}-minute starts</small>
        <button class="button secondary" type="button" data-remove-availability="${Number(window.weekday)}">Remove</button></div></article>`).join('') : '<p class="muted">No weekly availability is set.</p>';
    } catch (error) { setStatus($('#doctor-profile-message'), error.message, 'error'); }
  }

  async function loadDoctorProfile(doctorId) {
    try {
      const profile = await api.get(`/api/doctors/${Number(doctorId)}`);
      state.selectedDoctor = Number(doctorId);
      $('#detail-name').textContent = `Dr. ${profile.name}`;
      $('#detail-specialty').textContent = `${profile.specialty} · ${profile.experience} years of experience · ₹${profile.fee}`;
      $('#detail-bio').textContent = profile.bio || 'A patient-first clinician with a focus on clear next steps.';
      $('#detail-languages').textContent = `Languages: ${profile.languages || 'Please ask the clinic.'}`;
      $('#detail-date').min = today;
      $('#doctor-detail').classList.remove('hidden');
      $('#doctor-detail').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    } catch (error) { $('#detail-slots').textContent = error.message; }
  }

  async function loadSlots(doctorId, date, target) {
    if (!doctorId || !date) {
      target.textContent = 'Choose a doctor and date first.';
      return [];
    }
    try {
      const slots = await api.get(`/api/doctors/${Number(doctorId)}/slots?date=${encodeURIComponent(date)}`);
      if (!slots.length) {
        target.textContent = 'No available starts on that date. Try another date.';
        return [];
      }
      target.innerHTML = slots.map((slot) => `<span class="slot-pill">${esc(timeText(slot))}</span>`).join('');
      return slots;
    } catch (error) {
      target.textContent = error.message;
      return [];
    }
  }

  async function loadBookingSlots() {
    const select = $('#booking-time');
    select.innerHTML = '<option value="">Checking availability…</option>';
    const slots = await loadSlots($('#booking-doctor').value, $('#booking-date').value, $('#booking-message'));
    select.innerHTML = slots.length ? '<option value="">Choose a start time</option>' + slots.map((slot) => `<option value="${esc(slot)}">${esc(timeText(slot))}</option>`).join('') : '<option value="">No times available</option>';
  }

  async function start() {
    $('#booking-date').min = today;
    $('#detail-date').min = today;
    $('#report-from').value = `${today.slice(0, 7)}-01`;
    $('#report-to').value = today;
    try {
      state.user = await api.get('/api/auth/me');
      $('#welcome').textContent = `Welcome back, ${state.user.name.split(' ')[0]}.`;
      $('#profile').textContent = state.user.name.split(' ').map((part) => part[0]).join('').slice(0, 2);
      $('#logout').classList.remove('hidden');
      $('#auth').classList.add('hidden');
      $('#dashboard').classList.remove('hidden');
      $('#dash-name').textContent = state.user.name;
      $('#dash-role').textContent = state.user.role;
      $('#role-title').textContent = state.user.role === 'PATIENT' ? 'Your care, in sync.' : state.user.role === 'DOCTOR' ? 'Your doctor workspace.' : 'MediSlot administration.';
      $('#role-desc').textContent = state.user.role === 'PATIENT' ? 'Find a doctor, save favorites, and review your visits and demo bills.' : state.user.role === 'DOCTOR' ? 'Manage your profile, view your schedule, and record a visit after its scheduled time.' : 'Review synthetic operational and billing totals without patient-level details.';
      for (const link of document.querySelectorAll('.patient-link')) link.classList.toggle('role-patient', state.user.role === 'PATIENT');
      for (const link of document.querySelectorAll('.doctor-link')) link.classList.toggle('role-doctor', state.user.role === 'DOCTOR');
      document.querySelectorAll('.schedule-link').forEach((link) => link.classList.toggle('role-schedule', ['PATIENT', 'DOCTOR'].includes(state.user.role)));
      document.querySelectorAll('.admin-link').forEach((link) => link.classList.toggle('role-admin', state.user.role === 'ADMIN'));
      $('#appointments').classList.toggle('hidden', !['PATIENT', 'DOCTOR'].includes(state.user.role));
      $('#time-requests').classList.toggle('hidden', !['PATIENT', 'DOCTOR'].includes(state.user.role));
      $('#reports').classList.toggle('hidden', state.user.role !== 'ADMIN');
      $('#appointments-title').textContent = state.user.role === 'DOCTOR' ? 'Your upcoming schedule' : 'Your appointments';
      if (state.user.role === 'PATIENT') {
        document.querySelectorAll('.patient-workspace').forEach((section) => {
          section.classList.add('role-visible');
          section.classList.remove('hidden');
        });
      } else if (state.user.role === 'DOCTOR') {
        document.querySelectorAll('.doctor-workspace').forEach((section) => {
          section.classList.add('role-visible');
          section.classList.remove('hidden');
        });
      }
      $('#logout').onclick = async () => { await api.post('/api/auth/logout'); location.reload(); };
      await loadDoctors();
      if (['PATIENT', 'DOCTOR'].includes(state.user.role)) {
        await loadAppointments();
        await loadTimeRequests();
      }
      if (state.user.role === 'PATIENT') {
        await loadFavorites();
        await loadPatientHistory();
      }
      if (state.user.role === 'DOCTOR') {
        await loadDoctorWorkspace();
        await loadConsultations();
      }
      if (state.user.role === 'ADMIN') await loadReports();
      window.dispatchEvent(new CustomEvent('medislot:ready', { detail: { user: state.user } }));
    } catch (_) {
      $('#doctors').innerHTML = '<div class="glass empty">Sign in to discover your care team.</div>';
    }
  }

  $('#load-slots').addEventListener('click', loadBookingSlots);
  $('#booking-doctor').addEventListener('change', () => { $('#booking-time').innerHTML = '<option value="">Choose a date and load times</option>'; });
  $('#booking-date').addEventListener('change', () => { $('#booking-time').innerHTML = '<option value="">Load available times</option>'; });
  $('#booking-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const result = await api.post('/api/appointments', { doctorId: Number($('#booking-doctor').value), date: $('#booking-date').value, time: $('#booking-time').value });
      setStatus($('#booking-message'), `Booked for ${result.date} at ${timeText(result.time)}.`, 'ok');
      await loadAppointments();
      await loadBookingSlots();
    } catch (error) { setStatus($('#booking-message'), error.message, error.status === 409 ? 'error' : 'error'); }
  });
  $('#detail-slots-button').addEventListener('click', async () => {
    const slots = await loadSlots(state.selectedDoctor, $('#detail-date').value, $('#detail-slots'));
    if (slots.length && state.user?.role === 'PATIENT') {
      $('#booking-doctor').value = String(state.selectedDoctor);
      $('#booking-date').value = $('#detail-date').value;
      await loadBookingSlots();
      document.querySelector('#patient-booking').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
  });
  $('#doctor-profile-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      await api.put('/api/doctor/profile', { bio: $('#doctor-bio').value, languages: $('#doctor-languages').value });
      setStatus($('#doctor-profile-message'), 'Profile saved.', 'ok');
    } catch (error) { setStatus($('#doctor-profile-message'), error.message, 'error'); }
  });
  $('#availability-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const day = $('#availability-weekday').value;
    try {
      await api.put(`/api/doctor/availability/${day}`, { startTime: $('#availability-start').value, endTime: $('#availability-end').value });
      setStatus($('#availability-message'), 'Availability saved.', 'ok');
      await loadDoctorWorkspace();
    } catch (error) { setStatus($('#availability-message'), error.message, 'error'); }
  });
  document.addEventListener('click', async (event) => {
    const view = event.target.closest('[data-view-doctor]');
    const favorite = event.target.closest('[data-favorite]');
    const unfavorite = event.target.closest('[data-unfavorite]');
    const choose = event.target.closest('[data-choose-doctor]');
    const cancel = event.target.closest('[data-cancel]');
    const reschedule = event.target.closest('[data-reschedule]');
    const removeAvailability = event.target.closest('[data-remove-availability]');
    const decision = event.target.closest('[data-time-decision]');
    if (view) await loadDoctorProfile(view.dataset.viewDoctor);
    if (favorite) {
      try { await api.post(`/api/patient/favorites/${Number(favorite.dataset.favorite)}`); await loadFavorites(); }
      catch (error) { setStatus($('#booking-message'), error.message, 'error'); }
    }
    if (unfavorite) {
      try { await api.delete(`/api/patient/favorites/${Number(unfavorite.dataset.unfavorite)}`); await loadFavorites(); }
      catch (error) { setStatus($('#booking-message'), error.message, 'error'); }
    }
    if (choose) $('#booking-doctor').value = choose.dataset.chooseDoctor;
    if (cancel) {
      const id = Number(cancel.dataset.cancel);
      try { await api.delete(`/api/appointments/${id}`); await loadAppointments(); }
      catch (error) { setStatus($(`[data-appointment-message="${id}"]`), error.message, 'error'); }
    }
    if (removeAvailability) {
      const day = Number(removeAvailability.dataset.removeAvailability);
      try { await api.delete(`/api/doctor/availability/${day}`); await loadDoctorWorkspace(); }
      catch (error) { setStatus($('#availability-message'), error.message, 'error'); }
    }
    if (decision) {
      const id = Number(decision.dataset.timeDecision);
      try {
        await api.post(`/api/time-requests/${id}/decision`, { decision: decision.dataset.decision });
        await loadTimeRequests();
        await loadAppointments();
      } catch (error) { setStatus($(`[data-request-message="${id}"]`), error.message, 'error'); await loadTimeRequests(); }
    }
  });

  document.addEventListener('submit', async (event) => {
    const proposalForm = event.target.closest('[data-proposal-form]');
    const rescheduleForm = event.target.closest('[data-reschedule-form]');
    const consultationForm = event.target.closest('[data-consultation-form]');
    const reportForm = event.target.closest('#report-form');
    if (proposalForm) {
      event.preventDefault();
      const id = Number(proposalForm.dataset.proposalForm);
      const form = new FormData(proposalForm);
      try {
        await api.post(`/api/appointments/${id}/time-requests`, { date: form.get('date'), time: form.get('time') });
        setStatus($(`[data-appointment-message="${id}"]`), 'Time request sent to the other participant.', 'ok');
        await loadTimeRequests();
      } catch (error) { setStatus($(`[data-appointment-message="${id}"]`), error.message, 'error'); }
    }
    if (rescheduleForm) {
      event.preventDefault();
      const id = Number(rescheduleForm.dataset.rescheduleForm);
      try {
        await api.post(`/api/appointments/${id}/reschedule`, {
          date: $(`[data-reschedule-date="${id}"]`).value,
          time: $(`[data-reschedule-time="${id}"]`).value,
        });
        setStatus($(`[data-appointment-message="${id}"]`), 'Appointment time updated.', 'ok');
        await loadAppointments();
      } catch (error) { setStatus($(`[data-appointment-message="${id}"]`), error.message, 'error'); }
    }
    if (consultationForm) {
      event.preventDefault();
      const id = Number(consultationForm.dataset.consultationForm);
      const fields = new FormData(consultationForm);
      try {
        const result = await api.post('/api/phase3/consultations', {
          appointmentId: id,
          note: fields.get('note'),
          diagnosis: fields.get('diagnosis'),
          treatment: fields.get('treatment'),
        });
        await loadAppointments();
        await loadConsultations();
        const notice = document.createElement('p');
        notice.className = 'status-line ok';
        notice.textContent = `Visit complete. Demo bill ${result.billNumber} is ready for the patient.`;
        $('#consultation-list').prepend(notice);
      } catch (error) { setStatus($(`[data-consultation-message="${id}"]`), error.message, 'error'); }
    }
    if (reportForm) {
      event.preventDefault();
      await loadReports();
    }
  });

  start();
})();
