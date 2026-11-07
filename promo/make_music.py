"""Synthesizes an original soundtrack timed to make_ad.py's scenes and muxes it in.

Usage:  python make_music.py                -> music.wav + audio added to focus_timer_ad.mp4
        python make_music.py --wav-only
Needs:  pip install numpy imageio-ffmpeg
"""
import os
import subprocess
import sys
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
SR, DUR = 44100, 24.0
N = int(SR * DUR)
BPM = 120
BEAT = 60 / BPM
BAR = 4 * BEAT
GROOVE_START, GROOVE_END = 3.0, 21.0      # phone arrives / end card resolves
rng = np.random.default_rng(7)

L = np.zeros(N)
R = np.zeros(N)


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def tt(dur):
    return np.arange(int(dur * SR)) / SR


def add(sig, start, gain=1.0, pan=0.0, bus=None):
    """Mix a mono signal in at `start` seconds; pan -1 (left) .. 1 (right)."""
    i = int(start * SR)
    if i >= N:
        return
    sig = sig[: N - i] * gain
    lb, rb = (L, R) if bus is None else bus
    lb[i:i + len(sig)] += sig * np.sqrt((1 - pan) / 2) * 1.414
    rb[i:i + len(sig)] += sig * np.sqrt((1 + pan) / 2) * 1.414


def tone(freq, dur, harmonics=6, bright=1.0, decay=None, attack=0.005, release=0.05, detune=0.0):
    """Band-limited additive tone; `bright` < 1 rolls off upper harmonics faster."""
    t = tt(dur)
    out = np.zeros_like(t)
    for k in range(1, harmonics + 1):
        amp = (1 / k) * bright ** (k - 1)
        ph = rng.uniform(0, 2 * np.pi)
        h = np.sin(2 * np.pi * freq * k * (1 + detune) * t + ph) * amp
        if decay:
            h *= np.exp(-t * decay * (1 + 0.6 * (k - 1)))
        out += h
    env = np.minimum(1, t / attack) * np.minimum(1, (dur - t) / release).clip(0)
    return out * env


def noise(dur):
    return rng.standard_normal(int(dur * SR))


def bright_noise(dur):
    n = noise(dur + 1 / SR)
    return np.diff(n) * 0.5


# ------------------------------------------------------------------ instruments
def kick():
    t = tt(0.45)
    f = 45 + 95 * np.exp(-t * 28)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t * 7.5) + np.sin(ph * 2) * 0.1 * np.exp(-t * 30)


def clap():
    t = tt(0.3)
    n = bright_noise(0.3) + noise(0.3) * 0.3
    env = np.exp(-t * 22)
    for d in (0.008, 0.017):
        env += 0.6 * np.exp(-np.clip(t - d, 0, None) * 120) * (t >= d)
    return n * env * 0.5


def hat(open_=False):
    t = tt(0.25 if open_ else 0.06)
    return bright_noise(len(t) / SR) * np.exp(-t * (18 if open_ else 70))


def crash(dur=2.2):
    t = tt(dur)
    return bright_noise(dur) * np.exp(-t * 2.2) * 0.6


def ping(midi, dur=0.5):
    t = tt(dur)
    f = hz(midi)
    return (np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 14)) \
        * np.exp(-t * 7) * np.minimum(1, t / 0.003)


def tick(high):
    t = tt(0.04)
    f = 3200 if high else 2400
    return (np.sin(2 * np.pi * f * t) * 0.6 + bright_noise(0.04) * 0.4) * np.exp(-t * 160)


def swoosh(dur, up=True):
    t = tt(dur)
    x = t / dur
    env = (x if up else 1 - x) ** 2 * np.sin(np.pi * np.clip(x * 1.02, 0, 1)) ** 0.3
    n = noise(dur)
    # crude moving-average lowpass that opens up over time
    out = np.zeros_like(n)
    for w, wt in ((24, 1 - x), (6, x), (2, x ** 2)):
        k = np.ones(w) / w
        out += np.convolve(n, k, mode="same") * wt
    return out * env


# ------------------------------------------------------------------ harmony
# one chord per bar from 3.0s: C  G  Am  F  C  G  Am  F  Dm7->G(sus) | 21.0 C
PROG = [
    ([60, 64, 67, 74], 36), ([59, 62, 67, 71], 31), ([57, 60, 64, 71], 33), ([57, 60, 65, 69], 29),
    ([60, 64, 67, 74], 36), ([59, 62, 67, 71], 31), ([57, 60, 64, 71], 33), ([57, 60, 65, 69], 29),
    ([57, 60, 62, 65], 38),
]
FINAL = ([55, 60, 64, 67, 72, 76], 36)

padL, padR = np.zeros(N), np.zeros(N)
pad_bus = (padL, padR)
fxL, fxR = np.zeros(N), np.zeros(N)
fx_bus = (fxL, fxR)                        # gets the echo/reverb

# --- hook (0-3s): quiet drone, clock ticks speeding up, notification pings
for m in (45, 52, 57):
    add(tone(hz(m), 3.2, harmonics=4, bright=.5, attack=1.2, release=.6), 0, .1, bus=pad_bus)
tk, i = 0.0, 0
while tk < 2.4:
    add(tick(i % 2 == 0), tk, .5, pan=-.2 if i % 2 else .2)
    tk += 0.5 if tk < 1.0 else 0.25 if tk < 1.8 else 0.125
    i += 1
for start, m, pan in ((0.28, 88, -.5), (0.48, 91, .5), (0.68, 86, -.4), (0.88, 93, .4)):
    add(ping(m), start, .32, pan, bus=fx_bus)
