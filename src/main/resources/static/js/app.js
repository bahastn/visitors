/* Shared helpers for all pages */
(function () {
  window.csrfHeaders = function () {
    const t = document.querySelector('meta[name="_csrf"]');
    const h = document.querySelector('meta[name="_csrf_header"]');
    const headers = {};
    if (t && h && t.content && h.content) headers[h.content] = t.content;
    return headers;
  };

  /** Small toast message (used by pages that work with fetch). */
  window.toast = function (text, kind) {
    let stack = document.querySelector('.toast-stack');
    if (!stack) { stack = document.createElement('div'); stack.className = 'toast-stack'; document.body.appendChild(stack); }
    const el = document.createElement('div');
    el.className = 'alert alert-' + (kind || 'success');
    el.setAttribute('role', 'status');
    el.textContent = text;
    stack.appendChild(el);
    setTimeout(function () { el.remove(); }, 5000);
  };

  // htmx requests carry the CSRF header too
  document.addEventListener('htmx:configRequest', function (e) {
    const h = window.csrfHeaders();
    Object.keys(h).forEach(function (k) { e.detail.headers[k] = h[k]; });
  });

  /* ---------------- Awesome bar (PLAN-v3 3.4) ---------------- */
  function initAwesomeBar() {
    const input = document.getElementById('globalSearch');
    if (!input) return;
    const box = document.getElementById(input.getAttribute('aria-controls') || 'o-search-results');
    if (!box) return;
    const form = input.closest('form');
    const api = input.dataset.api || '/api/search';
    const searchUrl = input.dataset.searchUrl || '/search';
    let timer = null;
    let seq = 0;          // ignore out-of-order responses
    let lastJson = null;
    let lastJsonFor = null; // query the lastJson answers

    function options() { return Array.prototype.slice.call(box.querySelectorAll('[role="option"]')); }
    function activeIndex() { return options().findIndex(function (o) { return o.classList.contains('active'); }); }

    function open() {
      if (!box.firstChild) return;
      box.hidden = false;
      input.setAttribute('aria-expanded', 'true');
    }
    function close() {
      box.hidden = true;
      input.setAttribute('aria-expanded', 'false');
      input.removeAttribute('aria-activedescendant');
      options().forEach(function (o) { o.classList.remove('active'); o.setAttribute('aria-selected', 'false'); });
    }
    function setActive(i) {
      const opts = options();
      opts.forEach(function (o, j) {
        const on = j === i;
        o.classList.toggle('active', on);
        o.setAttribute('aria-selected', on ? 'true' : 'false');
        if (on) { input.setAttribute('aria-activedescendant', o.id); o.scrollIntoView({ block: 'nearest' }); }
      });
      if (i < 0) input.removeAttribute('aria-activedescendant');
    }
    function fullSearchUrl(q) { return searchUrl + '?q=' + encodeURIComponent(q); }
    /** Only same-origin relative URLs from the server are followed (no javascript:, no other hosts). */
    function safeUrl(u) {
      if (typeof u !== 'string' || !u) return null;
      try {
        const url = new URL(u, window.location.origin);
        return url.origin === window.location.origin ? url.pathname + url.search + url.hash : null;
      } catch (e) { return null; }
    }

    function item(it, id) {
      const href = safeUrl(it.url);
      if (!href) return null;
      const a = document.createElement('a');
      a.className = 'o-search-item';
      a.id = id;
      a.href = href;
      a.setAttribute('role', 'option');
      a.setAttribute('aria-selected', 'false');
      a.tabIndex = -1;
      const i = document.createElement('i');
      const icon = typeof it.icon === 'string' && /^bi-[a-z0-9-]+$/.test(it.icon) ? it.icon : 'bi-arrow-return-left';
      i.className = 'bi ' + icon;
      i.setAttribute('aria-hidden', 'true');
      a.appendChild(i);
      const label = document.createElement('span');
      label.className = 'o-search-label';
      label.textContent = it.label == null ? '' : String(it.label);
      a.appendChild(label);
      if (it.sub) {
        const sub = document.createElement('span');
        sub.className = 'o-search-sub';
        sub.textContent = String(it.sub);
        a.appendChild(sub);
      }
      return a;
    }

    function render(json, q) {
      while (box.firstChild) box.removeChild(box.firstChild);
      let n = 0;
      (json && Array.isArray(json.groups) ? json.groups : []).forEach(function (g) {
        const items = Array.isArray(g.items) ? g.items : [];
        if (!items.length) return;
        const group = document.createElement('div');
        group.className = 'o-search-group';
        group.setAttribute('role', 'group');
        const title = document.createElement('div');
        title.className = 'o-search-group-title';
        title.id = 'o-sg-' + n;
        title.textContent = g.label == null ? '' : String(g.label);
        group.setAttribute('aria-labelledby', title.id);
        group.appendChild(title);
        items.forEach(function (it) {
          const a = item(it, 'o-so-' + (n++));
          if (a) group.appendChild(a);
        });
        box.appendChild(group);
      });
      // Last row: full search page
      const all = item({ label: (input.dataset.seeAll || 'Search') + ': ' + q, url: fullSearchUrl(q), icon: 'bi-search' }, 'o-so-' + n);
      if (n === 0 && input.dataset.empty) {
        const empty = document.createElement('div');
        empty.className = 'o-search-group-title';
        empty.textContent = input.dataset.empty;
        box.appendChild(empty);
      }
      const g2 = document.createElement('div');
      g2.className = 'o-search-group';
      g2.appendChild(all);
      box.appendChild(g2);
      open();
    }

    function query() {
      const q = input.value.trim();
      if (q.length < 2) { lastJson = null; lastJsonFor = null; close(); while (box.firstChild) box.removeChild(box.firstChild); return; }
      const my = ++seq;
      fetch(api + '?q=' + encodeURIComponent(q), { credentials: 'same-origin', headers: { Accept: 'application/json' } })
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (json) {
          if (my !== seq) return;
          lastJson = json;
          lastJsonFor = q;
          render(json, q);
        })
        .catch(function () { if (my === seq) { lastJson = null; lastJsonFor = q; render(null, q); } });
    }

    input.addEventListener('input', function () {
      clearTimeout(timer);
      timer = setTimeout(query, 250);
    });
    input.addEventListener('focus', function () { if (input.value.trim().length >= 2 && box.firstChild) open(); });
    input.addEventListener('keydown', function (e) {
      const opts = options();
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        if (box.hidden) { open(); }
        if (!opts.length) return;
        e.preventDefault();
        let i = activeIndex();
        i = e.key === 'ArrowDown' ? (i + 1) % opts.length : (i <= 0 ? opts.length - 1 : i - 1);
        setActive(i);
      } else if (e.key === 'Enter') {
        e.preventDefault();
        const q = input.value.trim();
        const i = box.hidden ? -1 : activeIndex();
        if (i >= 0) { window.location.href = opts[i].href; return; }
        // Typed quickly (debounce not fired / answer not back yet): ask for "goto" now
        if (q && q !== lastJsonFor) {
          clearTimeout(timer);
          seq++;
          fetch(api + '?q=' + encodeURIComponent(q), { credentials: 'same-origin', headers: { Accept: 'application/json' } })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (json) { window.location.href = (json && safeUrl(json.goto)) || fullSearchUrl(q); })
            .catch(function () { window.location.href = fullSearchUrl(q); });
          return;
        }
        const go = lastJson && safeUrl(lastJson.goto);
        if (go) { window.location.href = go; return; }
        if (q) window.location.href = fullSearchUrl(q);
      } else if (e.key === 'Escape') {
        if (!box.hidden) { e.preventDefault(); close(); } else { input.blur(); }
      }
    });
    // Mouse: hovering moves the highlight; clicking a link navigates normally
    box.addEventListener('mousemove', function (e) {
      const o = e.target.closest('[role="option"]');
      if (o) setActive(options().indexOf(o));
    });
    box.addEventListener('mousedown', function (e) { e.preventDefault(); }); // keep focus in the input
    document.addEventListener('click', function (e) { if (!e.target.closest('.o-search')) close(); });
    if (form) form.addEventListener('submit', function (e) { if (!input.value.trim()) e.preventDefault(); });

    // "/" and Ctrl+K / Cmd+K focus the search
    document.addEventListener('keydown', function (e) {
      const t = e.target;
      const typing = t && (t.isContentEditable || (t.closest && t.closest('input,textarea,select,[contenteditable=""],[contenteditable="true"]')));
      if ((e.key === 'k' || e.key === 'K') && (e.ctrlKey || e.metaKey) && !e.altKey) {
        e.preventDefault(); input.focus(); input.select(); return;
      }
      if (e.key === '/' && !typing && !e.ctrlKey && !e.metaKey && !e.altKey) {
        e.preventDefault(); input.focus(); input.select();
      }
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    initAwesomeBar();
    // Toasts from the server disappear after a while (errors stay a bit longer)
    document.querySelectorAll('.toast-stack .alert').forEach(function (el) {
      setTimeout(function () { el.remove(); }, el.classList.contains('alert-danger') ? 9000 : 5000);
    });
    // Confirmation for dangerous actions: <form data-confirm="...">
    document.querySelectorAll('form[data-confirm]').forEach(function (f) {
      f.addEventListener('submit', function (e) { if (!window.confirm(f.dataset.confirm)) e.preventDefault(); });
    });
    document.querySelectorAll('button[data-confirm]').forEach(function (b) {
      b.addEventListener('click', function (e) { if (!window.confirm(b.dataset.confirm)) e.preventDefault(); });
    });
    // Clickable table rows
    document.querySelectorAll('tr[data-href]').forEach(function (tr) {
      tr.addEventListener('click', function (e) {
        if (e.target.closest('a,button,input,label,select,form,textarea')) return;
        window.location = tr.dataset.href;
      });
    });
    // "Select all" checkboxes
    document.querySelectorAll('[data-check-all]').forEach(function (cb) {
      cb.addEventListener('change', function () {
        document.querySelectorAll('input[name="' + cb.dataset.checkAll + '"]').forEach(function (x) { x.checked = cb.checked; });
      });
    });
    // Auto refresh
    const r = document.querySelector('[data-refresh]');
    if (r) setTimeout(function () { window.location.reload(); }, parseInt(r.dataset.refresh, 10) * 1000);
    // Offline banner
    const banner = document.getElementById('offline-banner');
    function upd() { if (banner) banner.classList.toggle('d-none', navigator.onLine); }
    window.addEventListener('online', upd); window.addEventListener('offline', upd); upd();
  });

  if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () { navigator.serviceWorker.register('/sw.js').catch(function () {}); });
  }
})();
