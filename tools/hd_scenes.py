#!/usr/bin/env python3
"""Escenas HD (10 capas) lienzo 320x512. Copia de social/tools/gen_capes_anim.py (solo se importa CAPES; la salida la hace gen_capes_hd.py)."""
import os, sys, math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageChops

W, H, FR = 320, 512, 12
TAU = 2 * math.pi

def grad(top, bot, mid=None):
    t = np.linspace(0, 1, H)[:, None]
    top, bot = np.array(top, np.float32), np.array(bot, np.float32)
    if mid is None:
        c = top * (1 - t) + bot * t
    else:
        mid = np.array(mid, np.float32)
        c = np.where(t < 0.5, top * (1 - t * 2) + mid * (t * 2), mid * (1 - (t - 0.5) * 2) + bot * ((t - 0.5) * 2))
    a = np.repeat(c[:, None, :], W, axis=1)
    return Image.fromarray(a.astype(np.uint8), 'RGB').convert('RGBA')

def layer():
    return Image.new('RGBA', (W, H), (0, 0, 0, 0))

def over(im, lay, blur=0):
    if blur: lay = lay.filter(ImageFilter.GaussianBlur(blur))
    return Image.alpha_composite(im, lay)

def glow(im, xy, r, color, a=1.0):
    l = layer(); ImageDraw.Draw(l).ellipse([xy[0] - r, xy[1] - r, xy[0] + r, xy[1] + r], fill=tuple(int(c) for c in color) + (int(255 * max(0, min(1, a))),))
    return over(im, l, r * 0.55)

def frame(im, color, inner=None, t=0.0):
    d = ImageDraw.Draw(im)
    d.rectangle([6, 6, W - 7, H - 7], outline=color + (255,), width=6)
    d.rectangle([20, 20, W - 21, H - 21], outline=(inner or color) + (150,), width=2)
    # destello que recorre el marco
    k = (t * 4) % 1.0
    pos = k * (2 * (W + H))
    pts = []
    for off in range(-40, 41, 8):
        p = (pos + off) % (2 * (W + H))
        if p < W: xy = (p, 8)
        elif p < W + H: xy = (W - 8, p - W)
        elif p < 2 * W + H: xy = (W - (p - W - H), H - 8)
        else: xy = (8, H - (p - 2 * W - H))
        a = int(220 * (1 - abs(off) / 44))
        d.ellipse([xy[0] - 4, xy[1] - 4, xy[0] + 4, xy[1] + 4], fill=(255, 255, 255, a))
    return im

def twinkle(im, seed, n, t, color=(255, 255, 255), ymin=30, ymax=H - 30, size=(3, 9), k=1):
    rnd = random.Random(seed)
    l = layer(); d = ImageDraw.Draw(l)
    for _ in range(n):
        x, y, s, ph = rnd.randint(30, W - 30), rnd.randint(ymin, ymax), rnd.randint(*size), rnd.random()
        a = max(0.0, math.sin(TAU * (t * k + ph))) ** 2
        if a < 0.05: continue
        al = int(255 * a)
        d.line([x - s * a - 1, y, x + s * a + 1, y], fill=color + (al,), width=2)
        d.line([x, y - s * a - 1, x, y + s * a + 1], fill=color + (al,), width=2)
        d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(255, 255, 255, al))
    return over(im, l)

def rising(im, seed, n, t, color, rmin=2, rmax=5, speed=1, sway=12, alpha=210, ymin=0, ymax=H, up=True):
    rnd = random.Random(seed)
    l = layer(); d = ImageDraw.Draw(l)
    for _ in range(n):
        x0, y0, r, ph = rnd.randint(25, W - 25), rnd.randint(0, H), rnd.uniform(rmin, rmax), rnd.random()
        y = (y0 - (1 if up else -1) * H * t * speed) % H
        if y < ymin or y > ymax: continue
        x = x0 + sway * math.sin(TAU * (t * speed + ph))
        fade = min(1.0, min(y - ymin, ymax - y) / 60)
        d.ellipse([x - r, y - r, x + r, y + r], fill=color + (int(alpha * fade),))
    return over(im, l, 1.2)

def clipmask(poly_fn):
    m = Image.new('L', (W, H), 0); poly_fn(ImageDraw.Draw(m)); return m