add(swoosh(0.9), 2.1, .35)
for m in (64, 67, 71, 72):                       # rising sweep into the drop
    add(tone(hz(m), .2, 3, .6, decay=6), 2.1 + (m - 64) * .1, .05, bus=fx_bus)

# --- groove (3-21s)
for b, (chord, root) in enumerate(PROG):
    t0 = GROOVE_START + b * BAR
    last = b == len(PROG) - 1
    # pad: soft additive chord, two detuned layers spread L/R
    for m in chord:
        for det, pan in ((-.003, -.6), (.003, .6)):
            add(tone(hz(m), BAR + .3, 7, .55, attack=.08, release=.35, detune=det), t0, .045, pan,
                bus=pad_bus)
    # bass: 8th-note pulse on the root with octave pops
    for k in range(8):
        m = root + (12 if k in (3, 7) else 0)
        if last and k >= 4:
            m = 31 + (12 if k % 2 else 0)        # walk to G under the fill
        add(tone(hz(m), BEAT / 2 - .01, 5, .45, decay=5, attack=.004, release=.03), t0 + k * BEAT / 2,
            .32)
    # pluck arpeggio, 16ths with a gap pattern
    arp = chord + [chord[1] + 12, chord[2] + 12]
    order = [0, 2, 4, 3, 5, 3, 4, 2, 1, 3, 5, 4, 2, 4, 3, 5]
    for k, oi in enumerate(order):
        if k % 8 == 7:
            continue
        add(tone(hz(arp[oi] + 12), .35, 6, .7, decay=9), t0 + k * BEAT / 4, .085,
            pan=.35 * np.sin(k), bus=fx_bus)
    # drums
    for k in range(4):
        bt = t0 + k * BEAT
        add(kick(), bt, .9)
        if k in (1, 3):
            add(clap(), bt, .42, .05)
        if t0 >= GROOVE_START + 2 * BAR:          # hats join on bar 3
            add(hat(), bt + BEAT / 2, .2, .3)
            add(hat(), bt, .1, -.3)
            if k == 3:
                add(hat(True), bt + BEAT / 2, .12, .3)
    if last:                                      # snare fill into the ending
        for k in range(8):
            add(clap(), t0 + 2 * BEAT + k * BEAT / 4, .16 + k * .035, (-1) ** k * .2)

# section-change swooshes, synced to the screen slides and extras
for t0 in (7.55, 12.05, 16.6):
    add(swoosh(.55), t0, .22, .3)
for i in range(8):                                # each feature chip pops in
    add(ping([72, 74, 76, 79, 81, 84, 86, 88][i], .3), 17.5 + .1 * i + .08, .07,
        -.5 if i % 2 == 0 else .5, bus=fx_bus)

# --- ending (21s): big resolved chord, crash, button pop
chord, root = FINAL
add(kick(), GROOVE_END, 1.0)
add(crash(), GROOVE_END, .18)
for m in chord:
    for det, pan in ((-.004, -.7), (.004, .7)):
        add(tone(hz(m), 3.0, 7, .55, attack=.02, release=1.5, detune=det), GROOVE_END, .05, pan,
            bus=pad_bus)
    add(tone(hz(m + 12), 2.5, 5, .6, decay=2.2), GROOVE_END, .05, bus=fx_bus)
add(tone(hz(root), 2.6, 4, .4, decay=1.2, release=1.0), GROOVE_END, .45)
add(ping(84, .8), 21.95, .14, bus=fx_bus)
add(ping(91, .8), 22.05, .1, bus=fx_bus)

# ------------------------------------------------------------------ mix
t = np.arange(N) / SR
# sidechain-style pump: the pad ducks under every kick during the groove
pos = (t - GROOVE_START) % BEAT
duck = np.where((t >= GROOVE_START) & (t < GROOVE_END), 1 - .55 * np.exp(-pos * 9), 1.0)
L += padL * duck
R += padR * duck

# dotted-8th ping-pong echo + a short smear for space on the fx bus
for dl, g, tgt in ((.375, .32, R), (.75, .2, L), (1.125, .12, R)):
    d = int(dl * SR)
    tgt[d:] += (fxL if tgt is R else fxR)[:-d] * g
for dl in (.023, .041, .067):
    d = int(dl * SR)
    L[d:] += fxR[:-d] * .25
    R[d:] += fxL[:-d] * .25
L += fxL
R += fxR

mix = np.stack([L, R], axis=1)
fade_in = np.minimum(1, t / .02)
fade_out = np.clip((DUR - t) / 1.2, 0, 1)
mix *= (fade_in * fade_out)[:, None]
mix = np.tanh(mix * 1.1)                          # gentle saturation/limiting
mix *= 0.75 / np.abs(mix).max()                   # headroom for AAC (~-1.5 dBTP)

wav = os.path.join(HERE, "music.wav")
with wave.open(wav, "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((mix * 32767).astype("<i2").tobytes())
print("wrote", wav)

if "--wav-only" not in sys.argv:
    import imageio_ffmpeg
    video = os.path.join(HERE, "focus_timer_ad.mp4")
    tmp = os.path.join(HERE, "_with_music.mp4")
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-hide_banner", "-loglevel", "error", "-y",
                    "-i", video, "-i", wav, "-map", "0:v", "-map", "1:a", "-c:v", "copy",
                    "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", tmp],
                   check=True)
    os.replace(tmp, video)
    print("muxed into", video)
