// Writes the data the in-app splash draws: contour polylines around the start of the route,
// the first stretch of the route and its smoothed heading. Same terrain as the video (scene.mjs).
// Usage: node export-scene.mjs [--preview]
//
// Units are world px: at zoom 1 the video frame is 1080 wide, so the app scales by width / 1080.

import { createCanvas } from '@napi-rs/canvas';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { ANG, RN, RS, RX, RY, contourSegments } from './scene.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(HERE, '../../app/src/main/res/raw/splash_scene.json');
// Window: the splash camera stays within ~1100 world px of the rider, who rides s = 0..1100.
const BOUNDS = [-1350, -2050, 1350, 1100];
const ROUTE_END_S = 1100;
const TOLERANCE = 1.2; // Douglas-Peucker, world px

const inside = (x, y) => x >= BOUNDS[0] && x <= BOUNDS[2] && y >= BOUNDS[1] && y <= BOUNDS[3];

// Marching squares emits loose segments; shared cell edges produce identical endpoints, so they chain.
function chain(segments) {
  const key = (x, y) => `${x.toFixed(3)},${y.toFixed(3)}`;
  const byEnd = new Map();
  segments.forEach((s, i) => {
    for (const k of [key(s[0], s[1]), key(s[2], s[3])]) { if (!byEnd.has(k)) byEnd.set(k, []); byEnd.get(k).push(i); }
  });
  const used = new Uint8Array(segments.length);
  const lines = [];
  const extend = (pts, atEnd) => {
    for (;;) {
      const [x, y] = atEnd ? pts[pts.length - 1] : pts[0];
      const next = (byEnd.get(key(x, y)) ?? []).find((i) => !used[i]);
      if (next === undefined) return;
      used[next] = 1;
      const s = segments[next];
      const p = key(s[0], s[1]) === key(x, y) ? [s[2], s[3]] : [s[0], s[1]];
      if (atEnd) pts.push(p); else pts.unshift(p);
    }
  };
  segments.forEach((s, i) => {
    if (used[i]) return;
    used[i] = 1;
    const pts = [[s[0], s[1]], [s[2], s[3]]];
    extend(pts, true); extend(pts, false);
    lines.push(pts);
  });
  return lines;
}

function simplify(pts, tol) {
  if (pts.length < 3) return pts;
  const keep = new Uint8Array(pts.length); keep[0] = keep[pts.length - 1] = 1;
  const stack = [[0, pts.length - 1]];
  while (stack.length) {
    const [a, b] = stack.pop();
    const [ax, ay] = pts[a], [bx, by] = pts[b], len = Math.hypot(bx - ax, by - ay) || 1e-9;
    let worst = -1, wi = -1;
    for (let i = a + 1; i < b; i++) {
      const d = Math.abs((bx - ax) * (ay - pts[i][1]) - (ax - pts[i][0]) * (by - ay)) / len;
      if (d > worst) { worst = d; wi = i; }
    }
    if (worst > tol) { keep[wi] = 1; stack.push([a, wi], [wi, b]); }
  }
  return pts.filter((_, i) => keep[i]);
}

const segs = { minor: [], major: [] };
contourSegments(8, 5, (isMajor, x0, y0, x1, y1) => {
  if (inside(x0, y0) && inside(x1, y1)) (isMajor ? segs.major : segs.minor).push([x0, y0, x1, y1]);
});
const flat = (lines) => lines
  .map((l) => simplify(l, TOLERANCE))
  .filter((l) => l.length >= 2)
  .map((l) => l.flatMap(([x, y]) => [Math.round(x), Math.round(y)]));

const last = RS.findIndex((s) => s > ROUTE_END_S);
const n = last < 0 ? RN : last;
const scene = {
  version: 1,
  bounds: BOUNDS,
  routeStep: RS[1] - RS[0],
  route: Array.from({ length: n }, (_, i) => [Math.round(RX[i] * 10) / 10, Math.round(RY[i] * 10) / 10]).flat(),
  heading: Array.from({ length: n }, (_, i) => Math.round(ANG[i] * 1000) / 1000),
  minor: flat(chain(segs.minor)),
  major: flat(chain(segs.major)),
};
fs.mkdirSync(path.dirname(OUT), { recursive: true });
const json = JSON.stringify(scene);
fs.writeFileSync(OUT, json);
const pts = (ls) => ls.reduce((a, l) => a + l.length / 2, 0);
console.log(`wrote ${OUT}\n  ${(json.length / 1024).toFixed(1)} KB, ${scene.minor.length} minor + ${scene.major.length} major lines, ` +
  `${pts(scene.minor) + pts(scene.major)} points, route ${n} points`);

if (process.argv.includes('--preview')) {
  const W = 1080, H = 1920, c = createCanvas(W, H), g = c.getContext('2d');
  g.fillStyle = '#0E1012'; g.fillRect(0, 0, W, H);
  g.translate(W / 2, H / 2); g.scale(0.55, 0.55); g.translate(0, -500);
  const stroke = (lines, style, w) => {
    g.strokeStyle = style; g.lineWidth = w; g.beginPath();
    for (const l of lines) { g.moveTo(l[0], l[1]); for (let i = 2; i < l.length; i += 2) g.lineTo(l[i], l[i + 1]); }
    g.stroke();
  };
  stroke(scene.minor, 'rgba(169,176,182,0.3)', 2);
  stroke(scene.major, 'rgba(169,176,182,0.6)', 3.5);
  stroke([scene.route], '#FC4C02', 12);
  g.setLineDash([12, 12]); g.strokeStyle = 'rgba(242,244,245,0.5)'; g.lineWidth = 3;
  g.strokeRect(BOUNDS[0], BOUNDS[1], BOUNDS[2] - BOUNDS[0], BOUNDS[3] - BOUNDS[1]);
  const p = path.join(HERE, 'out', 'scene_preview.png');
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, c.toBuffer('image/png'));
  console.log(`  preview ${p}`);
}