def sweep(im, mask, t, width=46, strength=190, ang=0.5, k=1):
    """Banda de brillo diagonal que cruza la forma 'mask' una vez por bucle."""
    l = Image.new('L', (W, H), 0); d = ImageDraw.Draw(l)
    c = -160 + ((t * k) % 1.0) * (W + H + 320)
    d.polygon([(c - width, 0), (c + width, 0), (c + width - H * ang, H), (c - width - H * ang, H)], fill=255)
    l = l.filter(ImageFilter.GaussianBlur(width * 0.35))
    band = ImageChops.multiply(l, mask).point(lambda v: int(v * strength / 255))
    white = Image.new('RGBA', (W, H), (255, 255, 255, 0)); white.putalpha(band)
    return Image.alpha_composite(im, white)

def star_pts(cx, cy, ro, ri, n=5, rot=-90):
    return [(cx + (ro if i % 2 == 0 else ri) * math.cos(math.radians(rot + i * 180 / n)),
             cy + (ro if i % 2 == 0 else ri) * math.sin(math.radians(rot + i * 180 / n))) for i in range(n * 2)]

def rays(im, cx, cy, n, t, color, alpha, r=420, hw=0.07, spin=1):
    l = layer(); d = ImageDraw.Draw(l)
    rot = (t * spin) * TAU / n
    for i in range(n):
        a = rot + i * TAU / n
        d.polygon([(cx, cy), (cx + r * math.cos(a - hw), cy + r * math.sin(a - hw)), (cx + r * math.cos(a + hw), cy + r * math.sin(a + hw))], fill=color + (alpha,))
    return over(im, l, 6)

# ------------------------------------------------------------------ capas
def rey(t):
    im = grad((14, 8, 42), (84, 34, 170), (44, 18, 110))
    im = rays(im, 160, 255, 12, t, (255, 220, 120), 70)
    pulse = 0.5 + 0.12 * math.sin(TAU * t)
    im = glow(im, (160, 255), 150, (150, 100, 255), pulse)
    im = twinkle(im, 1, 22, t, (255, 235, 160), k=2)
    im = rising(im, 2, 18, t, (255, 220, 120), 1.5, 3.5, 1, 10, 200)
    pts = [(80, 300), (80, 190), (122, 250), (160, 170), (198, 250), (240, 190), (240, 300)]
    l = layer(); ImageDraw.Draw(l).polygon(pts, fill=(255, 200, 60, int(170 + 60 * math.sin(TAU * t))))
    im = over(im, l, 14)
    d = ImageDraw.Draw(im)
    d.polygon(pts, fill=(255, 205, 64, 255), outline=(150, 100, 10, 255))
    d.polygon([(80, 300), (80, 190), (100, 220), (100, 300)], fill=(255, 235, 140, 255))
    d.rectangle([80, 296, 240, 352], fill=(240, 180, 40, 255), outline=(150, 100, 10, 255), width=4)
    d.rectangle([80, 296, 240, 308], fill=(255, 232, 130, 255)); d.rectangle([80, 340, 240, 352], fill=(190, 130, 20, 255))
    for i, (x, c) in enumerate([(120, (230, 30, 60)), (160, (40, 210, 255)), (200, (230, 30, 60))]):
        r = 13 + 2.2 * math.sin(TAU * (t + i / 3))
        d.ellipse([x - r, 326 - r, x + r, 326 + r], fill=c + (255,), outline=(255, 255, 255, 230), width=2)
        d.ellipse([x - 7, 317, x - 1, 323], fill=(255, 255, 255, 210))
    for x, y in [(80, 190), (160, 170), (240, 190)]:
        d.ellipse([x - 12, y - 12, x + 12, y + 12], fill=(255, 235, 140, 255), outline=(150, 100, 10, 255), width=3)
    mask = clipmask(lambda dd: (dd.polygon(pts, fill=255), dd.rectangle([80, 296, 240, 352], fill=255)))
    im = sweep(im, mask, t, 40, 200)
    return frame(im, (255, 205, 64), (255, 240, 170), t)

