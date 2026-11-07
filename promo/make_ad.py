"""Renders a 9:16 promo video for Focus Timer -> focus_timer_ad.mp4.

Usage:  python make_ad.py            (full video)
        python make_ad.py --stills   (preview PNGs only)
Needs:  pip install pillow numpy imageio-ffmpeg
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONT = os.path.join(HERE, "..", "app", "src", "main", "res", "font", "nunito.ttf")

FPS, DUR = 30, 24.0
W, H, S = 1080, 1920, 2          # drawn at 2x, downscaled for anti-aliasing

INK = (30, 27, 46)
MUTED = (122, 116, 150)
ACC = (124, 108, 240)
ACC_L = (196, 187, 255)
GROOVE = (236, 232, 255)
ORANGE = (255, 138, 101)
GREEN = (47, 191, 143)
BLUE = (74, 157, 245)
PASTEL = [(230, 225, 255), (255, 228, 218), (213, 245, 233), (220, 235, 255),
          (255, 242, 199), (255, 224, 238)]
WHITE = (255, 255, 255)


def s(v):
    return int(round(v * S))


# ------------------------------------------------------------------ easing
def cl(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def p(t, start, dur):
    return cl((t - start) / dur)


def eo(x):
    return 1 - (1 - x) ** 3


def eio(x):
    return 4 * x ** 3 if x < .5 else 1 - (-2 * x + 2) ** 3 / 2


def back(x, k=1.7):
    x -= 1
    return 1 + (k + 1) * x ** 3 + k * x ** 2


def lerp(a, b, x):
    return a + (b - a) * x


# ------------------------------------------------------------------ drawing
_fonts = {}


def font(px, wt=800):
    key = (px, wt)
    if key not in _fonts:
        f = ImageFont.truetype(FONT, s(px))
        f.set_variation_by_axes([wt])
        _fonts[key] = f
    return _fonts[key]


def rr(d, x0, y0, x1, y1, r, fill):
    d.rounded_rectangle([s(x0), s(y0), s(x1), s(y1)], radius=s(r), fill=fill)


def circ(d, cx, cy, r, fill):
    d.ellipse([s(cx - r), s(cy - r), s(cx + r), s(cy + r)], fill=fill)


def arc(d, cx, cy, r, a0, a1, fill, width):
    if a1 - a0 < 0.5:
        return
    box = [s(cx - r), s(cy - r), s(cx + r), s(cy + r)]
    d.arc(box, a0, a1, fill=fill, width=s(width))
    rm = r - width / 2       # round caps
    for a in (a0, a1):
        circ(d, cx + rm * math.cos(math.radians(a)), cy + rm * math.sin(math.radians(a)),
             width / 2, fill)


def text(d, x, y, txt, px, wt, fill, anchor="la"):
    d.text((s(x), s(y)), txt, font=font(px, wt), fill=fill, anchor=anchor)


def mono(d, cx, cy, txt, px, wt, fill):
    """Digits on a fixed advance so a ticking clock doesn't wobble."""
    f = font(px, wt)
    dw = max(f.getlength(c) for c in "0123456789")
    widths = [dw if c.isdigit() else f.getlength(c) for c in txt]
    x = s(cx) - sum(widths) / 2
    for c, w in zip(txt, widths):
        d.text((x + w / 2, s(cy)), c, font=f, fill=fill, anchor="mm")
        x += w


def shadow_card(d, x0, y0, x1, y1, r, fill, tint=(124, 108, 240)):
    rr(d, x0 + 2, y0 + 14, x1 - 2, y1 + 14, r, tint + (22,))
    rr(d, x0, y0 + 6, x1, y1 + 6, r, tint + (20,))
    rr(d, x0, y0, x1, y1, r, fill)


def fade(layer, a):
    if a >= 1:
        return layer
    layer = layer.copy()
    layer.putalpha(layer.getchannel("A").point(lambda v: int(v * a)))
    return layer


def paste(base, layer, cx, cy, scale=1.0, alpha=1.0):
    if scale <= 0.01 or alpha <= 0.01:
        return
    if abs(scale - 1) > 1e-3:
        layer = layer.resize((max(1, int(layer.width * scale)),
                              max(1, int(layer.height * scale))), Image.BILINEAR)
    layer = fade(layer, alpha)
    base.paste(layer, (int(s(cx) - layer.width / 2), int(s(cy) - layer.height / 2)), layer)


