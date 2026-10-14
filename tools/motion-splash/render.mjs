// Strideway splash, 15 s, 1080x1920 @ 30 fps.
// Usage: node render.mjs [--out ../../assets/motion/splash_15s.mp4] [--stills 1.2,4.8,...]
//
// Palette and slope bands are copied from the app (ui/theme/Color.kt, core/terrain/DemMath.kt)
// so the video and the app read as one product. Speed, heart rate and cadence are demo values.

import { createCanvas, GlobalFonts, Path2D } from '@napi-rs/canvas';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  ANG, CELL, E, GX0, GY0, L, NX, NY, RN, RS, RX, RY, SLOPE, STEP, clamp01, contourSegments,
  eInOutCubic, eInOutSine, eOutBack, eOutCubic, elevAt, lerp, routeAt, seg,
} from './scene.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const W = 1080, H = 1920, FPS = 30, DUR = 15, N = DUR * FPS;

const args = process.argv.slice(2);
const argVal = (k) => { const i = args.indexOf(k); return i >= 0 ? args[i + 1] : null; };
const OUT = path.resolve(HERE, argVal('--out') ?? '../../assets/motion/splash_15s_silent.mp4');
const STILLS = argVal('--stills')?.split(',').map(Number) ?? null;

// Bahnschrift: DIN-style wayfinding type, matches road-sign legibility. Windows system font.
GlobalFonts.registerFromPath('C:\\Windows\\Fonts\\bahnschrift.ttf', 'Bahn');
// Strideway Sans (tools/font/build_font.py) for the wordmark, the same face the app splash uses.
GlobalFonts.registerFromPath(path.resolve(HERE, '../../assets/fonts/StridewaySans-BoldItalic.ttf'), 'Strideway Sans');
const WM_FONT = (px) => `italic 700 ${px}px "Strideway Sans"`;

const C = {
  bg: '#0E1012', surface: '#171A1D', outline: '#353A3F',
  text: '#F2F4F5', muted: '#A9B0B6', accent: '#FC4C02',
  zones: ['#7D868E', '#4FA3E0', '#5BC27A', '#F2B53A', '#F0524F'],
};
const BG = [0x0E, 0x10, 0x12];

