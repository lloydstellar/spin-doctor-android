// Runs buildBeatEnvelope() from the website's app.js on a synthetic signal and
// writes the result for BeatEnvelopeParityTest.
// usage: node tools/parity/beat-envelope.mjs ../vinyl/js/app.js app/src/test/resources/beat-envelope.json
import fs from 'node:fs';
const [appJs, out] = process.argv.slice(2);
const src = fs.readFileSync(appJs, 'utf8');
const start = src.indexOf('function buildBeatEnvelope');
const end = src.indexOf('async function prepareBeatEnvelope');
const buildBeatEnvelope = new Function(src.slice(start, end) + '\nreturn buildBeatEnvelope;')();

// the same signal as BeatEnvelopeParityTest.signal(): a 120 BPM kick and a high tone, 44.1 kHz, 12 s
const rate = 44100, seconds = 12;
const data = new Float32Array(rate * seconds);
for (let i = 0; i < data.length; i++) {
  const t = i / rate;
  const p = (t - 0.25) - Math.floor((t - 0.25) / 0.5) * 0.5;
  const kick = t >= 0.25 ? 0.7 * Math.sin(2 * Math.PI * 60 * p) * Math.exp(-25 * p) : 0;
  data[i] = kick + 0.2 * Math.sin(2 * Math.PI * 3000 * t);
}
const buffer = { sampleRate: rate, length: data.length, getChannelData: () => data };
const env = buildBeatEnvelope(buffer, { start: 2.0, end: 10.5 }, 30);
fs.writeFileSync(out, JSON.stringify(Array.from(env)));
console.log('wrote', env.length, 'values');
