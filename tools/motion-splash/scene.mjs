// Shared scene for the splash: seeded terrain, the switchback route and contour extraction.
// render.mjs (the 15 s video) and export-scene.mjs (data for the in-app splash) both build on it,
// so the app and the video show the same hill.

// ---------- math ----------
export const clamp01 = (x) => Math.min(1, Math.max(0, x));
export const lerp = (a, b, t) => a + (b - a) * t;
export const seg = (t, a, b) => clamp01((t - a) / (b - a));
export const eOutCubic = (t) => 1 - Math.pow(1 - t, 3);
export const eInOutCubic = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
export const eInOutSine = (t) => -(Math.cos(Math.PI * t) - 1) / 2;
export const eOutExpo = (t) => (t >= 1 ? 1 : 1 - Math.pow(2, -10 * t));
export const eOutBack = (t) => { const c1 = 1.9, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); };

export function makeNoise(seed) {
  const perm = [...Array(256).keys()];
  let st = seed;
  const rnd = () => { st = (Math.imul(st, 1664525) + 1013904223) >>> 0; return st / 4294967296; };
  for (let i = 255; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [perm[i], perm[j]] = [perm[j], perm[i]]; }
  const p = new Uint8Array(512);
  for (let i = 0; i < 512; i++) p[i] = perm[i & 255];
  const gx = new Float32Array(256), gy = new Float32Array(256);
  for (let i = 0; i < 256; i++) { const a = rnd() * Math.PI * 2; gx[i] = Math.cos(a); gy[i] = Math.sin(a); }
  const fade = (t) => t * t * t * (t * (t * 6 - 15) + 10);
  const dotg = (h, x, y) => gx[h] * x + gy[h] * y;
  return (x, y) => {
    const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
    const X = xi & 255, Y = yi & 255, u = fade(xf), v = fade(yf);
    const aa = p[p[X] + Y], ab = p[p[X] + Y + 1], ba = p[p[X + 1] + Y], bb = p[p[X + 1] + Y + 1];
    return lerp(lerp(dotg(aa, xf, yf), dotg(ba, xf - 1, yf), u), lerp(dotg(ab, xf, yf - 1), dotg(bb, xf - 1, yf - 1), u), v) * 1.6;
  };
}
export const noise = makeNoise(20261012);

// ---------- route: a switchback climb, world units are px at zoom 1 ----------
export const CP = [
  [0, 380], [0, 0], [60, -260], [-120, -520], [-300, -640], [-180, -860], [120, -900], [330, -1050], [240, -1300],
  [-40, -1420], [-320, -1560], [-380, -1850], [-120, -2000], [200, -2060], [380, -2280], [220, -2520],
  [-60, -2600], [-300, -2780], [-240, -3050], [40, -3200], [260, -3400], [180, -3640], [140, -3900],
];
export function catmull(p0, p1, p2, p3, t) {
  const t2 = t * t, t3 = t2 * t;
  return [0, 1].map((k) => 0.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2 + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3));
}
export const dense = [];
for (let i = 0; i < CP.length - 1; i++) {
  const p0 = CP[Math.max(0, i - 1)], p1 = CP[i], p2 = CP[i + 1], p3 = CP[Math.min(CP.length - 1, i + 2)];
  for (let k = 0; k < 48; k++) dense.push(catmull(p0, p1, p2, p3, k / 48));
}
dense.push(CP[CP.length - 1]);
// The first and last control points are run-in/run-out; trim them so the ride starts at (0,0) and ends at (180,-3640).
export const STEP = 6;
export const RX = [], RY = [], RS = [];
{
  const cum = [0];
  for (let i = 1; i < dense.length; i++) cum.push(cum[i - 1] + Math.hypot(dense[i][0] - dense[i - 1][0], dense[i][1] - dense[i - 1][1]));
  const nearestIdx = (px, py) => dense.reduce((b, p, i) => { const d = Math.hypot(p[0] - px, p[1] - py); return d < b[0] ? [d, i] : b; }, [1e9, 0])[1];
  const s0 = cum[nearestIdx(0, 0)], s1 = cum[nearestIdx(180, -3640)];
  let j = 0;
  for (let s = s0; s <= s1; s += STEP) {
    while (j < dense.length - 2 && cum[j + 1] < s) j++;
    const f = (s - cum[j]) / (cum[j + 1] - cum[j]);
    RX.push(lerp(dense[j][0], dense[j + 1][0], f));
    RY.push(lerp(dense[j][1], dense[j + 1][1], f));
    RS.push(s - s0);
  }
}
export const RN = RX.length, L = RS[RN - 1];
export const ANG = new Float32Array(RN);
{
  const raw = new Float32Array(RN);
  for (let i = 0; i < RN; i++) {
    const a = Math.max(0, i - 3), b = Math.min(RN - 1, i + 3);
    raw[i] = Math.atan2(RY[b] - RY[a], RX[b] - RX[a]);
  }
  for (let i = 1; i < RN; i++) while (raw[i] - raw[i - 1] > Math.PI) raw[i] -= 2 * Math.PI; // unwrap
  for (let i = 1; i < RN; i++) while (raw[i] - raw[i - 1] < -Math.PI) raw[i] += 2 * Math.PI;
  const win = 70; // smooth over about 420 px so the camera banks instead of snapping
  for (let i = 0; i < RN; i++) {
    let acc = 0, cnt = 0;
    for (let k = -win; k <= win; k++) { const q = Math.min(RN - 1, Math.max(0, i + k)); acc += raw[q]; cnt++; }
    ANG[i] = acc / cnt;
  }
}
export function routeAt(s) {
  const f = Math.min(RN - 1.001, Math.max(0, s / STEP)), i = Math.floor(f), u = f - i;
  return { x: lerp(RX[i], RX[i + 1], u), y: lerp(RY[i], RY[i + 1], u), ang: lerp(ANG[i], ANG[i + 1], u), i };
}