def tormenta(t):
    flash = math.exp(-((t - 0.25) / 0.045) ** 2) + 0.7 * math.exp(-((t - 0.36) / 0.04) ** 2)
    im = grad((4, 8, 30), (34, 56, 150), (16, 26, 86))
    for i, (cx, cy, r) in enumerate([(70, 80, 70), (250, 60, 85), (160, 130, 95), (40, 190, 60), (285, 200, 66)]):
        x = (cx + t * W * (1 if i % 2 else 2)) % (W + 160) - 80
        im = glow(im, (x, cy), r, (70 + 90 * flash, 85 + 90 * flash, 150 + 80 * flash), 0.55)
    # lluvia
    rnd = random.Random(7); l = layer(); d = ImageDraw.Draw(l)
    for _ in range(48):
        x0, y0, ln = rnd.randint(0, W), rnd.randint(0, H), rnd.randint(22, 44)
        y = (y0 + H * t * 3) % H; x = x0 - 0.25 * (y - y0)
        d.line([x, y, x - 6, y + ln], fill=(170, 200, 255, 120), width=2)
    im = over(im, l)
    pts = [(205, 60), (95, 280), (160, 280), (112, 470), (245, 230), (178, 230), (252, 60)]
    a = 0.2 + 0.8 * min(1.0, flash)
    l = layer(); ImageDraw.Draw(l).polygon(pts, fill=(255, 235, 80, int(255 * a)))
    im = over(im, l, 26)
    l = layer(); d = ImageDraw.Draw(l)
    d.polygon(pts, fill=(255, 225, 40, int(255 * a)), outline=(255, 255, 255, int(255 * a)))
    d.polygon([(205, 98), (122, 272), (172, 272), (132, 428), (222, 240), (166, 240), (226, 98)], fill=(255, 250, 210, int(255 * a)))
    im = over(im, l)
    if flash > 0.05:
        w = Image.new('RGBA', (W, H), (210, 225, 255, int(110 * min(1, flash)))); im = Image.alpha_composite(im, w)
    return frame(im, (255, 225, 40), (140, 170, 255), t)

def calavera(t):
    im = grad((6, 4, 8), (90, 8, 14), (28, 4, 10))
    im = glow(im, (160, 250), 150, (220, 30, 40), 0.35 + 0.1 * math.sin(TAU * t))
    d = ImageDraw.Draw(im)
    for (x0, y0, x1, y1) in [(70, 330, 250, 460), (250, 330, 70, 460)]:
        d.line([x0, y0, x1, y1], fill=(225, 220, 205, 255), width=22)
        for (x, y) in [(x0, y0), (x1, y1)]: d.ellipse([x - 18, y - 18, x + 18, y + 18], fill=(225, 220, 205, 255))
    # llamas (3 capas)
    for layer_i, (col, hmul, wid) in enumerate([((150, 20, 10), 1.0, 46), ((255, 110, 20), 0.75, 36), ((255, 225, 90), 0.5, 24)]):
        l = layer(); dd = ImageDraw.Draw(l)
        for i in range(9):
            bx = 18 + i * 35
            h = (95 + 35 * math.sin(TAU * (t + i * 0.37 + layer_i * 0.11))) * hmul + 30
            sway = 14 * math.sin(TAU * (t + i * 0.23))
            dd.polygon([(bx - wid / 2, H), (bx + sway, H - h * 1.6), (bx + wid / 2, H)], fill=col + (235,))
        im = over(im, l, 5)
    im = rising(im, 3, 22, t, (255, 170, 50), 1.5, 3.5, 2, 14, 230, 0, 470)
    d = ImageDraw.Draw(im)
    bone = (240, 236, 224, 255)
    d.ellipse([72, 120, 248, 296], fill=bone, outline=(60, 50, 50, 255), width=5)
    d.rounded_rectangle([108, 260, 212, 340], radius=18, fill=bone, outline=(60, 50, 50, 255), width=5)
    d.ellipse([100, 195, 148, 250], fill=(10, 6, 8, 255)); d.ellipse([172, 195, 220, 250], fill=(10, 6, 8, 255))
    g = 0.6 + 0.4 * math.sin(TAU * t * 2)
    im = glow(im, (124, 222), 18, (255, 60, 60), g); im = glow(im, (196, 222), 18, (255, 60, 60), g)
    d = ImageDraw.Draw(im)
    d.ellipse([116, 214, 132, 230], fill=(255, 90, 90, 255)); d.ellipse([188, 214, 204, 230], fill=(255, 90, 90, 255))
    d.polygon([(160, 252), (146, 282), (174, 282)], fill=(10, 6, 8, 255))
    for x in range(124, 205, 20): d.line([x, 302, x, 336], fill=(60, 50, 50, 255), width=4)
    d.line([112, 320, 208, 320], fill=(60, 50, 50, 255), width=4)
    return frame(im, (210, 30, 40), (255, 130, 100), t)