_text_layers = {}


def text_layer(txt, px, wt, fill):
    key = (txt, px, wt, fill)
    if key not in _text_layers:
        f = font(px, wt)
        l, t, r, b = f.getbbox(txt)
        pad = s(6)
        im = Image.new("RGBA", (r - l + 2 * pad, b - t + 2 * pad), (0, 0, 0, 0))
        ImageDraw.Draw(im).text((pad - l, pad - t), txt, font=f, fill=fill)
        _text_layers[key] = im
    return _text_layers[key]


# ------------------------------------------------------------------ static assets
def make_background():
    top, bot = np.array((247, 245, 255)), np.array((255, 244, 238))
    g = np.linspace(0, 1, s(H))[:, None]
    col = (top * (1 - g) + bot * g).astype(np.uint8)
    arr = np.repeat(col[:, None, :], s(W), axis=1)
    return Image.fromarray(arr, "RGB")


def make_blob(color, radius):
    q = 4
    size = int(radius * 2.6 * S / q)
    im = Image.new("RGBA", (size, size), color + (0,))
    ImageDraw.Draw(im).ellipse([size * .2, size * .2, size * .8, size * .8], fill=color + (150,))
    im = im.filter(ImageFilter.GaussianBlur(size * .12))
    return im.resize((size * q, size * q), Image.BILINEAR)


BG = make_background()
BLOBS = [(make_blob((205, 196, 255), 260), 180, 300, 0.0),
         (make_blob((255, 205, 185), 230), 900, 1500, 1.7),
         (make_blob((190, 235, 215), 200), 950, 420, 3.1)]

PW, PH = 640, 1330               # phone
SX, SY = 18, 18                  # screen inset
SWD, SHT = PW - 2 * SX, PH - 2 * SY


