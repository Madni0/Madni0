/* Node test: run the browser engine (same code) against the sample JPEGs. */
import fs from 'fs';
import path from 'path';
import { createRequire } from 'module';
import url from 'url';
const require = createRequire(import.meta.url);
const engine = require('../public/engine.js');
const jpeg = require('jpeg-js');

const dir = path.join(path.dirname(url.fileURLToPath(import.meta.url)), '..', 'public', 'samples');

function scoreFile(file) {
  const raw = fs.readFileSync(path.join(dir, file));
  const img = jpeg.decode(raw, { useTArray: true, maxMemoryUsageInMB: 1024 });
  // scale to 256x256 with box sampling (nearest for test parity is fine)
  const W = 256, H = 256;
  const out = Buffer.alloc(W * H * 4);
  for (let y = 0; y < H; y++) {
    const sy = Math.floor(y * img.height / H);
    for (let x = 0; x < W; x++) {
      const sx = Math.floor(x * img.width / W);
      const si = (sy * img.width + sx) * 4, di = (y * W + x) * 4;
      out[di] = img.data[si]; out[di + 1] = img.data[si + 1];
      out[di + 2] = img.data[si + 2]; out[di + 3] = 255;
    }
  }
  const res = engine.analyzeImageData(out, W, H);
  return res;
}

const ai = scoreFile('sample-ai.jpg');
console.log('sample-ai.jpg   visual =', ai.visual.toFixed(3), JSON.stringify(Object.fromEntries(Object.entries(ai.features).map(([k, v]) => [k, +v.toFixed(3)]))));
const photo = scoreFile('sample-photo.jpg');
console.log('sample-photo.jpg visual =', photo.visual.toFixed(3), JSON.stringify(Object.fromEntries(Object.entries(photo.features).map(([k, v]) => [k, +v.toFixed(3)]))));