def luna(t):
    im = grad((4, 5, 26), (118, 84, 150), (30, 38, 100))
    im = twinkle(im, 3, 34, t, (255, 255, 255), 20, 340, (2, 7), k=1)
    im = twinkle(im, 13, 14, t, (200, 220, 255), 20, 340, (3, 8), k=2)
    # estrella fugaz
    s = (t - 0.55) / 0.25
    if 0 <= s <= 1:
        l = layer(); d = ImageDraw.Draw(l)
        x, y = 250 - 210 * s, 40 + 150 * s
        for i in range(14):
            d.ellipse([x + i * 6 - 3, y - i * 4 - 3, x + i * 6 + 3, y - i * 4 + 3], fill=(255, 255, 255, int(220 * (1 - i / 14))))
        im = over(im, l, 1.5)
    im = glow(im, (160, 190), 130 + 8 * math.sin(TAU * t), (200, 210, 255), 0.5)
    d = ImageDraw.Draw(im)
    d.ellipse([70, 100, 250, 280], fill=(246, 242, 222, 255))
    for (x, y, r) in [(120, 150, 20), (190, 140, 14), (180, 210, 24), (110, 225, 12), (215, 185, 9)]:
        d.ellipse([x - r, y - r, x + r, y + r], fill=(214, 208, 184, 255))
    d.pieslice([70, 100, 250, 280], 120, 300, fill=(228, 224, 204, 130))
    # nubes que cruzan la luna
    for i, (cy, r) in enumerate([(170, 60), (240, 48)]):
        x = (-120 + (t * (1 + i)) * (W + 240) + i * 110) % (W + 240) - 60
        im = glow(im, (x, cy), r, (40, 46, 110), 0.55)
    d = ImageDraw.Draw(im)
    d.polygon([(0, 440), (70, 380), (130, 420), (210, 350), (290, 410), (320, 390), (320, 512), (0, 512)], fill=(14, 12, 34, 255))
    for x, y, s2 in [(40, 440, 40), (95, 455, 30), (240, 430, 46), (285, 450, 32)]:
        d.polygon([(x, y - s2 * 2), (x - s2 * 0.6, y), (x + s2 * 0.6, y)], fill=(8, 6, 22, 255))
    # luciernagas
    rnd = random.Random(5); l = layer(); dd = ImageDraw.Draw(l)
    for _ in range(11):
        x0, y0, ph = rnd.randint(30, 290), rnd.randint(380, 490), rnd.random()
        x = x0 + 16 * math.sin(TAU * (t + ph)); y = y0 + 10 * math.cos(TAU * (t * 2 + ph))
        a = 0.4 + 0.6 * (0.5 + 0.5 * math.sin(TAU * (t * 2 + ph)))
        dd.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(255, 245, 150, int(255 * a)))
    im = over(im, l, 2)
    return frame(im, (190, 200, 255), (255, 255, 255), t)

