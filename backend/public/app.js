/* AI Shield Web — app logic (analyze tab + live screen scan) */
(function () {
  'use strict';
  const E = window.AIShieldEngine;

  // ------------------------------------------------------------ config
  const $ = id => document.getElementById(id);
  let CFG = { alertThreshold: 0.90, logThreshold: 0.70, sampleIntervalMs: 2500, overlayAutoDismissMs: 8000, dedupHamming: 10, changeThreshold: 0.035 };
  let SERVER_ONLINE = true;
  fetch('/config').then(r => { if (!r.ok) throw new Error('config unavailable'); return r.json(); })
    .then(c => { CFG = { ...CFG, ...c }; updateLiveCfg(); })
    .catch(() => {
      // Static hosting (e.g. CDN demo): run fully client-side with defaults.
      SERVER_ONLINE = false;
      document.querySelectorAll('[data-server-only]').forEach(el => { el.hidden = true; });
      const s = $('shareLogs');
      if (s) { s.checked = false; s.disabled = true; }
      const note = $('standaloneNote');
      if (note) note.hidden = false;
      document.querySelectorAll('[data-standalone-only]').forEach(el => { el.hidden = false; });
    });
  function updateLiveCfg() {
    const el = document.getElementById('liveCfg');
    if (el) el.textContent = `alert ≥ ${Math.round(CFG.alertThreshold * 100)}% · silent log ≥ ${Math.round(CFG.logThreshold * 100)}% · sample every ${CFG.sampleIntervalMs / 1000}s`;
  }

  // ------------------------------------------------------------ helpers
  function analysisCanvas(source, w, h) {
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    const ctx = c.getContext('2d', { willReadFrequently: true });
    ctx.drawImage(source, 0, 0, w, h);
    return ctx.getImageData(0, 0, w, h);
  }

  function verdictFor(overall) {
    if (overall >= CFG.alertThreshold) return { text: 'Likely AI Generated', cls: 'result-red' };
    if (overall >= CFG.logThreshold) return { text: 'Possibly AI Generated', cls: 'result-amber' };
    return { text: 'Appears Authentic', cls: 'result-green' };
  }

  function showResult(visual, features, sourceLabel) {
    const overall = visual; // web v1: visual-only (same as mobile without faces/audio)
    const v = verdictFor(overall);
    const box = $('resultBox');
    box.className = 'result ' + v.cls;
    const pct = Math.round(overall * 100);
    box.innerHTML = `
      <div class="score-big">${pct}%</div>
      <div class="verdict">${v.text}</div>
      <div class="bars" style="margin-top:16px">
        <div class="bar-row"><label>Visual content</label><div class="bar"><i style="width:${pct}%"></i></div><b>${pct}%</b></div>
        <div class="bar-row"><label>Content Credentials</label><span class="muted">Not detected</span><b></b></div>
      </div>
      <p class="explain">Analyzed on this device. Source: ${sourceLabel}.</p>
      <details><summary>Feature details</summary><pre>${JSON.stringify(features, null, 2)}</pre></details>`;
    return overall;
  }

  function postLog(row) {
    if (!SERVER_ONLINE || !$('shareLogs') || !$('shareLogs').checked) return;
    fetch('/logs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ packageName: 'web', source: row.source, visual: row.visual, overall: row.overall, action: row.action, timeMs: Date.now(), token: 'web-demo' })
    }).catch(() => {});
  }

  // ------------------------------------------------------------ analyze tab
  const dropzone = $('dropzone');

  function handleFile(file) {
    if (!file || !file.type.startsWith('image/')) return;
    const url = URL.createObjectURL(file);
    analyzeUrl(url, file.name);
  }

  function analyzeUrl(url, label) {
    $('previewCard').hidden = false;
    $('resultBox').className = 'result';
    $('resultBox').innerHTML = '<div class="skeleton">Analyzing…</div>';
    const img = $('previewImg');
    img.onload = () => {
      setTimeout(() => { // let the skeleton paint
        const data = analysisCanvas(img, 256, 256);
        const res = E.analyzeImageData(data.data, 256, 256);
        const overall = showResult(res.visual, res.features, label);
        const action = E.decide(overall, CFG);
        if (action !== 'IGNORE') postLog({ source: 'web-upload', visual: res.visual, overall, action });
      }, 30);
    };
    img.src = url;
  }

  dropzone.addEventListener('dragover', e => { e.preventDefault(); dropzone.classList.add('dragover'); });
  dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
  dropzone.addEventListener('drop', e => {
    e.preventDefault();
    dropzone.classList.remove('dragover');
    handleFile(e.dataTransfer.files[0]);
  });
  dropzone.addEventListener('click', () => $('fileInput').click());
  $('fileInput').addEventListener('change', e => handleFile(e.target.files[0]));
  document.addEventListener('paste', e => {
    for (const item of e.clipboardData.items) {
      if (item.type.startsWith('image/')) handleFile(item.getAsFile());
    }
  });
  document.querySelectorAll('.samples figure').forEach(f => {
    f.addEventListener('click', () => analyzeUrl(f.dataset.src, f.querySelector('figcaption').textContent));
  });

  // ------------------------------------------------------------ live scan
  const video = $('liveVideo');
  let stream = null, timer = null;
  let lastGray = null, lastHash = -1, lastAnalysis = 0;
  let analyzed = 0, alerts = 0, silent = 0;

  function showChip(overall, result, sourceLabel) {
    const chip = $('chip');
    $('chipText').textContent = `Likely AI · ${Math.round(overall * 100)}%`;
    chip.hidden = false;
    chip.onclick = (e) => {
      if (e.target.id === 'chipClose') return;
      openModal(overall, result, sourceLabel);
      hideChip();
    };
    clearTimeout(showChip._t);
    showChip._t = setTimeout(hideChip, CFG.overlayAutoDismissMs);
  }
  function hideChip() { $('chip').hidden = true; }
  $('chipClose').addEventListener('click', hideChip);

  function openModal(overall, result, sourceLabel) {
    const v = verdictFor(overall);
    $('mOverall').textContent = Math.round(overall * 100) + '%';
    $('mOverall').parentElement.parentElement.className = 'modal-card ' + v.cls;
    $('mVerdict').textContent = v.text;
    $('mSource').textContent = sourceLabel;
    $('mTime').textContent = new Date().toLocaleString();
    $('barVisual').style.width = Math.round(result.visual * 100) + '%';
    $('pctVisual').textContent = Math.round(result.visual * 100) + '%';
    $('mFeatureJson').textContent = JSON.stringify(result.features, null, 2);
    $('modal').hidden = false;
  }
  $('modalClose').addEventListener('click', () => { $('modal').hidden = true; });
  $('modal').addEventListener('click', e => { if (e.target === $('modal')) $('modal').hidden = true; });

  $('btnStartLive').addEventListener('click', async () => {
    try {
      stream = await navigator.mediaDevices.getDisplayMedia({ video: { frameRate: 8 }, audio: false });
    } catch (err) {
      alert('Screen share was cancelled or is not available in this browser.');
      return;
    }
    video.srcObject = stream;
    $('liveIdle').hidden = true;
    $('btnStopLive').hidden = false;
    $('btnStartLive').hidden = true;
    $('liveStats').hidden = false;
    updateLiveCfg();

    lastGray = null; lastHash = -1; lastAnalysis = 0; analyzed = 0; alerts = 0; silent = 0;
    stream.getVideoTracks()[0].addEventListener('ended', stopLive);
    timer = setInterval(tick, 250);
  });

  function tick() {
    if (video.readyState < 2) return;
    const data = analysisCanvas(video, 64, 48);
    const gray = E.grayFromImageData(data, 64, 48);
    if (lastGray !== null) {
      const diff = E.meanAbsDiff(gray, lastGray);
      if (diff < CFG.changeThreshold) return;
    }
    lastGray = gray;

    const now = Date.now();
    if (now - lastAnalysis < CFG.sampleIntervalMs) return;
    lastAnalysis = now;

    const hash = E.dHash(gray, 64, 48);
    if (lastHash !== -1 && E.hamming(hash, lastHash) < CFG.dedupHamming) return;
    lastHash = hash;

    // full analysis on a bigger sample
    const hi = analysisCanvas(video, 256, 256);
    const res = E.analyzeImageData(hi.data, 256, 256);
    const overall = res.visual;
    const action = E.decide(overall, CFG);
    analyzed++;
    $('statAnalyzed').textContent = analyzed;

    if (action === 'ALERT') {
      alerts++;
      $('statAlerts').textContent = alerts;
      showChip(overall, res, 'Live screen scan · ' + new Date().toLocaleTimeString());
      postLog({ source: 'web-live', visual: res.visual, overall, action });
    } else if (action === 'LOG_SILENT') {
      silent++;
      $('statSilent').textContent = silent;
      postLog({ source: 'web-live', visual: res.visual, overall, action });
    }
    // IGNORE -> do nothing (the product principle)
  }

  function stopLive() {
    if (timer) clearInterval(timer);
    timer = null;
    if (stream) stream.getTracks().forEach(t => t.stop());
    stream = null;
    hideChip();
    $('btnStopLive').hidden = true;
    $('btnStartLive').hidden = false;
    $('liveIdle').hidden = false;
  }
  $('btnStopLive').addEventListener('click', stopLive);

  // ------------------------------------------------------------ tabs
  document.querySelectorAll('.navlink[data-tab]').forEach(link => {
    link.addEventListener('click', e => {
      e.preventDefault();
      document.querySelectorAll('.navlink[data-tab]').forEach(l => l.classList.remove('active'));
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      link.classList.add('active');
      $('tab-' + link.dataset.tab).classList.add('active');
    });
  });
})();
