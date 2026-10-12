window.api = {
  async request(path, options = {}) {
    const response = await fetch(path, {
      credentials: 'same-origin',
      ...options,
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    });
    const data = await response.json().catch(() => ({ error: 'Unexpected server response.' }));
    if (!response.ok) {
      const error = new Error(data.error || 'Request failed. Please try again.');
      error.status = response.status;
      error.code = data.code || 'REQUEST_FAILED';
      throw error;
    }
    return data;
  },
  get(path) { return this.request(path); },
  post(path, data = {}) { return this.request(path, { method: 'POST', body: JSON.stringify(data) }); },
  put(path, data = {}) { return this.request(path, { method: 'PUT', body: JSON.stringify(data) }); },
  delete(path) { return this.request(path, { method: 'DELETE' }); },
};