def corazon(t):
    im = grad((52, 8, 40), (206, 30, 104), (136, 16, 74))
    tt = (t * 2) % 1.0
    beat = math.exp(-((tt - 0.12) / 0.07) ** 2) + 0.6 * math.exp(-((tt - 0.32) / 0.07) ** 2)
    im = glow(im, (160, 255), 140 + 20 * beat, (255, 80, 150), 0.45 + 0.2 * beat)
    # onda
    l = layer(); d = ImageDraw.Draw(l); r = 70 + 150 * tt
    d.ellipse([160 - r, 255 - r, 160 + r, 255 + r], outline=(255, 190, 220, int(150 * (1 - tt))), width=4)
    im = over(im, l, 1.5)
    im = twinkle(im, 4, 18, t, (255, 220, 235), k=2)
    # corazoncitos que suben
    rnd = random.Random(11); l = layer(); d = ImageDraw.Draw(l)
    for _ in range(9):
        x0, y0, s, ph = rnd.randint(30, 290), rnd.randint(0, H), rnd.uniform(0.35, 0.6), rnd.random()
        y = (y0 - H * t * (1 + (ph > 0.5))) % H; x = x0 + 14 * math.sin(TAU * (t + ph))
        pts = [(x + 16 * s * math.sin(a) ** 3, y - s * (13 * math.cos(a) - 5 * math.cos(2 * a) - 2 * math.cos(3 * a) - math.cos(4 * a))) for a in np.linspace(0, TAU, 40)]
        d.polygon(pts, fill=(255, 160, 200, 200))
    im = over(im, l, 0.8)
    sc = 8.4 * (1 + 0.1 * beat)
    pts = []
    for i in range(200):
        a = i / 200 * TAU
        pts.append((160 + 16 * math.sin(a) ** 3 * sc, 255 - (13 * math.cos(a) - 5 * math.cos(2 * a) - 2 * math.cos(3 * a) - math.cos(4 * a)) * sc))
    l = layer(); ImageDraw.Draw(l).polygon(pts, fill=(255, 70, 130, 255)); im = over(im, l, 22)
    d = ImageDraw.Draw(im)
    d.polygon(pts, fill=(255, 70, 125, 255), outline=(255, 215, 230, 255))
    small = [(160 + (x - 160) * 0.78, 255 + (y - 255) * 0.78 - 6) for x, y in pts]
    d.polygon(small, fill=(255, 100, 150, 255))
    d.ellipse([92, 150, 132, 186], fill=(255, 235, 242, 230)); d.ellipse([108, 192, 120, 204], fill=(255, 235, 242, 200))
    mask = clipmask(lambda dd: dd.polygon(pts, fill=255))
    im = sweep(im, mask, t, 34, 150)
    return frame(im, (255, 120, 170), (255, 220, 235), t)

def diamante(t):
    im = grad((4, 16, 48), (14, 74, 150), (8, 40, 100))
    im = rays(im, 160, 270, 10, t, (120, 220, 255), 60, spin=1)
    im = glow(im, (160, 270), 140, (80, 220, 255), 0.45 + 0.1 * math.sin(TAU * t))
    top = [(105, 160), (215, 160), (250, 215), (70, 215)]
    l = layer(); ImageDraw.Draw(l).polygon(top + [(160, 400)], fill=(120, 230, 255, 255)); im = over(im, l, 20)
    d = ImageDraw.Draw(im)
    d.polygon(top, fill=(150, 240, 255, 255))
    d.polygon([(70, 215), (160, 215), (160, 400)], fill=(40, 170, 230, 255))
    d.polygon([(160, 215), (250, 215), (160, 400)], fill=(20, 130, 205, 255))
    d.polygon([(70, 215), (115, 215), (160, 400)], fill=(90, 210, 250, 255))
    d.polygon([(205, 215), (250, 215), (160, 400)], fill=(10, 100, 180, 255))
    d.polygon([(105, 160), (160, 160), (130, 215), (70, 215)], fill=(200, 250, 255, 255))
    d.polygon([(160, 160), (215, 160), (250, 215), (190, 215)], fill=(110, 215, 245, 255))
    d.line(top + [top[0]], fill=(255, 255, 255, 255), width=4); d.line([(70, 215), (160, 400), (250, 215)], fill=(255, 255, 255, 255), width=4)
    mask = clipmask(lambda dd: dd.polygon(top + [(250, 215), (160, 400), (70, 215)], fill=255))
    # arcoiris que se desplaza por las facetas
    hue = np.zeros((H, W, 3), np.float32)
    yy, xx = np.mgrid[0:H, 0:W]
    hh = ((xx + yy * 0.6) / 260.0 - t) % 1.0
    r = np.clip(np.abs(hh * 6 - 3) - 1, 0, 1); g = np.clip(2 - np.abs(hh * 6 - 2), 0, 1); b = np.clip(2 - np.abs(hh * 6 - 4), 0, 1)
    rb = Image.fromarray((np.stack([r, g, b], -1) * 255).astype(np.uint8), 'RGB').convert('RGBA'); rb.putalpha(mask.point(lambda v: int(v * 0.30)))
    im = Image.alpha_composite(im, rb)
    im = sweep(im, mask, t, 30, 230, k=2)
    im = twinkle(im, 5, 14, t, (200, 245, 255), 30, 480, (6, 14), k=2)
    im = rising(im, 8, 14, t, (150, 235, 255), 2, 4, 1, 12, 200)
    return frame(im, (110, 220, 255), (255, 255, 255), t)

