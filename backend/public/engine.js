/*
 * AI Shield — Web detection engine
 *
 * A faithful JavaScript port of the Android app's heuristic baseline
 * detector (HeuristicImageDetector.kt): identical features, constants and
 * logistic combination, so scores are comparable across platforms.
 * Runs 100% client-side — images never leave the browser.
 *
 * Exposed as `AIShieldEngine` in the browser and as module.exports in Node.
 */
(function (root) {
  'use strict';

  var N = 256; // analysis resolution (matches Android)

  // ---------------------------------------------------------------- FFT

  function fft(re, im) {
    var n = re.length;
    if ((n & (n - 1)) !== 0) throw new Error('FFT size must be power of two');
    for (var i = 1, j = 0; i < n; i++) {
      var bit = n >> 1;
      for (; j & bit;) { j ^= bit; bit >>= 1; }
      j |= bit;
      if (i < j) {
        var tr = re[i]; re[i] = re[j]; re[j] = tr;
        var ti = im[i]; im[i] = im[j]; im[j] = ti;
      }
    }
    for (var len = 2; len <= n; len <<= 1) {
      var ang = -2 * Math.PI / len;
      var wRe = Math.cos(ang), wIm = Math.sin(ang);
      for (var a = 0; a < n; a += len) {
        var curRe = 1, curIm = 0;
        var half = len >> 1;
        for (var k = 0; k < half; k++) {
          var x = a + k, y = x + half;
          var vRe = re[y] * curRe - im[y] * curIm;
          var vIm = re[y] * curIm + im[y] * curRe;
          re[y] = re[x] - vRe; im[y] = im[x] - vIm;
          re[x] += vRe; im[x] += vIm;
          var nextRe = curRe * wRe - curIm * wIm;
          curIm = curRe * wIm + curIm * wRe;
          curRe = nextRe;
        }
      }
    }
  }

  function radialPower(gray, n) {
    var re = new Float64Array(n * n);
    var im = new Float64Array(n * n);
    for (var i = 0; i < n * n; i++) re[i] = gray[i];

    var rowRe = new Float64Array(n), rowIm = new Float64Array(n);
    for (var y = 0; y < n; y++) {
      for (var x1 = 0; x1 < n; x1++) { rowRe[x1] = re[y * n + x1]; rowIm[x1] = 0; }
      fft(rowRe, rowIm);
      for (var x2 = 0; x2 < n; x2++) { re[y * n + x2] = rowRe[x2]; im[y * n + x2] = rowIm[x2]; }
    }
    var colRe = new Float64Array(n), colIm = new Float64Array(n);
    for (var cx = 0; cx < n; cx++) {
      for (var cy = 0; cy < n; cy++) { colRe[cy] = re[cy * n + cx]; colIm[cy] = im[cy * n + cx]; }
      fft(colRe, colIm);
      for (var cy2 = 0; cy2 < n; cy2++) { re[cy2 * n + cx] = colRe[cy2]; im[cy2 * n + cx] = colIm[cy2]; }
    }

    var half = n >> 1;
    var power = new Float64Array(half);
    var counts = new Int32Array(half);
    for (var yy = 0; yy < n; yy++) {
      var fy = yy < half ? yy : yy - n;
      for (var xx = 0; xx < n; xx++) {
        var fx = xx < half ? xx : xx - n;
        var bin = Math.min(half - 1, Math.floor(Math.sqrt(fy * fy + fx * fx)));
        power[bin] += re[yy * n + xx] * re[yy * n + xx] + im[yy * n + xx] * im[yy * n + xx];
        counts[bin]++;
      }
    }
    for (var b = 0; b < half; b++) if (counts[b] > 0) power[b] /= counts[b];
    return power;
  }

  function spectralSlope(power, upTo) {
    if (upTo === undefined) upTo = power.length >> 1;
    var sx = 0, sy = 0, sxx = 0, sxy = 0, count = 0;
    for (var b = 1; b < upTo; b++) {
      var x = Math.log(b), y = Math.log(power[b] + 1e-12);
      sx += x; sy += y; sxx += x * x; sxy += x * y;
      count++;
    }
    if (count === 0) return 0;
    var denom = count * sxx - sx * sx;
    if (Math.abs(denom) < 1e-12) return 0;
    return (count * sxy - sx * sy) / denom;
  }

  // ------------------------------------------------------- image features

  // data: Uint8ClampedArray RGBA. Returns features + score (0..1).
  function analyzeImageData(data, width, height) {
    // Downscale to N x N via a canvas handled by the caller; here we assume
    // the caller passed an N x N region (see analyzeElement).
    var gray = new Int32Array(N * N);
    var satSum = 0;
    var pxCount = width * height;
    for (var i = 0; i < pxCount; i++) {
      var r = data[i * 4], g = data[i * 4 + 1], b = data[i * 4 + 2];
      gray[i] = (30 * r + 59 * g + 11 * b) / 100;
      var mx = Math.max(r, g, b), mn = Math.min(r, g, b);
      satSum += mx === 0 ? 0 : (mx - mn) / mx;
    }
    var satMean = satSum / pxCount;

    var noise = medianResidualNoise(gray, N, N);
    var slope = spectralSlope(radialPower(gray, N));
    var blockiness = blockinessRatio(gray, N, N);

    return {
      visual: combine(noise, slope, blockiness, satMean),
      features: { noise: noise, slope: slope, blockiness: blockiness, saturation: satMean }
    };
  }

  function medianResidualNoise(gray, w, h) {
    var acc = 0, count = 0;
    var buf = new Int32Array(9);
    for (var y = 2; y < h - 2; y += 4) {
      for (var x = 2; x < w - 2; x += 4) {
        var k = 0;
        for (var dy = -1; dy <= 1; dy++)
          for (var dx = -1; dx <= 1; dx++) buf[k++] = gray[(y + dy) * w + (x + dx)];
        buf.sort(); // Int32Array.sort = numeric
        acc += Math.abs(gray[y * w + x] - buf[4]);
        count++;
      }
    }
    return count === 0 ? 0 : acc / count;
  }

  function blockinessRatio(gray, w, h) {
    var onGrid = 0, onCount = 0, offGrid = 0, offCount = 0;
    for (var y = 1; y < h; y++) {
      for (var x = 1; x < w; x++) {
        var d = Math.abs(gray[y * w + x] - gray[y * w + x - 1]);
        if (x % 8 === 0) { onGrid += d; onCount++; }
        else { offGrid += d; offCount++; }
      }
    }
    if (onCount === 0 || offCount === 0) return 1;
    var on = onGrid / onCount, off = offGrid / offCount;
    return off < 1e-6 ? 1 : on / off;
  }

  function clamp(z) { return Math.max(-2.5, Math.min(2.5, z)); }

  // Identical weights to HeuristicImageDetector.kt (intercept calibrated on
  // labeled smooth-render vs photographic samples — keep both in sync).
  function combine(noise, slope, blockiness, satMean) {
    var cleanZ = clamp((4.5 - noise) / 2.5);
    var slopeZ = clamp((-2.55 - slope) / 0.35);
    var blockZ = clamp((1.10 - blockiness) / 0.18);
    var satZ = clamp((satMean - 0.38) / 0.14);
    var logit = 0.1 + 0.90 * cleanZ + 0.45 * slopeZ + 0.15 * blockZ + 0.15 * satZ;
    return 1 / (1 + Math.exp(-logit));
  }

  // ------------------------------------------------------- frame utilities

  // dHash over a gray buffer (matches core/PHash.kt)
  function dHash(gray, w, h) {
    var hash = 0;
    for (var gy = 0; gy < 8; gy++) {
      for (var gx = 0; gx < 8; gx++) {
        var sx = Math.floor(gx * (w - 1) / 8);
        var sy = Math.floor(gy * (h - 1) / 7);
        var left = gray[sy * w + sx];
        var right = gray[sy * w + Math.min(sx + 1, w - 1)];
        hash = hash * 2 + (left > right ? 1 : 0);
      }
    }
    return hash;
  }

  function grayFromImageData(data, w, h) {
    var gray = new Int32Array(w * h);
    for (var i = 0; i < w * h; i++) {
      gray[i] = (30 * data[i * 4] + 59 * data[i * 4 + 1] + 11 * data[i * 4 + 2]) / 100;
    }
    return gray;
  }

  function meanAbsDiff(a, b) {
    var n = Math.min(a.length, b.length);
    if (n === 0) return 0;
    var acc = 0;
    for (var i = 0; i < n; i++) acc += Math.abs(a[i] - b[i]);
    return (acc / n) / 255;
  }

  function hamming(a, b) {
    var v = a ^ b, c = 0;
    while (v) { c += v & 1; v = Math.floor(v / 2); }
    return c;
  }

  // ThresholdEngine (core/ThresholdEngine.kt)
  function decide(overall, cfg) {
    if (overall >= cfg.alertThreshold) return 'ALERT';
    if (overall >= cfg.logThreshold) return 'LOG_SILENT';
    return 'IGNORE';
  }

  var api = {
    analyzeImageData: analyzeImageData,
    dHash: dHash,
    grayFromImageData: grayFromImageData,
    meanAbsDiff: meanAbsDiff,
    hamming: hamming,
    decide: decide,
    spectralSlope: spectralSlope,
    radialPower: radialPower
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.AIShieldEngine = api;
})(typeof self !== 'undefined' ? self : this);