// ---------- terrain ----------
export const GX0 = -1500, GY0 = -4800, CELL = 10, NX = 301, NY = 591;
export const RELIEF = 0.3;
export const E = new Float32Array(NX * NY);
{
  const coarse = []; for (let i = 0; i < RN; i += 3) coarse.push(i);
  for (let j = 0; j < NY; j++) for (let i = 0; i < NX; i++) {
    const x = GX0 + i * CELL, y = GY0 + j * CELL;
    let dmin = 1e9, ws = 0, wsum = 0;
    for (const k of coarse) {
      const d2 = (RX[k] - x) ** 2 + (RY[k] - y) ** 2;
      if (d2 < dmin) dmin = d2;
      const w = Math.exp(-d2 / (2 * 380 * 380)); ws += w * RS[k]; wsum += w; // smooth stand-in for "arc position of nearest road"
    }
    const d = Math.sqrt(dmin), sn = wsum > 1e-9 ? ws / wsum : 0;
    const f1 = noise(x / 1100 + 3.1, y / 1100 + 7.7), f2 = noise(x / 450 + 11, y / 450 + 2), f3 = noise(x / 190 + 5, y / 190 + 9);
    const r = 1 - Math.abs(noise(x / 700 + 20, y / 700 + 40));
    let e = RELIEF * (210 * f1 + 85 * f2 + 22 * f3 + 90 * r * r);
    e += 520 * (sn / L) * Math.exp(-((d / 1000) ** 2)) - 60 * Math.exp(-((d / 170) ** 2));
    E[j * NX + i] = e;
  }
}
export const elevAt = (x, y) => {
  const fx = (x - GX0) / CELL, fy = (y - GY0) / CELL, i = Math.floor(fx), j = Math.floor(fy), u = fx - i, v = fy - j;
  const g = (a, b) => E[Math.min(NY - 1, Math.max(0, b)) * NX + Math.min(NX - 1, Math.max(0, a))];
  return lerp(lerp(g(i, j), g(i + 1, j), u), lerp(g(i, j + 1), g(i + 1, j + 1), u), v);
};
export const SLOPE = new Float32Array(NX * NY);
{
  for (let j = 0; j < NY; j++) for (let i = 0; i < NX; i++) {
    const a = E[j * NX + Math.max(0, i - 1)], b = E[j * NX + Math.min(NX - 1, i + 1)];
    const c = E[Math.max(0, j - 1) * NX + i], d = E[Math.min(NY - 1, j + 1) * NX + i];
    const gx = (b - a) / (2 * CELL * 2), gy = (d - c) / (2 * CELL * 2); // 1 world px = 2 m
    SLOPE[j * NX + i] = Math.atan(Math.hypot(gx, gy)) * 180 / Math.PI;
  }
}

// Contour lines via marching squares. emit(isIndexLevel, x0, y0, x1, y1) receives every segment.
export function contourSegments(intervalM, indexEvery, emit) {
  let lo = Infinity, hi = -Infinity;
  for (const v of E) { if (v < lo) lo = v; if (v > hi) hi = v; }
  const first = Math.ceil(lo / intervalM), last = Math.floor(hi / intervalM);
  for (let lv = first; lv <= last; lv++) {
    const level = lv * intervalM, major = lv % indexEvery === 0;
    for (let j = 0; j < NY - 1; j++) for (let i = 0; i < NX - 1; i++) {
      const a = E[j * NX + i], b = E[j * NX + i + 1], c = E[(j + 1) * NX + i + 1], d = E[(j + 1) * NX + i];
      const mn = Math.min(a, b, c, d), mx = Math.max(a, b, c, d);
      if (level <= mn || level > mx) continue;
      const x = GX0 + i * CELL, y = GY0 + j * CELL;
      const pts = [];
      const edge = (v0, v1, x0, y0, x1, y1) => { if ((v0 < level) !== (v1 < level)) { const t = (level - v0) / (v1 - v0); pts.push([lerp(x0, x1, t), lerp(y0, y1, t)]); } };
      edge(a, b, x, y, x + CELL, y); edge(b, c, x + CELL, y, x + CELL, y + CELL);
      edge(d, c, x, y + CELL, x + CELL, y + CELL); edge(a, d, x, y, x, y + CELL);
      if (pts.length >= 2) emit(major, pts[0][0], pts[0][1], pts[1][0], pts[1][1]);
      if (pts.length === 4) emit(major, pts[2][0], pts[2][1], pts[3][0], pts[3][1]);
    }
  }
}