def brujula(t):
    im = grad((4, 22, 34), (12, 44, 78), (8, 32, 56))
    im = glow(im, (160, 256), 140, (60, 190, 210), 0.35 + 0.12 * math.sin(TAU * t))
    im = rising(im, 9, 16, t, (150, 240, 250), 1.5, 3.5, 1, 14, 190)
    d = ImageDraw.Draw(im)
    for r, w, c in [(120, 6, (212, 175, 55)), (104, 2, (255, 235, 150))]:
        d.ellipse([160 - r, 256 - r, 160 + r, 256 + r], outline=c + (255,), width=w)
    rot = t * 15
    for a in range(0, 360, 15):
        x0, y0 = 160 + 104 * math.cos(math.radians(a + rot)), 256 + 104 * math.sin(math.radians(a + rot))
        x1, y1 = 160 + 114 * math.cos(math.radians(a + rot)), 256 + 114 * math.sin(math.radians(a + rot))
        d.line([x0, y0, x1, y1], fill=(255, 235, 150, 255), width=2)
    for a in range(0, 360, 30):  # runas que giran al reves
        x, y = 160 + 132 * math.cos(math.radians(a - t * 30)), 256 + 132 * math.sin(math.radians(a - t * 30))
        d.polygon([(x, y - 6), (x + 5, y), (x, y + 6), (x - 5, y)], fill=(120, 230, 240, 230))
    for a in (-90, 0, 90, 180):
        tip = (160 + 110 * math.cos(math.radians(a)), 256 + 110 * math.sin(math.radians(a)))
        l_ = (160 + 20 * math.cos(math.radians(a + 90)), 256 + 20 * math.sin(math.radians(a + 90)))
        r_ = (160 + 20 * math.cos(math.radians(a - 90)), 256 + 20 * math.sin(math.radians(a - 90)))
        d.polygon([tip, l_, (160, 256)], fill=(212, 175, 55, 255), outline=(120, 90, 10, 255))
        d.polygon([tip, r_, (160, 256)], fill=(250, 232, 150, 255), outline=(120, 90, 10, 255))
    for a in (-45, 45, 135, 225):
        tip = (160 + 70 * math.cos(math.radians(a)), 256 + 70 * math.sin(math.radians(a)))
        l_ = (160 + 12 * math.cos(math.radians(a + 90)), 256 + 12 * math.sin(math.radians(a + 90)))
        r_ = (160 + 12 * math.cos(math.radians(a - 90)), 256 + 12 * math.sin(math.radians(a - 90)))
        d.polygon([tip, l_, (160, 256), r_], fill=(190, 220, 235, 255), outline=(70, 110, 130, 255))
    sw = 28 * math.sin(TAU * t)  # la aguja oscila
    ang = math.radians(-90 + sw)
    tip = (160 + 96 * math.cos(ang), 256 + 96 * math.sin(ang)); tail = (160 - 60 * math.cos(ang), 256 - 60 * math.sin(ang))
    n = (-math.sin(ang), math.cos(ang))
    d.polygon([tip, (160 + n[0] * 9, 256 + n[1] * 9), tail, (160 - n[0] * 9, 256 - n[1] * 9)], fill=(235, 60, 60, 255), outline=(255, 255, 255, 255))
    d.polygon([tail, (160 + n[0] * 9, 256 + n[1] * 9), (160 - n[0] * 9, 256 - n[1] * 9)], fill=(240, 240, 250, 255))
    d.ellipse([148, 244, 172, 268], fill=(255, 250, 220, 255), outline=(120, 90, 10, 255), width=3)
    im = twinkle(im, 12, 10, t, (255, 235, 150), 40, 470, k=2)
    return frame(im, (212, 175, 55), (255, 235, 150), t)

