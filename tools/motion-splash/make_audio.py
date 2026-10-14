"""Synthesised sound design for the 15 s Strideway splash. Writes out/splash_audio.wav.

Every hit is placed on a cue from render.mjs (dot ignition, GPS pings, HUD in, camera pull-out,
slope scan, letter reveal, the dot landing as the tittle), so picture and sound stay in sync.
"""
import wave
from pathlib import Path

import numpy as np

SR = 48000
DUR = 15.0
N = int(SR * DUR)
rng = np.random.default_rng(1012)
mix = np.zeros((N, 2), dtype=np.float64)


def t_axis(d):
    return np.arange(int(SR * d)) / SR


def place(sig, at, gain=1.0, pan=0.0):
    i = int(at * SR)
    if i >= N:
        return
    sig = sig[: N - i]
    l, r = gain * (1 - max(0, pan)), gain * (1 + min(0, pan))
    mix[i : i + len(sig), 0] += sig * l
    mix[i : i + len(sig), 1] += sig * r


def env_ad(n, a, d, curve=4.0):
    t = np.arange(n) / SR
    e = np.minimum(1, t / max(a, 1e-4)) * np.exp(-t * curve / max(d, 1e-4))
    return e


def tone(freq, d, a=0.005, curve=5.0, harm=(1.0,), detune=0.0):
    t = t_axis(d)
    s = sum(h * np.sin(2 * np.pi * freq * (k + 1) * (1 + detune) * t) for k, h in enumerate(harm))
    return s * env_ad(len(t), a, d, curve)


def lowpass(x, cutoff):
    # one-pole, enough for whooshes
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def sweep_noise(d, f0, f1, shape="up"):
    """Band-limited noise swell with a moving cutoff, vectorised via block filtering."""
    n = int(SR * d)
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    block = 480
    acc = 0.0
    for b in range(0, n, block):
        u = b / n
        fc = f0 * (f1 / f0) ** u
        a = np.exp(-2 * np.pi * fc / SR)
        for i in range(b, min(n, b + block)):
            acc = (1 - a) * noise[i] + a * acc
            out[i] = acc
    w = np.sin(np.pi * np.linspace(0, 1, n)) ** 2 if shape == "up" else np.linspace(1, 0, n) ** 2
    return out * w / (np.max(np.abs(out)) + 1e-9)


def chirp(f0, f1, d, curve=1.0):
    t = t_axis(d)
    u = t / d
    f = f0 * (f1 / f0) ** (u**curve)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.sin(np.pi * u) ** 1.5


def thump(f0=110, f1=42, d=0.6):
    t = t_axis(d)
    f = f1 + (f0 - f1) * np.exp(-t * 18)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t * 7)


# --- 0.35 s: dot ignites
place(tone(880, 0.35, curve=9, harm=(1, 0.25)), 0.35, 0.30)
# --- 0.5 and 0.78 s: GPS pings, with a faint echo
for at, f, g in ((0.5, 1318.5, 0.26), (0.78, 987.8, 0.18)):
    s = tone(f, 1.4, a=0.002, curve=3.2, harm=(1, 0.18))
    place(s, at, g, -0.1)
    place(s, at + 0.19, g * 0.35, 0.25)

# --- 0.6-2.9 s: contour scan swell
place(sweep_noise(2.3, 300, 3800), 0.6, 0.06)

# --- 1.5-11.2 s: low pad that rises with the ride
t = t_axis(9.7)
pad = (np.sin(2 * np.pi * 55 * t) + 0.5 * np.sin(2 * np.pi * 82.4 * t + 0.3) + 0.25 * np.sin(2 * np.pi * 110.3 * t)) * (0.7 + 0.3 * np.sin(2 * np.pi * 0.21 * t))
swell = np.minimum(1, t / 3.0) * np.minimum(1, (9.7 - t) / 1.2)
place(pad * swell, 1.5, 0.09)

# --- pulse: soft thumps at 112 bpm from 2.0 s to 11.0 s, tied to the rider moving
beat = 60 / 112
at = 2.0
k = 0
while at < 11.0:
    place(thump(96, 44, 0.35), at, 0.16 * min(1, (at - 2.0) / 2.5) * (1 if (k % 4) else 1.25))
    if k % 2:
        place(lowpass(rng.standard_normal(int(SR * 0.06)), 7000) * env_ad(int(SR * 0.06), 0.001, 0.05, 6), at + beat / 2, 0.05)
    at += beat
    k += 1

# --- 4.2 s: HUD ticks (speed, ring, cadence)
for i, f in enumerate((1568, 1760, 2093)):
    place(tone(f, 0.12, a=0.001, curve=9), 4.2 + i * 0.14, 0.12, (-0.3, 0.3, 0)[i])

# --- 8.0-9.4 s: camera pull-out whoosh
place(sweep_noise(1.5, 400, 5000), 7.95, 0.13)
# --- 8.9-10.5 s: slope scan sweep
place(chirp(220, 1760, 1.6, 1.4), 8.9, 0.07)
place(sweep_noise(1.6, 900, 6500), 8.9, 0.045)

# --- 11.2-12.0 s: map fades, falling whoosh
place(sweep_noise(0.9, 4200, 300, shape="down"), 11.15, 0.1)

# --- 12.0-13.0 s: letters pluck in as the dot passes them (pentatonic)
fr = np.array([0.0, 0.14, 0.27, 0.35, 0.47, 0.62, 0.77, 0.88, 0.95])
notes = [523.25, 587.33, 659.25, 783.99, 880.0, 783.99, 880.0, 1046.5, 1174.7]
uu = np.linspace(0, 1, 2001)
ease = np.where(uu < 0.5, 4 * uu**3, 1 - (-2 * uu + 2) ** 3 / 2)
for f, nt in zip(fr, notes):
    u = uu[np.searchsorted(ease, f)]
    place(tone(nt, 0.5, a=0.002, curve=6, harm=(1, 0.3, 0.1)), 12.0 + u * 1.0, 0.13, (f - 0.5) * 0.6)

# --- 13.0-13.65 s: the hop, then the landing
place(chirp(300, 2400, 0.65, 0.8), 13.0, 0.09)
place(thump(120, 40, 0.8), 13.65, 0.42)
for f, g in ((880, 0.20), (1318.5, 0.11), (1760, 0.06), (2637, 0.03)):
    place(tone(f, 1.6, a=0.002, curve=3.4, harm=(1, 0.12)), 13.65, g)
place(sweep_noise(0.8, 800, 7000), 13.68, 0.03)

# --- master: gentle tilt, soft clip, normalise to -2 dBFS, 20 ms in / 0.35 s out
mix = np.tanh(mix * 1.4) / np.tanh(1.4)
fade = np.ones(N)
fi, fo = int(0.02 * SR), int(0.35 * SR)
fade[:fi] = np.linspace(0, 1, fi)
fade[-fo:] = np.linspace(1, 0, fo)
mix *= fade[:, None]
mix *= 10 ** (-2 / 20) / np.max(np.abs(mix))

out = Path(__file__).parent / "out"
out.mkdir(exist_ok=True)
with wave.open(str(out / "splash_audio.wav"), "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((mix * 32767).astype("<i2").tobytes())
print("wrote", out / "splash_audio.wav")