def make_phone_body():
    im = Image.new("RGBA", (s(PW), s(PH)), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rr(d, 0, 0, PW, PH, 92, (32, 29, 48, 255))
    rr(d, 4, 4, PW - 4, PH - 4, 88, (52, 48, 72, 255))
    rr(d, 9, 9, PW - 9, PH - 9, 84, (24, 22, 36, 255))
    return im


def make_phone_shadow():
    q = 4
    im = Image.new("RGBA", (s(PW + 300) // q, s(PH + 300) // q), (90, 70, 200, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([s(150) // q, s(170) // q, s(PW + 150) // q, s(PH + 150) // q],
                        radius=s(90) // q, fill=(90, 70, 200, 90))
    im = im.filter(ImageFilter.GaussianBlur(s(45) / q))
    return im.resize((im.width * q, im.height * q), Image.BILINEAR)


PHONE_BODY = make_phone_body()
PHONE_SHADOW = make_phone_shadow()
SCREEN_MASK = Image.new("L", (s(SWD), s(SHT)), 0)
ImageDraw.Draw(SCREEN_MASK).rounded_rectangle([0, 0, s(SWD), s(SHT)], radius=s(74), fill=255)


# ------------------------------------------------------------------ app screens
SCREEN_BG = (245, 243, 252)
TABS = ["Timers", "Projects", "History", "Settings"]


def status_bar(d):
    text(d, 52, 42, "9:41", 22, 800, INK, "lm")
    circ(d, SWD / 2, 40, 11, (20, 18, 30))
    for i in range(3):
        rr(d, SWD - 120 + i * 14, 48 - 8 - i * 5, SWD - 112 + i * 14, 50, 2, INK)
    rr(d, SWD - 70, 31, SWD - 34, 51, 6, INK)


def header(d, label, title):
    text(d, 44, 120, label, 21, 800, MUTED, "lm")
    text(d, 42, 178, title, 58, 900, INK, "lm")


def nav(d, active):
    shadow_card(d, 28, 1150, SWD - 28, 1252, 51, WHITE)
    tw = (SWD - 56) / 4
    for i, name in enumerate(TABS):
        cx = 28 + tw * (i + .5)
        if i == active:
            rr(d, cx - 62, 1170, cx + 62, 1232, 31, PASTEL[0])
        text(d, cx, 1201, name, 20, 900 if i == active else 700,
             ACC if i == active else MUTED, "mm")


def ripple(d, cx, cy, u):
    if 0 < u < 1:
        circ(d, cx, cy, lerp(20, 90, eo(u)), ACC + (int(90 * (1 - u)),))


def screen_timer(u):
    im = Image.new("RGB", (s(SWD), s(SHT)), SCREEN_BG)
    d = ImageDraw.Draw(im, "RGBA")
    status_bar(d)
    header(d, "TODAY  ·  2 SESSIONS DONE", "Focus")

    start = 0.9                               # the "tap" on play
    total = 25 * 60
    left = total - max(0.0, u - start) * 85   # fast-forwarded countdown
    frac = left / total

    shadow_card(d, 36, 250, SWD - 36, 820, 56, WHITE)
    cx, cy, r = SWD / 2, 520, 200
    d.ellipse([s(cx - r), s(cy - r), s(cx + r), s(cy + r)], outline=GROOVE, width=s(36))
    arc(d, cx, cy, r, -90, -90 + 360 * frac, ACC, 36)
    m, sec = divmod(int(left), 60)
    mono(d, cx, cy - 10, f"{m:02d}:{sec:02d}", 104, 800, INK)
    text(d, cx, cy + 72, "DEEP WORK" if u > start else "READY", 22, 900, MUTED, "mm")

    by = 960
    circ(d, 150, by, 56, (234, 230, 248))
    text(d, 150, by, "+1m", 24, 900, INK, "mm")
    circ(d, SWD - 150, by, 56, (234, 230, 248))
    rr(d, SWD - 168, by - 18, SWD - 132, by + 18, 6, INK)

    press = 1 - 0.08 * math.sin(math.pi * p(u, start - .15, .3))
    br = 80 * press
    circ(d, cx, by + 12, br, ACC + (60,))
    circ(d, cx, by, br, ACC)
    if u < start:
        d.polygon([(s(cx - 20), s(by - 30)), (s(cx - 20), s(by + 30)), (s(cx + 32), s(by))],
                  fill=WHITE)
    else:
        rr(d, cx - 24, by - 28, cx - 8, by + 28, 5, WHITE)
        rr(d, cx + 8, by - 28, cx + 24, by + 28, 5, WHITE)
    ripple(d, cx, by, p(u, start - .1, .7))

    nav(d, 0)
    ripple(d, 28 + (SWD - 56) / 4 * 1.5, 1201, p(u, 4.55, .6))
    return im


PROJECTS = [("Study", 0, ACC, 400, .82), ("Work", 1, ORANGE, 255, .55),
            ("Reading", 2, GREEN, 125, .32), ("Side project", 3, BLUE, 90, .22)]


def hm(minutes):
    h, m = divmod(int(minutes), 60)
    return f"{h}h {m:02d}m" if h else f"{m}m"


def screen_projects(u):
    im = Image.new("RGB", (s(SWD), s(SHT)), SCREEN_BG)
    d = ImageDraw.Draw(im, "RGBA")
    status_bar(d)
    header(d, "4 ACTIVE PROJECTS", "Projects")
    grow = eo(p(u, .6, 1.6))
    for i, (name, pi, col, mins, prog) in enumerate(PROJECTS):
        k = eo(p(u, .1 + .14 * i, .6))
        dx = (1 - k) * 520
        y = 262 + i * 206
        x0, x1 = 36 + dx, SWD - 36 + dx
        shadow_card(d, x0, y, x1, y + 180, 44, PASTEL[pi], col)
        circ(d, x0 + 62, y + 66, 24, col)
        circ(d, x0 + 62, y + 66, 10, WHITE)
        text(d, x0 + 104, y + 52, name, 34, 900, INK, "lm")
        text(d, x0 + 104, y + 90, "this week", 20, 700, MUTED, "lm")
        text(d, x1 - 36, y + 66, hm(mins * grow), 32, 900, INK, "rm")
        rr(d, x0 + 38, y + 130, x1 - 38, y + 148, 9, (255, 255, 255, 170))
        rr(d, x0 + 38, y + 130, x0 + 38 + max(18, (x1 - x0 - 76) * prog * grow), y + 148, 9, col)
    nav(d, 1)
    ripple(d, 28 + (SWD - 56) / 4 * 2.5, 1201, p(u, 4.05, .6))
    return im


BARS = [.42, .68, .55, .92, .64, .3, .8]


def screen_history(u):
    im = Image.new("RGB", (s(SWD), s(SHT)), SCREEN_BG)
    d = ImageDraw.Draw(im, "RGBA")
    status_bar(d)
    header(d, "THIS WEEK", "History")

    shadow_card(d, 36, 250, SWD - 36, 420, 44, WHITE)
    text(d, 72, 296, "Total focus", 22, 800, MUTED, "lm")
    text(d, 70, 360, hm(870 * eo(p(u, .2, 1.8))), 60, 900, INK, "lm")
    a = p(u, 1.4, .4)
    if a > 0:
        rr(d, SWD - 222, 330, SWD - 70, 382, 26, (213, 245, 233, int(255 * a)))
        text(d, SWD - 146, 356, "+23%", 26, 900, GREEN + (int(255 * a),), "mm")

    shadow_card(d, 36, 450, SWD - 36, 960, 44, WHITE)
    base, top = 880, 520
    bw, gap = 46, (SWD - 72 - 80 - 7 * 46) / 6
    for i, v in enumerate(BARS):
        k = back(p(u, .3 + .09 * i, .7), 1.2)
        x = 76 + i * (bw + gap)
        hgt = (base - top) * v * k
        if hgt > 4:
            rr(d, x, base - hgt, x + bw, base, 16, ACC if i == 3 else ACC_L)
        text(d, x + bw / 2, 918, "MTWTFSS"[i], 22, 800, INK if i == 3 else MUTED, "mm")

    k = eo(p(u, 1.6, .6))
    if k > 0:                                 # slides up from behind the tab bar
        y = 990 + (1 - k) * 200
        shadow_card(d, 36, y, SWD - 36, y + 120, 44, PASTEL[4], ORANGE)
        circ(d, 104, y + 60, 30, ORANGE)
        text(d, 104, y + 60, "5", 30, 900, WHITE, "mm")
        text(d, 152, y + 46, "5-day streak", 30, 900, INK, "lm")
        text(d, 152, y + 82, "Keep it going tomorrow", 20, 700, MUTED, "lm")
    nav(d, 2)
    return im


def screen_at(t):
    """Screen content with slide transitions between the three app screens."""
    scenes = [(3.0, screen_timer), (8.0, screen_projects), (12.5, screen_history)]
    for i, (st, fn) in enumerate(scenes):
        nxt = scenes[i + 1][0] if i + 1 < len(scenes) else 1e9
        if st - .45 <= t < nxt - .45 or i == 0 and t < st:
            k = eio(p(t, st - .45, .45)) if i > 0 else 1
            cur = fn(t - st)
            if k >= 1:
                return cur
            prev = scenes[i - 1][1](t - scenes[i - 1][0])
            out = Image.new("RGB", cur.size)
            off = int(cur.width * k)
            out.paste(prev, (-off, 0))
            out.paste(cur, (cur.width - off, 0))
            return out
    return scenes[-1][1](t - scenes[-1][0])


def phone_layer(t):
    layer = PHONE_BODY.copy()
    layer.paste(screen_at(t), (s(SX), s(SY)), SCREEN_MASK)
    return layer


# ------------------------------------------------------------------ scene helpers
def headline(base, t, lines, t_in, t_out, y=230, px=92):
    a_in = eo(p(t, t_in, .5))
    a_out = eio(p(t, t_out, .4))
    a = a_in * (1 - a_out)
    if a <= 0:
        return
    for i, (txt, col) in enumerate(lines):
        k = eo(p(t, t_in + .1 * i, .5))
        dy = (1 - k) * 50 - a_out * 50
        paste(base, text_layer(txt, px, 900, col), W / 2, y + i * (px * 1.12) + dy, alpha=a)


PILLS = [("12 new messages", BLUE, 300, 470, .25),
         ("Someone liked your post", ORANGE, 640, 610, .45),
         ("Breaking news!", (240, 80, 110), 380, 1270, .65),
         ("Your friend is live", GREEN, 700, 1420, .85)]
_pill_cache = {}


def pill_layer(txt, col):
    if txt not in _pill_cache:
        f = font(32, 800)
        tw = f.getlength(txt) / S
        w, h = tw + 130, 92
        im = Image.new("RGBA", (s(w + 20), s(h + 30)), (0, 0, 0, 0))
        d = ImageDraw.Draw(im, "RGBA")
        rr(d, 10, 22, w + 10, h + 22, 46, (90, 70, 160, 30))
        rr(d, 10, 10, w + 10, h + 10, 46, WHITE)
        circ(d, 58, 10 + h / 2, 20, col)
        text(d, 92, 10 + h / 2, txt, 32, 800, INK, "lm")
        _pill_cache[txt] = im
    return _pill_cache[txt]


FEATURES = [("Home-screen widget", ACC), ("Live notifications", ORANGE),
            ("Quick Settings tile", GREEN), ("Pomodoro presets", (240, 80, 110)),
            ("Notes on sessions", BLUE), ("Dark mode", (90, 80, 140)),
            ("10 languages", GREEN), ("Backup & restore", ACC)]


def feature_layer(txt, col):
    key = "f" + txt
    if key not in _pill_cache:
        w, h = 470, 140
        im = Image.new("RGBA", (s(w + 20), s(h + 34)), (0, 0, 0, 0))
        d = ImageDraw.Draw(im, "RGBA")
        rr(d, 12, 26, w + 8, h + 24, 40, col + (40,))
        rr(d, 10, 10, w + 10, h + 10, 40, WHITE)
        circ(d, 72, 10 + h / 2, 30, col + (45,))
        circ(d, 72, 10 + h / 2, 13, col)
        text(d, 120, 10 + h / 2, txt, 31, 900, INK, "lm")
        _pill_cache[key] = im
    return _pill_cache[key]


def draw_icon(base, cx, cy, size, ring):
    """The launcher icon (ic_timer_foreground + background), redrawn at any size."""
    k = size / 108
    lay = Image.new("RGBA", (s(size), s(size)), (0, 0, 0, 0))
    g = np.linspace(0, 1, s(size))
    gg = (g[:, None] + g[None, :]) / 2
    c0, c1 = np.array((182, 169, 255)), np.array((124, 108, 240))
    grad = (c0 * (1 - gg[..., None]) + c1 * gg[..., None]).astype(np.uint8)
    gi = Image.fromarray(grad, "RGB")
    m = Image.new("L", lay.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, s(size), s(size)], radius=s(size * .26), fill=255)
    lay.paste(gi, (0, 0), m)
    d = ImageDraw.Draw(lay, "RGBA")
    circ(d, 54 * k, 62 * k, 23 * k * 1.35, (42, 31, 107, 51))
    circ(d, 54 * k, 56 * k, 23 * k * 1.35, (228, 222, 255))
    circ(d, 53 * k, 55 * k, 20 * k * 1.35, WHITE)
    rg = 13 * k * 1.35
    d.ellipse([s(54 * k - rg - 3 * k), s(56 * k - rg - 3 * k), s(54 * k + rg + 3 * k),
               s(56 * k + rg + 3 * k)], outline=(236, 232, 255), width=s(6 * k * 1.2))
    arc(d, 54 * k, 56 * k, rg + 3 * k, -90, -90 + 270 * ring, ACC, 6 * k * 1.2)
    rr(d, 45 * k, 22 * k, 63 * k, 29 * k, 3.5 * k, WHITE)
    paste(base, lay, cx, cy)


# ------------------------------------------------------------------ frame
def render(t):
    base = BG.copy()
    for blob, bx, by, ph in BLOBS:
        x = bx + 40 * math.sin(t * .5 + ph)
        y = by + 50 * math.cos(t * .4 + ph)
        base.paste(blob, (s(x) - blob.width // 2, s(y) - blob.height // 2), blob)
    d = ImageDraw.Draw(base, "RGBA")

    # 1. hook: distractions
    if t < 3.0:
        out = eio(p(t, 2.1, .45))
        for txt, col, x, y, t0 in PILLS:
            k = back(p(t, t0, .45))
            jig = 6 * math.sin(t * 9 + x)
            paste(base, pill_layer(txt, col), x + jig, y - out * 40, scale=k * (1 - out),
                  alpha=1 - out)
        k = back(p(t, 0, .5))
        paste(base, text_layer("Can't focus?", 128, 900, INK), W / 2, 880 - out * 120,
              scale=k, alpha=1 - out)
        paste(base, text_layer("Your phone keeps winning.", 46, 800, MUTED), W / 2,
              1010 + (1 - eo(p(t, .9, .5))) * 30 - out * 120, alpha=eo(p(t, .9, .5)) * (1 - out))

    # 2-4. the phone demo
    if 2.3 < t < 17.8:
        rise = eo(p(t, 2.3, .9))
        drop = eio(p(t, 16.9, .8))
        py = 520 + (1 - rise) * 1500 + drop * 1500
        lay = phone_layer(t)
        base.paste(PHONE_SHADOW, (s(W / 2 - PW / 2 - 150), s(py - 150)), PHONE_SHADOW)
        base.paste(lay, (s(W / 2 - PW / 2), s(py)), lay)
    headline(base, t, [("One tap.", INK), ("Deep focus.", ACC)], 3.0, 7.7, y=200)
    headline(base, t, [("Track every", INK), ("project.", ACC)], 8.0, 12.2, y=200)
    headline(base, t, [("Watch your", INK), ("progress grow.", ACC)], 12.5, 16.8, y=200)

    # 5. extras
    if 17.0 < t < 21.0:
        headline(base, t, [("Packed with", INK), ("the good stuff.", ACC)], 17.1, 20.2, y=260)
        out = eio(p(t, 20.2, .4))
        for i, (txt, col) in enumerate(FEATURES):
            k = back(p(t, 17.5 + .1 * i, .45))
            x = 285 if i % 2 == 0 else 795
            y = 660 + (i // 2) * 190
            paste(base, feature_layer(txt, col), x, y, scale=k * (1 - out * .3), alpha=1 - out)

    # 6. end card
    if t > 20.4:
        k = back(p(t, 20.5, .6))
        draw_icon(base, W / 2, 700, 300 * k, eo(p(t, 20.8, 1.0)))
        a = eo(p(t, 21.1, .5))
        paste(base, text_layer("Focus Timer", 120, 1000, INK), W / 2, 980 + (1 - a) * 40, alpha=a)
        a2 = eo(p(t, 21.4, .5))
        paste(base, text_layer("Make every minute count.", 48, 800, MUTED), W / 2,
              1090 + (1 - a2) * 30, alpha=a2)
        kb = back(p(t, 21.9, .5)) * (1 + .03 * math.sin(max(0, t - 22.4) * 4))
        if kb > .01:
            bl = Image.new("RGBA", (s(560), s(150)), (0, 0, 0, 0))
            bd = ImageDraw.Draw(bl, "RGBA")
            rr(bd, 10, 26, 550, 136, 55, ACC + (70,))
            rr(bd, 10, 10, 550, 120, 55, ACC)
            text(bd, 280, 65, "Get it on Android", 40, 900, WHITE, "mm")
            paste(base, bl, W / 2, 1300, scale=kb)

    return base.resize((W, H), Image.LANCZOS)


def main():
    if "--stills" in sys.argv:
        for t in (1.6, 4.5, 6.6, 10.0, 15.0, 18.8, 23.0):
            render(t).save(os.path.join(HERE, f"still_{t:04.1f}.png"))
        return
    import imageio_ffmpeg
    out = os.path.join(HERE, "focus_timer_ad.mp4")
    w = imageio_ffmpeg.write_frames(out, (W, H), fps=FPS, codec="libx264", pix_fmt_out="yuv420p",
                                    macro_block_size=1,
                                    output_params=["-crf", "17", "-preset", "slow",
                                                   "-movflags", "+faststart"])
    w.send(None)
    n = int(DUR * FPS)
    for i in range(n):
        w.send(np.ascontiguousarray(np.asarray(render(i / FPS))))
        if i % 60 == 0:
            print(f"frame {i}/{n}", flush=True)
    w.close()
    print("wrote", out)


if __name__ == "__main__":
    main()