def sol(t):
    im = grad((255, 130, 40), (100, 20, 90), (232, 64, 70))
    im = glow(im, (160, 230), 170 + 10 * math.sin(TAU * t), (255, 225, 100), 0.75)
    for i, (cy, r) in enumerate([(60, 40), (110, 34)]):
        x = (t * (W + 200) * (1 + i) + i * 140) % (W + 200) - 100
        im = glow(im, (x, cy), r, (255, 190, 140), 0.55)
    d = ImageDraw.Draw(im); rot = t * 45
    for i in range(16):
        a = math.radians(i * 22.5 + rot)
        r0, r1, hw = 92, (150 if i % 2 == 0 else 125), 0.11
        d.polygon([(160 + r0 * math.cos(a - hw), 230 + r0 * math.sin(a - hw)), (160 + r1 * math.cos(a), 230 + r1 * math.sin(a)),
                   (160 + r0 * math.cos(a + hw), 230 + r0 * math.sin(a + hw))], fill=(255, 225, 80, 255), outline=(255, 150, 20, 255))
    d.ellipse([70, 140, 250, 320], fill=(255, 205, 50, 255), outline=(255, 140, 20, 255), width=6)
    d.ellipse([92, 162, 228, 298], fill=(255, 232, 110, 255)); d.ellipse([112, 180, 160, 220], fill=(255, 250, 200, 200))
    # mar
    sea = layer(); sd = ImageDraw.Draw(sea)
    sd.rectangle([0, 360, W, H], fill=(40, 20, 90, 255))
    for row in range(10):
        y = 366 + row * 14
        pts = [(x, y + 4 * math.sin(TAU * (x / 80.0 - t * (1 if row % 2 else -1)) + row)) for x in range(0, W + 8, 8)]
        gl = 1 - abs(row - 4) / 8
        sd.line(pts, fill=(255, int(150 + 80 * gl), int(60 + 40 * gl), int(120 + 110 * gl)), width=3)
    im = over(im, sea)
    l = layer(); ld = ImageDraw.Draw(l)  # reflejo
    for row in range(10):
        wdt = 90 - row * 6 + 16 * math.sin(TAU * (t + row * 0.13))
        ld.rectangle([160 - wdt / 2, 366 + row * 14, 160 + wdt / 2, 369 + row * 14], fill=(255, 225, 100, 160))
    im = over(im, l, 1.5)
    return frame(im, (255, 215, 80), (255, 245, 200), t)

def trebol(t):
    im = grad((4, 34, 20), (22, 118, 64), (10, 72, 42))
    im = glow(im, (160, 255), 140, (80, 230, 130), 0.35 + 0.12 * math.sin(TAU * t))
    im = rising(im, 6, 20, t, (210, 255, 150), 1.5, 3.5, 1, 14, 200)
    im = twinkle(im, 7, 12, t, (220, 255, 230), k=2)
    sway = 5 * math.sin(TAU * t)
    lay = layer(); d = ImageDraw.Draw(lay)
    d.line([(160, 290), (176, 420), (205, 455)], fill=(20, 120, 60, 255), width=14)
    for dx, dy in [(-1, -1), (1, -1), (-1, 1), (1, 1)]:
        cx, cy = 160 + dx * 40, 255 + dy * 40
        d.ellipse([cx - 46, cy - 46, cx + 46, cy + 46], fill=(46, 190, 96, 255), outline=(210, 255, 220, 255), width=4)
        d.ellipse([cx - 30, cy - 30, cx + 6, cy + 6], fill=(90, 235, 140, 255))
        d.line([cx, cy, 160, 255], fill=(20, 120, 60, 255), width=3)
    g = layer(); g.paste(lay.rotate(sway, center=(160, 255), resample=Image.BICUBIC))
    im = over(im, g.copy(), 12)
    im = Image.alpha_composite(im, g)
    mask = g.split()[3]
    im = sweep(im, mask, t, 30, 150)
    d = ImageDraw.Draw(im)
    gl = 0.5 + 0.5 * math.sin(TAU * t)  # gota de rocio
    d.ellipse([196, 190, 214, 208], fill=(220, 250, 255, int(120 + 120 * gl)), outline=(255, 255, 255, 255))
    return frame(im, (110, 235, 150), (230, 255, 235), t)

