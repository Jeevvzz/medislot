(() => {
  const $ = (selector) => document.querySelector(selector);
  const launcher = $('#assistant-launcher');
  const panel = $('#assistant-panel');
  const messages = $('#assistant-messages');
  const input = $('#assistant-chat-input');
  const form = $('#assistant-chat-form');
  const status = $('#assistant-chat-status');
  if (!launcher || !panel || !messages || !form) return;

  const routes = {
    appointments: ['Appointments', '#appointments'],
    favorites: ['Favorites', '#favorites'],
    pharmacy: ['Pharmacy', '#pharmacy'],
    'time-requests': ['Time requests', '#time-requests'],
    history: ['History & bills', '#history'],
    doctors: ['Doctors', '#doctors'],
    'care-tools': ['Care tools', '#care-tools'],
    schedule: ['My schedule', '#appointments'],
    consultations: ['Consultations', '#consultations'],
    'doctor-profile': ['My profile', '#doctor-profile'],
    reports: ['Administration', '#reports'],
  };
  let busy = false;

  function addMessage(text, kind = '') {
    const bubble = document.createElement('div');
    bubble.className = `assistant-message ${kind}`.trim();
    bubble.textContent = text;
    messages.append(bubble);
    messages.scrollTop = messages.scrollHeight;
    return bubble;
  }

  function addNavigation(bubble, links) {
    const valid = Array.isArray(links) ? links.filter((route) => Object.hasOwn(routes, route)) : [];
    if (!valid.length) return;
    const container = document.createElement('div');
    container.className = 'assistant-links';
    for (const route of valid) {
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = routes[route][0];
      button.dataset.assistantGo = routes[route][1];
      container.append(button);
    }
    bubble.append(container);
  }

  function open() {
    panel.hidden = false;
    launcher.setAttribute('aria-expanded', 'true');
    input.focus();
  }
  function close() {
    panel.hidden = true;
    launcher.setAttribute('aria-expanded', 'false');
    launcher.focus();
  }
  async function sendQuestion(question) {
    const text = String(question || '').trim();
    if (!text || busy) return;
    busy = true;
    $('#assistant-context-consent').disabled = true;
    input.disabled = true;
    addMessage(text, 'user');
    const waiting = addMessage('Checking your MediSlot options…');
    status.textContent = 'Assistant is responding.';
    try {
      const response = await window.api.post('/api/phase45/assistant', {
        question: text,
        includeContext: $('#assistant-context-consent').checked,
      });
      waiting.remove();
      const bubble = addMessage(response.answer || 'I could not prepare a response just now.');
      if (response.summary && Object.keys(response.summary).length) {
        const counts = [];
        if (response.summary.upcomingAppointments !== undefined) counts.push(`${response.summary.upcomingAppointments} upcoming appointment(s)`);
        if (response.summary.nextAppointment?.date) counts.push(`next: ${response.summary.nextAppointment.date} at ${response.summary.nextAppointment.time}`);
        if (response.summary.pendingTimeRequests !== undefined) counts.push(`${response.summary.pendingTimeRequests} pending time request(s)`);
        if (response.summary.favoriteDoctors !== undefined) counts.push(`${response.summary.favoriteDoctors} saved doctor(s)`);
        if (response.summary.pharmacyOrders !== undefined) counts.push(`${response.summary.pharmacyOrders} mock order(s)`);
        if (response.summary.unreadReminders !== undefined) counts.push(`${response.summary.unreadReminders} unread reminder(s)`);
        if (counts.length) addMessage(`Included account summary: ${counts.join(' · ')}.`, 'notice');
      }
      addNavigation(bubble, response.navigation);
      addMessage(`${response.provider === 'Groq' ? 'Groq assistant' : 'Offline assistant'} · ${response.disclaimer || 'Not medical advice.'}`, 'notice');
      status.textContent = 'Assistant response received.';
    } catch (error) {
      waiting.remove();
      addMessage(`${error.message || 'The assistant is unavailable right now.'} You can still use the MediSlot navigation links.`, 'notice');
      status.textContent = 'Assistant request failed.';
    } finally {
      busy = false;
      input.disabled = false;
      $('#assistant-context-consent').disabled = false;
      input.value = '';
      input.focus();
    }
  }

  launcher.addEventListener('click', () => panel.hidden ? open() : close());
  $('#assistant-close').addEventListener('click', close);
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    sendQuestion(input.value);
  });
  document.querySelectorAll('[data-assistant-prompt]').forEach((button) => button.addEventListener('click', () => {
    open();
    sendQuestion(button.dataset.assistantPrompt);
  }));
  messages.addEventListener('click', (event) => {
    const button = event.target.closest('[data-assistant-go]');
    if (!button) return;
    const anchor = document.querySelector(`.sidebar a[href="${button.dataset.assistantGo}"]`);
    const target = document.querySelector(button.dataset.assistantGo);
    if (anchor && target && !target.classList.contains('hidden')) {
      location.hash = button.dataset.assistantGo;
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      panel.hidden = true;
      launcher.setAttribute('aria-expanded', 'false');
    }
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !panel.hidden) close();
  });

  window.addEventListener('medislot:ready', (event) => {
    if (!event.detail?.user) return;
    launcher.hidden = false;
  });
})();