const rgba = (hex, a) => {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${n >> 16},${(n >> 8) & 255},${n & 255},${a})`;
};


{
  const sorted = Float32Array.from(SLOPE).sort();
  const q = (f) => sorted[Math.floor(f * (sorted.length - 1))].toFixed(1);
  console.log(`slope deg  p25 ${q(0.25)}  p50 ${q(0.5)}  p75 ${q(0.75)}  p90 ${q(0.9)}  p97 ${q(0.97)}`);
}

// minor and index contour levels go into two Path2D objects
function buildContours(intervalM, indexEvery) {
  const minor = new Path2D(), major = new Path2D();
  contourSegments(intervalM, indexEvery, (isMajor, x0, y0, x1, y1) => { const p = isMajor ? major : minor; p.moveTo(x0, y0); p.lineTo(x1, y1); });
  return { minor, major };
}
const CONTOURS = buildContours(8, 5);

// slope heat texture, same stepped ramp as DemMath.slopeColor
const HTEX = 4, HW = Math.round((NX - 1) * CELL / HTEX), HH = Math.round((NY - 1) * CELL / HTEX);
const heatCanvas = createCanvas(HW, HH);
{
  const hctx = heatCanvas.getContext('2d'), img = hctx.createImageData(HW, HH);
  const stops = [[3, 0x4C, 0xAF, 0x50, 0x66], [6, 0xCD, 0xDC, 0x39, 0x99], [10, 0xFF, 0xC1, 0x07, 0xAA], [15, 0xFF, 0x57, 0x22, 0xBB], [25, 0xB7, 0x1C, 0x1C, 0xCC]];
  for (let y = 0; y < HH; y++) for (let x = 0; x < HW; x++) {
    const fx = x * HTEX / CELL, fy = y * HTEX / CELL, i = Math.min(NX - 2, Math.floor(fx)), j = Math.min(NY - 2, Math.floor(fy)), u = fx - i, v = fy - j;
    const deg = lerp(lerp(SLOPE[j * NX + i], SLOPE[j * NX + i + 1], u), lerp(SLOPE[(j + 1) * NX + i], SLOPE[(j + 1) * NX + i + 1], u), v);
    let col = null; for (const s of stops) if (deg >= s[0]) col = s;
    const o = (y * HW + x) * 4;
    if (col) { img.data[o] = col[1]; img.data[o + 1] = col[2]; img.data[o + 2] = col[3]; img.data[o + 3] = Math.min(255, Math.round(col[4] * 0.95)); }
  }
  hctx.putImageData(img, 0, 0);
}

// elevation profile along the route
const PROF = new Float32Array(RN);
let PMIN = Infinity, PMAX = -Infinity;
for (let i = 0; i < RN; i++) { PROF[i] = elevAt(RX[i], RY[i]); PMIN = Math.min(PMIN, PROF[i]); PMAX = Math.max(PMAX, PROF[i]); }

// ---------- canvases ----------
const canvas = createCanvas(W, H), ctx = canvas.getContext('2d');
const layer = createCanvas(W, H), lctx = layer.getContext('2d');

// ---------- timeline ----------
const T = { dot: 0.35, run0: 1.5, run1: 11.0, ov0: 8.0, ov1: 9.4, heat0: 8.9, heat1: 10.5, mapOut0: 11.2, mapOut1: 12.0, base0: 12.0, base1: 13.0, hop1: 13.65, tag0: 13.8 };

const progress = (t) => eInOutSine(seg(t, T.run0, T.run1));
const OVER = { x: 0, y: -1820, z: 0.40, ay: H * 0.46 };

function camera(t) {
  const s = progress(t) * L, r = routeAt(s);
  const e = eInOutCubic(seg(t, T.ov0, T.ov1));
  const zf = 0.92 + 0.26 * eOutCubic(seg(t, 0.4, 4.0)) - 0.14 * eInOutSine(seg(t, 5.5, 8.0));
  const ayF = lerp(H * 0.5, H * 0.62, eInOutCubic(seg(t, 0.9, 2.2)));
  const rotF = -Math.PI / 2 - r.ang;
  return {
    cx: lerp(r.x, OVER.x, e), cy: lerp(r.y, OVER.y, e),
    ax: W / 2, ay: lerp(ayF, OVER.ay, e),
    rot: lerp(rotF, 0, e), z: Math.exp(lerp(Math.log(zf), Math.log(OVER.z), e)),
    rider: r, s,
  };
}
const setCam = (c, cam) => { c.setTransform(1, 0, 0, 1, 0, 0); c.translate(cam.ax, cam.ay); c.rotate(cam.rot); c.scale(cam.z, cam.z); c.translate(-cam.cx, -cam.cy); };
const project = (cam, x, y) => {
  const dx = (x - cam.cx) * cam.z, dy = (y - cam.cy) * cam.z, c = Math.cos(cam.rot), s = Math.sin(cam.rot);
  return [cam.ax + dx * c - dy * s, cam.ay + dx * s + dy * c];
};
const ident = (c) => c.setTransform(1, 0, 0, 1, 0, 0);

// ---------- drawing helpers ----------
function riderDot(c, x, y, r, scaleX = 1, scaleY = 1) {
  ident(c);
  c.save(); c.translate(x, y); c.scale(scaleX, scaleY);
  c.fillStyle = C.bg; c.beginPath(); c.arc(0, 0, r + 6, 0, Math.PI * 2); c.fill(); // separation ring so the dot reads over any layer
  c.fillStyle = C.accent; c.beginPath(); c.arc(0, 0, r, 0, Math.PI * 2); c.fill();
  c.restore();
}
function ping(c, x, y, t0, t, maxR, dur = 1.4, lw = 3) {
  const u = seg(t, t0, t0 + dur); if (u <= 0 || u >= 1) return;
  ident(c);
  c.strokeStyle = rgba(C.accent, 0.7 * (1 - u)); c.lineWidth = lerp(lw, 1, u);
  c.beginPath(); c.arc(x, y, eOutCubic(u) * maxR, 0, Math.PI * 2); c.stroke();
}
function scrim(c, yOpaque, yClear, a) {
  if (a <= 0) return;
  ident(c);
  const g = c.createLinearGradient(0, yOpaque, 0, yClear);
  g.addColorStop(0, rgba(C.bg, a)); g.addColorStop(1, rgba(C.bg, 0));
  c.fillStyle = g; c.fillRect(0, Math.min(yOpaque, yClear), W, Math.abs(yClear - yOpaque));
}
const fontNum = (px) => `600 ${px}px Bahn`;
const fontLbl = (px) => `400 ${px}px Bahn`;

// ---------- map group ----------
function drawMap(c, t, cam, alpha) {
  if (alpha <= 0) return;
  c.globalAlpha = alpha;
  const dotScreen = project(cam, cam.rider.x, cam.rider.y);

  // slope heat, revealed bottom to top by a scan line
  const hu = seg(t, T.heat0, T.heat1);
  if (hu > 0) {
    const yscan = lerp(1760, 80, eInOutSine(hu));
    lctx.setTransform(1, 0, 0, 1, 0, 0); lctx.globalCompositeOperation = 'source-over'; lctx.clearRect(0, 0, W, H);
    setCam(lctx, cam); lctx.imageSmoothingEnabled = true; lctx.imageSmoothingQuality = 'high';
    lctx.drawImage(heatCanvas, GX0, GY0, (NX - 1) * CELL, (NY - 1) * CELL);
    ident(lctx); lctx.globalCompositeOperation = 'destination-in';
    const g = lctx.createLinearGradient(0, yscan, 0, yscan + 200);
    g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(1, 'rgba(0,0,0,1)');
    lctx.fillStyle = g; lctx.fillRect(0, 0, W, H);
    lctx.globalCompositeOperation = 'source-over';
    ident(c); c.globalAlpha = alpha * 0.92; c.drawImage(layer, 0, 0); c.globalAlpha = alpha;
  }

  // contours, revealed by a radial scan from the first GPS fix
  const reveal = seg(t, 0.55, 2.9);
  const drawContourSet = (cc) => {
    setCam(cc, cam); cc.lineCap = 'round';
    cc.strokeStyle = rgba(C.muted, 0.24); cc.lineWidth = 1.4 / cam.z; cc.stroke(CONTOURS.minor);
    cc.strokeStyle = rgba(C.muted, 0.5); cc.lineWidth = 2.2 / cam.z; cc.stroke(CONTOURS.major);
  };
  if (reveal < 1) {
    lctx.setTransform(1, 0, 0, 1, 0, 0); lctx.globalCompositeOperation = 'source-over'; lctx.clearRect(0, 0, W, H);
    drawContourSet(lctx);
    ident(lctx); lctx.globalCompositeOperation = 'destination-in';
    const R = eOutCubic(reveal) * 1500, soft = 260;
    const g = lctx.createRadialGradient(dotScreen[0], dotScreen[1], Math.max(0, R - soft), dotScreen[0], dotScreen[1], R + 1);
    g.addColorStop(0, 'rgba(0,0,0,1)'); g.addColorStop(1, 'rgba(0,0,0,0)');
    lctx.fillStyle = g; lctx.fillRect(0, 0, W, H);
    lctx.globalCompositeOperation = 'source-over';
    ident(c); c.drawImage(layer, 0, 0);
  } else drawContourSet(c);

  // scan edge
  if (hu > 0 && hu < 1) {
    const yscan = lerp(1760, 80, eInOutSine(hu));
    ident(c);
    const gl = c.createLinearGradient(0, yscan, 0, yscan + 90);
    gl.addColorStop(0, rgba(C.text, 0.16)); gl.addColorStop(1, rgba(C.text, 0));
    c.fillStyle = gl; c.fillRect(0, yscan, W, 90);
    const gh = c.createLinearGradient(0, 0, W, 0);
    gh.addColorStop(0, rgba(C.text, 0)); gh.addColorStop(0.5, rgba(C.text, 0.9)); gh.addColorStop(1, rgba(C.text, 0));
    c.fillStyle = gh; c.fillRect(0, yscan - 1, W, 2);
  }

  // route ahead (dashed), route behind (solid accent)
  setCam(c, cam); c.lineCap = 'round'; c.lineJoin = 'round';
  c.strokeStyle = rgba(C.text, 0.28); c.lineWidth = 3 / cam.z; c.setLineDash([16 / cam.z, 18 / cam.z]);
  c.beginPath(); for (let i = cam.rider.i; i < RN; i += 2) (i === cam.rider.i ? c.moveTo(cam.rider.x, cam.rider.y) : c.lineTo(RX[i], RY[i])); c.stroke();
  c.setLineDash([]);
  const trailW = lerp(9, 7, seg(t, T.ov0, T.ov1));
  c.strokeStyle = C.accent; c.lineWidth = trailW / cam.z;
  c.beginPath(); c.moveTo(RX[0], RY[0]); for (let i = 1; i <= cam.rider.i; i++) c.lineTo(RX[i], RY[i]); c.lineTo(cam.rider.x, cam.rider.y); c.stroke();

  // head glow (the one glow in this scene)
  ident(c);
  const gg = c.createRadialGradient(dotScreen[0], dotScreen[1], 0, dotScreen[0], dotScreen[1], 110);
  gg.addColorStop(0, rgba(C.accent, 0.38)); gg.addColorStop(1, rgba(C.accent, 0));
  c.fillStyle = gg; c.beginPath(); c.arc(dotScreen[0], dotScreen[1], 110, 0, Math.PI * 2); c.fill();
  c.globalAlpha = 1;
}

// ---------- overlays ----------
function drawProfile(c, t, cam) {
  const a = eOutCubic(seg(t, 2.4, 3.3)) * (1 - eInOutCubic(seg(t, 8.8, 9.4)));
  if (a <= 0) return;
  const x0 = 90, x1 = 990, base = 1800, h = 150;
  const px = (i) => x0 + (i / (RN - 1)) * (x1 - x0), py = (i) => base - 8 - ((PROF[i] - PMIN) / (PMAX - PMIN)) * h;
  ident(c); c.globalAlpha = a;
  c.fillStyle = C.muted; c.font = fontLbl(30); c.textAlign = 'left'; c.fillText('Elevasi', x0, 1672);
  c.strokeStyle = C.outline; c.lineWidth = 2; c.beginPath(); c.moveTo(x0, base); c.lineTo(x1, base); c.stroke();
  c.lineJoin = 'round';
  const path = (to) => { c.beginPath(); for (let i = 0; i <= to; i += 2) (i === 0 ? c.moveTo(px(i), py(i)) : c.lineTo(px(i), py(i))); c.lineTo(px(to), py(to)); };
  c.strokeStyle = rgba(C.text, 0.3); c.lineWidth = 3; path(RN - 1); c.stroke();
  const cur = cam.rider.i;
  if (cur > 1) {
    path(cur); c.strokeStyle = C.text; c.lineWidth = 4; c.stroke();
    c.lineTo(px(cur), base); c.lineTo(px(0), base); c.closePath();
    const gf = c.createLinearGradient(0, base - h, 0, base); gf.addColorStop(0, rgba(C.text, 0.16)); gf.addColorStop(1, rgba(C.text, 0));
    c.fillStyle = gf; c.fill();
  }
  c.fillStyle = C.accent; c.beginPath(); c.arc(px(cur), py(cur), 9, 0, Math.PI * 2); c.fill();
  c.globalAlpha = 1;
}

function drawLegend(c, t) {
  const a = eOutCubic(seg(t, 9.2, 9.9)) * (1 - eInOutCubic(seg(t, T.mapOut0, T.mapOut1)));
  if (a <= 0) return;
  ident(c); c.globalAlpha = a;
  const x0 = 90, x1 = 990, y = 1760, hgt = 24, n = 5, w = (x1 - x0) / n;
  const cols = [[0x4C, 0xAF, 0x50, 0x66], [0xCD, 0xDC, 0x39, 0x99], [0xFF, 0xC1, 0x07, 0xAA], [0xFF, 0x57, 0x22, 0xBB], [0xB7, 0x1C, 0x1C, 0xCC]];
  c.fillStyle = C.muted; c.font = fontLbl(30); c.textAlign = 'left'; c.fillText('Kemiringan', x0, 1722);
  cols.forEach((k, i) => {
    const al = Math.min(1, (k[3] / 255) * 1.15);
    c.fillStyle = `rgb(${Math.round(lerp(BG[0], k[0], al))},${Math.round(lerp(BG[1], k[1], al))},${Math.round(lerp(BG[2], k[2], al))})`;
    c.fillRect(x0 + i * w + (i ? 2 : 0), y, w - (i ? 2 : 0), hgt);
  });
  c.fillStyle = C.muted; c.font = fontLbl(28); c.textAlign = 'center';
  ['3°', '6°', '10°', '15°', '25°'].forEach((s, i) => c.fillText(s, x0 + i * w + (i ? 0 : 16), y + 64));
  c.globalAlpha = 1;
}

function drawHud(c, t) {
  const inT = 4.2, outT = 8.8;
  const stage = (k) => eOutCubic(seg(t, inT + k * 0.14, inT + k * 0.14 + 0.7)) * (1 - eInOutCubic(seg(t, outT + k * 0.06, outT + k * 0.06 + 0.5)));
  scrim(c, 0, 700, 0.92 * stage(0));
  ident(c);
  c.textAlign = 'left';

  // speed
  let a = stage(0);
  if (a > 0) {
    const sp = 36.4 * eOutCubic(seg(t, 4.3, 6.4)) + Math.sin(t * 2.1) * 0.5 * seg(t, 6, 6.6);
    c.globalAlpha = a; c.save(); c.translate(0, (1 - a) * 36);
    c.fillStyle = C.muted; c.font = fontLbl(32); c.fillText('Kecepatan', 90, 190);
    c.fillStyle = C.text; c.font = fontNum(200);
    const str = sp.toFixed(1), cell = c.measureText('0').width; let x = 90;
    for (const ch of str) { if (ch === '.') { c.fillText('.', x, 372); x += cell * 0.45; } else { c.fillText(ch, x, 372); x += cell; } }
    c.fillStyle = C.muted; c.font = fontLbl(44); c.fillText('km/j', x + 14, 372);
    c.restore();
  }

  // heart-rate zone ring
  a = stage(1);
  if (a > 0) {
    const bpm = Math.round(112 + 46 * eInOutSine(seg(t, 4.4, 8.6)));
    const zone = bpm < 110 ? 1 : bpm < 130 ? 2 : bpm < 150 ? 3 : bpm < 165 ? 4 : 5;
    c.globalAlpha = a; c.save(); c.translate(0, (1 - a) * 36);
    const cx = 850, cy = 285, R = 108, gap = 0.09, span = (Math.PI * 1.5 - gap * 4) / 5, start = Math.PI * 0.75;
    c.lineWidth = 20; c.lineCap = 'butt';
    for (let z = 0; z < 5; z++) {
      const a0 = start + z * (span + gap), on = z < zone;
      c.strokeStyle = on ? C.zones[z] : C.outline; c.beginPath(); c.arc(cx, cy, R, a0, a0 + span); c.stroke();
    }
    c.fillStyle = C.text; c.font = fontNum(78); c.textAlign = 'center'; c.fillText(String(bpm), cx, cy + 20);
    c.fillStyle = C.muted; c.font = fontLbl(28); c.fillText('bpm', cx, cy + 62);
    c.restore(); c.textAlign = 'left';
  }

  // cadence
  a = stage(2);
  if (a > 0) {
    const rpm = Math.round(80 + 8 * eInOutSine(seg(t, 4.6, 8.0)));
    c.globalAlpha = a; c.save(); c.translate(0, (1 - a) * 36);
    c.fillStyle = C.muted; c.font = fontLbl(32); c.fillText('Kadens', 90, 470);
    c.fillStyle = C.text; c.font = fontNum(64); const w = c.measureText(String(rpm)).width; c.fillText(String(rpm), 90, 540);
    c.fillStyle = C.muted; c.font = fontLbl(32); c.fillText('rpm', 90 + w + 12, 540);
    const n = 22, x0 = 330, step = 30;
    for (let i = 0; i < n; i++) {
      const ph = ((t * rpm / 60) - i / n * 0.9) % 1, p = Math.pow(Math.max(0, Math.cos(ph * Math.PI * 2)), 3);
      const hh = 12 + 46 * p;
      c.fillStyle = p > 0.7 ? C.text : C.outline; c.fillRect(x0 + i * step, 530 - hh, 14, hh);
    }
    c.restore();
  }
  c.globalAlpha = 1;
}

// ---------- wordmark ----------
const WM = { text: 'Str\u0131deway', fs: 190, baseline: 930 };
ctx.font = WM_FONT(WM.fs);
const WM_W = ctx.measureText(WM.text).width;
const WM_L = (W - WM_W) / 2, WM_R = WM_L + WM_W, LINE_Y = WM.baseline + 64;
const LET = [...WM.text].map((ch, i) => ({ ch, x: WM_L + ctx.measureText(WM.text.slice(0, i)).width, w: ctx.measureText(ch).width }));
// Tittle of "i" in Strideway Sans font units (1000 UPM): x 154-334 from the glyph origin, y 574-700 above baseline.
const TIT = { x: LET[3].x + 0.244 * WM.fs, y: WM.baseline - 0.637 * WM.fs, r: 0.063 * WM.fs };

function dotPath(t, dotAtMapEnd) {
  // returns {x,y,sx,sy,visible} for the detached dot after the ride ends
  if (t < T.mapOut0) return null;
  if (t < T.base0) {
    const u = eInOutCubic(seg(t, T.mapOut0, T.base0)), p0 = dotAtMapEnd, p1 = [WM_L, LINE_Y], c1 = [p0[0], p0[1] + 200];
    const x = (1 - u) ** 2 * p0[0] + 2 * (1 - u) * u * c1[0] + u * u * p1[0], y = (1 - u) ** 2 * p0[1] + 2 * (1 - u) * u * c1[1] + u * u * p1[1];
    return { x, y, sx: 1, sy: 1, r: 15 };
  }
  if (t < T.base1) { const u = eInOutCubic(seg(t, T.base0, T.base1)); return { x: lerp(WM_L, WM_R, u), y: LINE_Y, sx: 1, sy: 1, r: 15 }; }
  if (t < T.hop1) {
    const u = seg(t, T.base1, T.hop1), ex = eInOutSine(u), arc = 4 * u * (1 - u) * 330;
    return { x: lerp(WM_R, TIT.x, ex), y: lerp(LINE_Y, TIT.y, ex) - arc, sx: 1, sy: 1, r: lerp(15, TIT.r, ex), hop: true };
  }
  const k = seg(t, T.hop1, T.hop1 + 0.5), sq = Math.exp(-k * 9) * Math.cos(k * 18) * 0.28; // landing squash
  return { x: TIT.x, y: TIT.y, sx: 1 + sq, sy: 1 - sq, r: TIT.r };
}

function drawWordmark(c, t, dp) {
  if (t < T.base0) return;
  ident(c);
  const dotX = dp && t < T.hop1 ? (t < T.base1 ? dp.x : WM_R) : WM_R + 400;
  const runX = t < T.base1 ? (dp ? dp.x : WM_L) : WM_R;
  const revealX = t < T.base1 ? runX : WM_R + 420 * eOutCubic(seg(t, T.base1, T.base1 + 0.5));

  // route line under the word: drawn by the dot, then retracts toward the right once the dot hops
  const tail = eInOutCubic(seg(t, 13.25, 14.0));
  const lx0 = lerp(WM_L, WM_R, tail);
  if (runX > lx0) {
    c.strokeStyle = C.accent; c.lineWidth = 9; c.lineCap = 'round';
    c.beginPath(); c.moveTo(lx0, LINE_Y); c.lineTo(runX, LINE_Y); c.stroke();
  }

  // letters rise in behind the dot
  c.font = WM_FONT(WM.fs); c.textAlign = 'left'; c.fillStyle = C.text;
  LET.forEach((l, i) => {
    const u = eOutCubic(clamp01((revealX - (l.x - 70)) / 300));
    if (u <= 0 || l.ch === ' ') return;
    c.globalAlpha = u; c.fillText(l.ch, l.x, WM.baseline + (1 - u) * 46);
  });
  c.globalAlpha = 1;

  // tagline
  const tg = eOutCubic(seg(t, T.tag0, T.tag0 + 0.7));
  if (tg > 0) {
    c.globalAlpha = tg; c.fillStyle = C.muted; c.font = fontLbl(46); c.textAlign = 'center';
    c.fillText('Rekam jalurmu. Baca medannya.', W / 2, 1090 + (1 - tg) * 20);
    c.globalAlpha = 1;
  }
}

// ---------- frame ----------
function drawFrame(t) {
  ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
  ctx.fillStyle = C.bg; ctx.fillRect(0, 0, W, H);
  const cam = camera(t);
  const mapA = 1 - eInOutCubic(seg(t, T.mapOut0, T.mapOut1));
  const dotScreen = project(cam, cam.rider.x, cam.rider.y);

  // bottom scrim keeps the profile and legend readable over contour lines
  drawMap(ctx, t, cam, mapA);
  const botA = (eOutCubic(seg(t, 2.4, 3.3)) * (1 - eInOutCubic(seg(t, 8.8, 9.4))) + eOutCubic(seg(t, 9.2, 9.9)) * (1 - eInOutCubic(seg(t, T.mapOut0, T.mapOut1)))) * 0.94;
  scrim(ctx, 1920, 1500, Math.min(0.94, botA));
  drawProfile(ctx, t, cam);
  drawLegend(ctx, t);
  drawHud(ctx, t);

  // opening pings, then the rider dot
  const start = project(cam, RX[0], RY[0]);
  if (t < T.mapOut0) {
    ping(ctx, start[0], start[1], 0.5, t, 560, 1.5); ping(ctx, start[0], start[1], 0.78, t, 380, 1.3, 2);
    const pop = eOutBack(seg(t, T.dot, T.dot + 0.45));
    if (pop > 0) riderDot(ctx, dotScreen[0], dotScreen[1], 15 * pop);
  }
  const dp = dotPath(t, project(camera(T.mapOut0 - 1e-3), RX[RN - 1], RY[RN - 1]));
  drawWordmark(ctx, t, dp);
  if (dp) {
    if (dp.hop) { // comet tail on the hop
      for (let k = 1; k <= 7; k++) {
        const tp = t - k * 0.022; if (tp < T.base1) break;
        const q = dotPath(tp, [0, 0]); ident(ctx);
        ctx.fillStyle = rgba(C.accent, 0.5 * (1 - k / 8)); ctx.beginPath(); ctx.arc(q.x, q.y, q.r * (1 - k / 10), 0, Math.PI * 2); ctx.fill();
      }
    }
    if (t >= T.hop1) { ping(ctx, TIT.x, TIT.y, T.hop1, t, 320, 1.0); ping(ctx, TIT.x, TIT.y, T.hop1 + 0.2, t, 200, 0.8, 2); }
    // the dot stops using the separation ring once it sits in the letter, so it reads as the tittle
    if (t >= T.hop1 + 0.5) { ident(ctx); ctx.fillStyle = C.accent; ctx.beginPath(); ctx.arc(dp.x, dp.y, dp.r, 0, Math.PI * 2); ctx.fill(); }
    else riderDot(ctx, dp.x, dp.y, dp.r, dp.sx, dp.sy);
  }
  return canvas;
}

// ---------- run ----------
if (STILLS) {
  const dir = path.join(HERE, 'out'); fs.mkdirSync(dir, { recursive: true });
  for (const s of STILLS) fs.writeFileSync(path.join(dir, `still_${s.toFixed(2)}.png`), drawFrame(s).toBuffer('image/png'));
  console.log(`wrote ${STILLS.length} stills to ${dir}`);
  process.exit(0);
}
fs.mkdirSync(path.dirname(OUT), { recursive: true });
const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgba', '-s', `${W}x${H}`, '-r', String(FPS), '-i', '-',
  '-vf', 'scale=out_color_matrix=bt709:out_range=tv,format=yuv420p', '-c:v', 'libx264', '-preset', 'slow', '-crf', '14',
  '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv', '-movflags', '+faststart', OUT], { stdio: ['pipe', 'inherit', 'inherit'] });
const t0 = Date.now();
for (let f = 0; f < N; f++) {
  const buf = drawFrame(f / FPS).data();
  if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r));
  if (f % 30 === 0) process.stdout.write(`\rframe ${f}/${N}  ${((Date.now() - t0) / 1000).toFixed(0)}s`);
}
ff.stdin.end();
await new Promise((r) => ff.on('close', r));
console.log(`\nwrote ${OUT}`);
