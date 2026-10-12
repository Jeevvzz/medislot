(() => {
  const key = 'medislot-theme';
  let storage;
  try { storage = window.localStorage; } catch (_) { storage = null; }
  let saved = null;
  try { saved = storage?.getItem(key) || null; } catch (_) { saved = null; }
  const initial = saved === 'dark' || saved === 'light'
    ? saved
    : (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  document.documentElement.dataset.theme = initial;

  const updateButtons = (theme) => document.querySelectorAll('[data-theme-toggle]').forEach((button) => {
    const dark = theme === 'dark';
    button.setAttribute('aria-pressed', String(dark));
    button.setAttribute('aria-label', `Switch to ${dark ? 'light' : 'dark'} theme`);
    const label = button.querySelector('[data-theme-label]');
    if (label) label.textContent = dark ? 'Light mode' : 'Dark mode';
    const icon = button.querySelector('[data-theme-icon]');
    if (icon) icon.textContent = dark ? '☼' : '◐';
  });

  document.addEventListener('DOMContentLoaded', () => {
    updateButtons(document.documentElement.dataset.theme);
    document.querySelectorAll('[data-theme-toggle]').forEach((button) => button.addEventListener('click', () => {
      const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.dataset.theme = next;
      try { storage?.setItem(key, next); } catch (_) { /* Theme still changes for this page. */ }
      updateButtons(next);
    }));
  });
})();
