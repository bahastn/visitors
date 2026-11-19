/*
 * Reception desk: camera QR scanning and offline-friendly check-in.
 * When the network is down, check-ins are kept in localStorage and sent to
 * /api/reception/sync as soon as the connection returns.
 */
(function () {
  const KEY = 'vms-offline-checkins';
  const CACHE = 'vms-today';
  const M = function (k) { return (window.RECEPTION_MSG || {})[k] || k; };

  function queue() { try { return JSON.parse(localStorage.getItem(KEY) || '[]'); } catch (e) { return []; } }
  function save(q) { try { localStorage.setItem(KEY, JSON.stringify(q)); } catch (e) { /* storage unavailable */ } }
  function status(text, cls) {
    const el = document.getElementById('sync-status');
    if (!el) return;
    el.className = 'alert small ' + (cls || 'alert-info');
    el.textContent = text;
    el.classList.toggle('d-none', !text);
  }
  function showPending() {
    const q = queue();
    if (q.length) status(M('pending') + ': ' + q.length, 'alert-warning'); else status('', 'alert-info');
  }

  // Keep today's list for offline lookups by QR code
  function refreshCache() {
    if (!navigator.onLine) return;
    fetch('/api/reception/today').then(function (r) { return r.json(); })
      .then(function (list) { try { localStorage.setItem(CACHE, JSON.stringify(list)); } catch (e) { /* ignore */ } })
      .catch(function () {});
  }

  function sync() {
    const q = queue();
    if (!q.length || !navigator.onLine) return;
    fetch('/api/reception/sync', {
      method: 'POST',
      headers: Object.assign({ 'Content-Type': 'application/json' }, window.csrfHeaders ? csrfHeaders() : {}),
      body: JSON.stringify(q)
    }).then(function (r) { return r.json(); }).then(function (res) {
      save([]);
      const ok = res.filter(function (x) { return x.ok; }).length;
      status(M('synced') + ': ' + ok + '/' + res.length, 'alert-success');
      setTimeout(function () { window.location.reload(); }, 1500);
    }).catch(showPending);
  }

  document.addEventListener('DOMContentLoaded', function () {
    refreshCache();
    showPending();
    sync();
    window.addEventListener('online', sync);

    // Offline check-in: intercept the form submit
    document.querySelectorAll('.checkin-form').forEach(function (f) {
      f.addEventListener('submit', function (e) {
        if (navigator.onLine) return;
        e.preventDefault();
        const q = queue();
        q.push({ id: f.dataset.id, at: new Date().toISOString() });
        save(q);
        const row = f.closest('tr');
        if (row) row.classList.add('table-warning');
        f.querySelector('button').disabled = true;
        status(M('offlineSaved'), 'alert-warning');
      });
    });

    // Table filter
    const filter = document.getElementById('filter');
    if (filter) filter.addEventListener('input', function () {
      const v = filter.value.toLowerCase();
      document.querySelectorAll('#expectedTable tbody tr').forEach(function (tr) {
        tr.style.display = tr.textContent.toLowerCase().indexOf(v) >= 0 ? '' : 'none';
      });
    });

    // Scan form offline: find in cached list and check in offline
    const scanForm = document.getElementById('scanForm');
    if (scanForm) scanForm.addEventListener('submit', function (e) {
      if (navigator.onLine) return;
      e.preventDefault();
      let code = document.getElementById('scanCode').value.trim();
      if (code.indexOf('/') >= 0) code = code.substring(code.lastIndexOf('/') + 1);
      let list = [];
      try { list = JSON.parse(localStorage.getItem(CACHE) || '[]'); } catch (x) { /* ignore */ }
      const hit = list.find(function (v) { return v.qr === code || (v.ref || '').toLowerCase() === code.toLowerCase(); });
      if (!hit) { status(M('notFound') + ': ' + code, 'alert-danger'); return; }
      if (hit.flag) { status(M('flagged') + ': ' + hit.name, 'alert-danger'); return; }
      const q = queue(); q.push({ id: hit.id, at: new Date().toISOString() }); save(q);
      status(M('offlineSaved') + ': ' + hit.name, 'alert-warning');
    });

    // Camera scanning with html5-qrcode
    const btn = document.getElementById('startScan');
    if (btn && window.Html5Qrcode) {
      let scanner = null;
      btn.addEventListener('click', function () {
        if (scanner) { scanner.stop().then(function () { scanner = null; }); return; }
        scanner = new Html5Qrcode('qr-reader');
        scanner.start({ facingMode: 'environment' }, { fps: 10, qrbox: 220 }, function (text) {
          scanner.stop().then(function () {
            scanner = null;
            document.getElementById('scanCode').value = text;
            scanForm.requestSubmit ? scanForm.requestSubmit() : scanForm.submit();
          });
        }).catch(function (err) { status(String(err), 'alert-danger'); scanner = null; });
      });
    }
  });
})();