def espadas(t):
    im = grad((22, 32, 48), (70, 92, 120), (40, 56, 80))
    im = glow(im, (160, 250), 150, (120, 170, 230), 0.35 + 0.1 * math.sin(TAU * t))
    d = ImageDraw.Draw(im)
    for sgn in (1, -1):
        x0, y0, x1, y1 = 160 - sgn * 105, 70, 160 + sgn * 105, 450
        d.line([x0, y0, x1, y1], fill=(215, 225, 235, 255), width=22); d.line([x0, y0, x1, y1], fill=(255, 255, 255, 255), width=6)
        ang = math.atan2(y1 - y0, x1 - x0); gx, gy = x0 + (x1 - x0) * 0.8, y0 + (y1 - y0) * 0.8
        nx, ny = -math.sin(ang), math.cos(ang)
        d.line([gx - nx * 46, gy - ny * 46, gx + nx * 46, gy + ny * 46], fill=(240, 185, 40, 255), width=14)
        d.ellipse([x1 - 14, y1 - 14, x1 + 14, y1 + 14], fill=(240, 185, 40, 255))
    # destello en las hojas
    mask = clipmask(lambda dd: [dd.line([160 - s * 105, 70, 160 + s * 105, 450], fill=255, width=24) for s in (1, -1)])
    im = sweep(im, mask, t, 26, 230, ang=0.2, k=2)
    d = ImageDraw.Draw(im)
    shield = [(90, 130), (230, 130), (230, 290), (160, 400), (90, 290)]
    d.polygon(shield, fill=(36, 88, 200, 255)); d.line(shield + [shield[0]], fill=(250, 240, 210, 255), width=8)
    d.polygon([(160, 130), (230, 130), (230, 290), (160, 400)], fill=(30, 72, 170, 255))
    r = 52 + 4 * math.sin(TAU * t)
    im = glow(im, (160, 245), 50, (255, 215, 70), 0.5 + 0.2 * math.sin(TAU * t))
    d = ImageDraw.Draw(im)
    d.polygon(star_pts(160, 245, r, 22), fill=(255, 215, 70, 255), outline=(150, 100, 10, 255))
    # chispas del choque
    s = (t * 2) % 1.0
    l = layer(); ld = ImageDraw.Draw(l); rnd = random.Random(21)
    for _ in range(14):
        a = rnd.random() * TAU; ln = rnd.uniform(30, 80) * s
        ld.line([160 + 12 * math.cos(a), 160 + 12 * math.sin(a) * 0.8, 160 + (12 + ln) * math.cos(a), 160 + (12 + ln) * math.sin(a) * 0.8],
                fill=(255, 235, 140, int(255 * (1 - s))), width=2)
    im = over(im, l, 0.8)
    return frame(im, (240, 185, 40), (220, 235, 255), t)

CAPES = {'rey': rey, 'rayo': tormenta, 'calavera': calavera, 'espadas': espadas, 'luna': luna, 'corazon': corazon,
         'diamante': diamante, 'brujula': brujula, 'sol': sol, 'trebol': trebol}

def to_arr(img): return np.array(img.convert('RGB').resize((80, 128), Image.LANCZOS))

def sheet(outer):
    inner = (outer * 0.62).astype(np.uint8)
    fr = np.zeros((256, 512, 4), np.uint8)
    fr[0:144, 0:96, :3] = np.pad(outer, ((8, 8), (8, 8), (0, 0)), mode='edge'); fr[0:144, 0:96, 3] = 255
    fr[0:144, 96:184, :3] = np.pad(inner, ((8, 8), (0, 8), (0, 0)), mode='edge'); fr[0:144, 96:184, 3] = 255
    return fr

def icon(outer):
    im = Image.fromarray(outer).resize((18, 30), Image.LANCZOS).convert('RGBA')
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); out.paste(Image.new('RGBA', (20, 32), (0, 0, 0, 120)), (6, 0)); out.paste(im, (7, 1))
    return out

def main(client_dir, mod_dir, only=None):
    targets = [os.path.join(client_dir, 'src/main/resources/assets/atenasclient/textures/cosmetics'),
               os.path.join(mod_dir, 'src/main/resources/assets/atenassocial/textures/cosmetics')]
    contact = Image.new('RGB', (len(CAPES) * 88, 4 * 134), (30, 30, 36))
    for ci, (cid, fn) in enumerate(CAPES.items()):
        if only and cid not in only: continue
        outers = [to_arr(fn(f / FR).convert('RGB')) for f in range(FR)]
        strip = np.concatenate([sheet(o) for o in outers], axis=0)
        for base in targets:
            os.makedirs(os.path.join(base, 'capes'), exist_ok=True); os.makedirs(os.path.join(base, 'icons'), exist_ok=True)
            Image.fromarray(strip, 'RGBA').save(os.path.join(base, 'capes', cid + '.png'), optimize=True)
            icon(outers[3]).save(os.path.join(base, 'icons', 'cape_' + cid + '.png'))
        for k, f in enumerate([0, 3, 6, 9]):
            contact.paste(Image.fromarray(outers[f]), (ci * 88 + 4, k * 134 + 3))
        print(cid, 'ok', flush=True)
    contact.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'contacto_anim.png'))

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3:] or None)
