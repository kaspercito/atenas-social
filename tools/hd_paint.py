"""Capas HD pintadas a mano: atenas (logo), matrix y las 5 estaticas (atardecer, sakura, oceano, bosque, hielo). Lienzo 320x512."""
import os, math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from hd_core import *
import hd_scenes as S

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.environ.get('ATENAS_ASSETS', os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'atenasclient'))


def smooth(a, b, v):
    t = np.clip((v - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def font(size):
    return ImageFont.truetype(os.path.join(ASSETS, 'fonts', 'ui.ttf'), size)


def arr(im):
    return np.asarray(im.convert('RGB'), np.float32) / 255.0


def pil(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGB').convert('RGBA')


def vyg(stops):
    """degradado vertical con posiciones 0..1 y colores hex (sin depender de las unidades viejas)"""
    return pal(stops, Y)


def clouds(col, seed, y0, y1, light, dark, cover=0.52, gw=2, gh=13):
    n = fbm(seed, gw, gh, 4)
    py = np.arange(HH, dtype=np.float32)[:, None]
    fade = np.clip(np.minimum(py - y0, y1 - py) / 48.0, 0, 1)
    m = smooth(cover, cover + 0.16, n) * fade * 0.85
    shade = smooth(cover + 0.02, cover + 0.34, n)[..., None]
    c = (hexc(dark) / 255.0) * (1 - shade) + (hexc(light) / 255.0) * shade
    return col * (1 - m[..., None]) + c * m[..., None]


def sparkles(im, seed, n, ymin, ymax, color=(255, 255, 255), rr=(2, 5), alpha=230):
    rnd = random.Random(seed); l = S.layer(); d = ImageDraw.Draw(l)
    for _ in range(n):
        x, y, r = rnd.randint(8, HW - 8), rnd.randint(ymin, ymax), rnd.uniform(*rr)
        d.ellipse([x - r, y - r, x + r, y + r], fill=color + (alpha,))
        if r > 3:
            d.line([x - r * 2.4, y, x + r * 2.4, y], fill=color + (alpha // 2,), width=1)
            d.line([x, y - r * 2.4, x, y + r * 2.4], fill=color + (alpha // 2,), width=1)
    return S.over(im, l, 0.6)


# ===================================================================== ATENAS (exclusiva)
_LOGO = None


def _logo():
    global _LOGO
    if _LOGO is None:
        lg = Image.open(os.path.join(ASSETS, 'textures', 'gui', 'logo.png')).convert('RGBA').crop((22, 22, 234, 234))
        _LOGO = lg.resize((276, 276), Image.LANCZOS)
    return _LOGO


def _pal_cycle(u):
    stops = [(0, (34, 211, 238)), (.33, (139, 92, 246)), (.66, (236, 72, 153)), (1, (34, 211, 238))]
    u = u % 1.0
    for (p0, c0), (p1, c1) in zip(stops[:-1], stops[1:]):
        if p0 <= u <= p1:
            f = (u - p0) / (p1 - p0)
            return tuple(int(c0[i] * (1 - f) + c1[i] * f) for i in range(3))
    return stops[0][1]


def atenas(f):
    t = f / FR
    col = pal([(0, 0x0C0B22), (.55, 0x120F34), (1, 0x1A1245)], Y)
    hexg = smooth(0.72, 0.92, np.sin(xx * 0.9 + yy * 0.52) * np.sin(xx * 0.9 - yy * 0.52))
    col = col + np.array([40, 30, 90]) / 255.0 * (hexg * (0.22 + 0.12 * math.sin(TAU * t)))[..., None]
    col = col + 0.04 * (fbm(5, 4, 6, 3) - 0.5)[..., None] * np.array([0.6, 0.4, 1.0])
    im = pil(col)
    im = S.twinkle(im, 7, 22, t, (190, 205, 255), 330, 470, (3, 7), k=2)
    ox, oy = 22, 34
    lg = _logo()
    im.alpha_composite(lg, (ox, oy))
    # brillo diagonal que recorre el emblema
    lum = np.asarray(lg.convert('L'), np.float32) / 255.0
    gy, gx = np.mgrid[0:276, 0:276].astype(np.float32)
    dd = (gx + gy * 0.7) / 430.0 - (t * 1.7 - 0.35)
    band = np.exp(-(dd / 0.07) ** 2) * np.clip((lum - 0.25) * 3, 0, 1) * np.asarray(lg.split()[3], np.float32) / 255.0
    sh = Image.new('RGBA', (276, 276), (255, 235, 255, 0)); sh.putalpha(Image.fromarray((band * 190).astype(np.uint8)))
    im.alpha_composite(sh, (ox, oy))
    # nodos del hexagono que laten uno tras otro
    nodes = [(128, 32, (159, 232, 255)), (210, 80, (79, 184, 255)), (210, 176, (167, 139, 250)), (128, 222, (255, 92, 176)), (45, 176, (167, 139, 250)), (45, 80, (79, 184, 255))]
    for i, (nx, ny, c) in enumerate(nodes):
        b = 0.5 + 0.5 * math.sin(TAU * (t - i / 6.0))
        px, py = ox + (nx - 22) * 276 / 212.0, oy + (ny - 22) * 276 / 212.0
        im = S.glow(im, (px, py), 14 + 10 * b, c, 0.25 + 0.5 * b)
    # ATENAS (letra a letra, degradado y destello)
    fnt = font(66)
    txt = Image.new('RGBA', (HW, HH), (0, 0, 0, 0)); td = ImageDraw.Draw(txt)
    letters = "ATENAS"
    widths = [td.textlength(ch, font=fnt) for ch in letters]
    gap = 6
    total = sum(widths) + gap * 5
    x = (HW - total) / 2
    mask = Image.new('L', (HW, HH), 0); md = ImageDraw.Draw(mask)
    for ch, wch in zip(letters, widths):
        md.text((x, 330), ch, font=fnt, fill=255); x += wch + gap
    gy2, gx2 = np.mgrid[0:HH, 0:HW].astype(np.float32)
    v = np.clip((gy2 - 335) / 70.0, 0, 1)[..., None]
    base = np.array([185, 166, 255]) * (1 - v) + np.array([244, 114, 182]) * v
    shine = np.exp(-(((gx2 / HW) - ((t * 1.6) % 1.6 - 0.3)) / 0.10) ** 2)[..., None]
    rgb = np.clip(base * (0.9 + 0.1 * math.sin(TAU * t)) + 255 * shine * 0.6, 0, 255).astype(np.uint8)
    tl = Image.fromarray(rgb, 'RGB').convert('RGBA'); tl.putalpha(mask)
    glow_l = S.layer(); glow_l.paste(Image.new('RGBA', (HW, HH), (170, 120, 255, 255)), (0, 0), mask.point(lambda a: a // 3))
    im = S.over(im, glow_l, 6)
    im.alpha_composite(tl)
    # barras del logo
    d = ImageDraw.Draw(im)
    for yb, wb, a_ in ((428, 176, 255), (446, 128, 150), (462, 80, 90)):
        for xp in range(int(160 - wb / 2), int(160 + wb / 2), 2):
            sh_ = 0.5 + 0.5 * math.sin(TAU * (t - (xp - 70) / 180.0))
            c = (int(139 * 0.6 + 236 * 0.4 + 40 * sh_), int(92 * 0.6 + 72 * 0.4 + 30 * sh_), int(246 * 0.6 + 153 * 0.4))
            d.rectangle([xp, yb, xp + 2, yb + 6], fill=c + (a_,))
    # borde de neon con cometa
    n = 900
    l = S.layer(); ld = ImageDraw.Draw(l)
    x0, y0, x1, y1 = 5, 5, HW - 6, HH - 6
    per = 2 * ((x1 - x0) + (y1 - y0))
    def pt(s):
        s = s % per
        if s < x1 - x0: return (x0 + s, y0)
        s -= x1 - x0
        if s < y1 - y0: return (x1, y0 + s)
        s -= y1 - y0
        if s < x1 - x0: return (x1 - s, y1)
        s -= x1 - x0
        return (x0, y1 - s)
    for i in range(n):
        u = i / n
        c = _pal_cycle(u + t)
        dist = abs(((u) - t + 0.5) % 1.0 - 0.5)
        comet = math.exp(-(dist / 0.045) ** 2)
        x, y = pt(u * per)
        r = 3.2 + 1.6 * comet
        cc = tuple(min(255, int(ch + 160 * comet)) for ch in c)
        ld.ellipse([x - r, y - r, x + r, y + r], fill=cc + (255,))
    l2 = l.filter(ImageFilter.GaussianBlur(5))
    im = S.over(im, l2); im = S.over(im, l)
    d = ImageDraw.Draw(im)
    d.rectangle([16, 16, HW - 17, HH - 17], outline=(160, 120, 255, 70), width=2)
    return im.convert('RGB')


# ===================================================================== MATRIX
_CH = "0123456789ABCDEFGHKLMNPRSTXYZ<>=+*#$%&"


def matrix(f):
    t = f / FR
    col = pal([(0, 0x010603), (1, 0x02100A)], Y)
    im = pil(col)
    cw, ch = 16, 24
    ncol = HW // cw
    nrow = HH // ch + 1
    fnt = font(20)
    r = np.random.RandomState(13)
    lay = S.layer(); d = ImageDraw.Draw(lay)
    glow_l = S.layer(); gd = ImageDraw.Draw(glow_l)
    for c in range(ncol):
        sp, off, tail = int(r.randint(1, 3)), r.randint(0, HH), r.randint(20, 34) * 8
        for stream in (0, 1):
            head = (off + stream * HH / 2 + sp * HH * t) % HH
            for row in range(nrow):
                y = row * ch
                dist = (head - (y + ch / 2)) % HH
                if dist >= tail: continue
                gr = np.random.RandomState(c * 977 + row * 31 + f * 7 + stream * 5)
                if gr.rand() < 0.18 and dist > ch: continue
                b = 1 - dist / tail
                g = _CH[gr.randint(len(_CH))]
                if dist < ch * 1.2:
                    fill = (232, 255, 232, 255)
                    gd.text((c * cw + 2, y), g, font=fnt, fill=(60, 255, 120, 255))
                else:
                    fill = (int(8 + 30 * b), int(70 + 185 * b), int(34 + 70 * b), int(120 + 135 * b))
                d.text((c * cw + 2, y), g, font=fnt, fill=fill)
    im = S.over(im, glow_l, 5)
    im = S.over(im, lay)
    return S.over(im, S.layer()).convert('RGB') if False else im.convert('RGB')


# ===================================================================== ESTATICAS
def atardecer(f):
    col = pal([(0, 0x1B3A6B), (.30, 0x3E5C9A), (.55, 0xE08A6E), (.78, 0x8C4A7A), (1, 0x2B2A5C)], Y)
    im = pil(col)
    im = sparkles(im, 3, 34, 10, 150, (255, 255, 255), (1.5, 3.5))
    cx, cy, R = 160, 105, 40
    im = S.glow(im, (cx, cy), 70, (230, 235, 255), 0.35)
    a = arr(im)
    gy, gx = np.mgrid[0:HH, 0:HW].astype(np.float32)
    d1 = np.hypot(gx - cx, gy - cy); d2 = np.hypot(gx - cx - R * 0.62, gy - cy + R * 0.15)
    m = smooth(R + 1, R - 1, d1) * smooth(R * 0.92 - 1, R * 0.92 + 1, d2)
    a = a * (1 - m[..., None]) + np.array([0.96, 0.95, 0.91]) * m[..., None]
    beam = np.exp(-((gx - cx) / 5.0) ** 2) * np.clip(1 - (gy - 160) / 240.0, 0, 1) * (gy > 160) * 0.26
    a = a + beam[..., None] * np.array([0.75, 0.85, 1.0])
    a = clouds(a, 11, 200, 450, 0xFFC98A, 0x7A3E6E, 0.50)
    a = clouds(a, 21, 330, 512, 0xFF9E7A, 0x4A2E6E, 0.54)
    return pil(a)


def sakura(f):
    col = pal([(0, 0xFFD6E8), (.5, 0xF8A9C8), (1, 0x7A3A6E)], Y)
    col = clouds(col, 6, 40, 250, 0xFFF0F6, 0xF3B6D0, 0.55)
    im = pil(col)
    rnd = random.Random(8)
    # ramas oscuras en las esquinas
    l = S.layer(); d = ImageDraw.Draw(l)
    for (x0, y0, dx) in ((0, 330, 1), (320, 250, -1)):
        pts = [(x0, y0)]
        x, y = x0, y0
        for i in range(7):
            x += dx * rnd.randint(26, 46); y += rnd.randint(-34, 12); pts.append((x, y))
        d.line(pts, fill=(74, 42, 58, 255), width=9, joint='curve')
        for (x, y) in pts[1:]:
            d.line([(x, y), (x + dx * rnd.randint(10, 30), y - rnd.randint(14, 40))], fill=(74, 42, 58, 255), width=4)
    im = S.over(im, l)
    # flores
    def flower(dd, x, y, r, rot, c1, c2):
        for k in range(5):
            a = rot + k * TAU / 5
            px, py = x + math.cos(a) * r * 0.62, y + math.sin(a) * r * 0.62
            dd.ellipse([px - r * 0.55, py - r * 0.55, px + r * 0.55, py + r * 0.55], fill=c1 + (255,))
            dd.ellipse([px - r * 0.3, py - r * 0.3, px + r * 0.3, py + r * 0.3], fill=c2 + (255,))
        dd.ellipse([x - r * 0.2, y - r * 0.2, x + r * 0.2, y + r * 0.2], fill=(255, 214, 90, 255))
    l = S.layer(); d = ImageDraw.Draw(l)
    for _ in range(46):
        side = rnd.random() < 0.5
        x = rnd.randint(0, 130) if side else rnd.randint(190, 320)
        y = rnd.randint(190, 400) if side else rnd.randint(110, 330)
        flower(d, x, y, rnd.uniform(10, 20), rnd.random() * TAU, rnd.choice([(255, 235, 244), (255, 214, 232), (255, 190, 218)]), (255, 156, 196))
    im = S.over(im, l, 0.7)
    # petalos cayendo
    l = S.layer(); d = ImageDraw.Draw(l)
    for _ in range(80):
        x, y, r = rnd.randint(0, 320), rnd.randint(0, 512), rnd.uniform(3, 7)
        a = rnd.random() * TAU
        d.ellipse([x - r, y - r * 0.55, x + r, y + r * 0.55], fill=rnd.choice([(255, 255, 255, 220), (255, 224, 238, 220), (255, 156, 196, 220)]))
    im = S.over(im, l.rotate(0), 0.5)
    a = arr(im)
    a = clouds(a, 9, 400, 512, 0xFFB6D6, 0x7A3A6E, 0.6)
    return pil(a)


def oceano(f):
    hz = 205
    col = pal([(0, 0xFFB86B), (.32, 0xFF7E6B), (.40, 0x2A5AA8), (1, 0x06183A)], Y)
    a = clouds(col, 4, 20, 170, 0xFFE2B0, 0xD9605E, 0.58)
    im = pil(a)
    im = S.glow(im, (160, hz - 6), 120, (255, 230, 150), 0.7)
    d = ImageDraw.Draw(im)
    R = 54
    d.ellipse([160 - R, hz - R + 6, 160 + R, hz + R + 6], fill=(255, 241, 184, 255))
    sea = Image.new('RGBA', (HW, HH - hz), (0, 0, 0, 0))
    sa = pal([(0, 0x2A5AA8), (1, 0x06183A)], np.linspace(0, 1, HH - hz)[:, None] * np.ones((1, HW)))
    sea = pil(sa)
    im.paste(sea, (0, hz))
    d = ImageDraw.Draw(im)
    rnd = random.Random(1)
    for row in range(26):
        y = hz + 10 + row * 11.5
        amp = 1.5 + row * 0.22
        pts = [(x, y + amp * math.sin(x / (16 + row * 1.1) + row * 1.7)) for x in range(0, HW + 8, 6)]
        k = row / 26.0
        d.line(pts, fill=(int(120 + 80 * (1 - k)), int(180 + 50 * (1 - k)), 255, int(60 + 120 * (1 - k))), width=2)
    for row in range(22):  # reflejo del sol
        y = hz + 6 + row * 11
        wdt = max(8, 118 - row * 3.6) * (0.75 + 0.25 * math.sin(row * 2.1))
        d.rectangle([160 - wdt / 2, y, 160 + wdt / 2, y + 3.5], fill=(255, 232, 168, int(230 - row * 7)))
    a = arr(im)
    a *= (0.88 + 0.12 * smooth(0, 3, np.minimum(xx, W - 1 - xx)))[..., None]
    return pil(a)


def bosque(f):
    col = pal([(0, 0xA8D8C8), (.4, 0xE8F0D0), (1, 0x2E5B3E)], Y)
    im = pil(col)
    # rayos de luz
    l = S.layer(); d = ImageDraw.Draw(l)
    for i in range(6):
        x = 40 + i * 52
        d.polygon([(x, 0), (x + 24, 0), (x + 90, 512), (x + 20, 512)], fill=(255, 250, 200, 34))
    im = S.over(im, l, 10)
    rnd = random.Random(4)
    def ridge(seed, base, amp, c, pines, fog):
        nonlocal im
        r1 = smooth_noise(seed, 7, 3)[0]
        l = S.layer(); d = ImageDraw.Draw(l)
        pts = [(x, base - r1[x] * amp) for x in range(0, HW)]
        d.polygon(pts + [(HW, HH), (0, HH)], fill=c + (255,))
        for _ in range(pines):
            x = rnd.randint(0, HW); y = base - r1[min(x, HW - 1)] * amp + 4
            h = rnd.randint(40, 90)
            for k in range(4):
                w = (4 - k) * h / 9 + 4
                yy_ = y - h + k * h / 4.5
                d.polygon([(x, yy_ - h / 5), (x - w, yy_ + h / 4), (x + w, yy_ + h / 4)], fill=c + (255,))
        im = S.over(im, l)
        if fog:
            fl = S.layer(); fd = ImageDraw.Draw(fl)
            for k in range(30):
                yk = base - amp + k * (amp + 40) / 30
                fd.rectangle([0, yk, HW, yk + 8], fill=(232, 245, 232, int(fog * math.sin(math.pi * k / 30))))
            im = S.over(im, fl, 6)
    ridge(12, 280, 90, (127, 181, 154), 14, 70)
    ridge(15, 340, 90, (79, 143, 114), 14, 80)
    ridge(18, 410, 80, (47, 106, 82), 12, 70)
    ridge(19, 480, 40, (21, 59, 44), 12, 0)
    l = S.layer(); d = ImageDraw.Draw(l)
    for _ in range(30):
        x, y = rnd.randint(10, 310), rnd.randint(250, 480)
        d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(255, 246, 168, 255))
    im = S.over(im, l, 4); im = S.over(im, l, 0.5)
    return im


def hielo(f):
    col = pal([(0, 0xDFF6FF), (.5, 0x8ACBF0), (1, 0x2B5CA8)], Y)
    col = col * (0.94 + 0.12 * fbm(9, 6, 9, 4))[..., None]
    im = pil(col)
    rnd = random.Random(9)
    l = S.layer(); d = ImageDraw.Draw(l)
    for _ in range(22):
        cx, cy = rnd.randint(10, 310), rnd.randint(30, 500)
        h, w = rnd.randint(70, 170), rnd.randint(18, 40)
        ang = rnd.uniform(-0.5, 0.5)
        def P(px, py): return (cx + px * math.cos(ang) - py * math.sin(ang), cy + px * math.sin(ang) + py * math.cos(ang))
        tip = P(0, -h / 2); bl = P(-w / 2, h / 2); br = P(w / 2, h / 2); ml = P(-w / 2, -h / 6); mr = P(w / 2, -h / 6)
        k = rnd.uniform(0.7, 1.15)
        d.polygon([tip, ml, bl, P(0, h / 2), P(0, -h / 6)], fill=(int(190 * k), int(236 * k), min(255, int(255 * k)), 150))
        d.polygon([tip, mr, br, P(0, h / 2), P(0, -h / 6)], fill=(int(110 * k), int(190 * k), min(255, int(245 * k)), 150))
        d.line([tip, ml, bl], fill=(255, 255, 255, 190), width=2); d.line([tip, mr, br], fill=(255, 255, 255, 120), width=2)
    im = S.over(im, l)
    a = arr(im)
    ys = np.arange(HH, dtype=np.float32)[:, None]
    a = a + 0.0
    im = pil(a)
    im = sparkles(im, 12, 40, 10, 500, (255, 255, 255), (2, 5.5))
    return im


PAINT = {
    'atenas': (atenas, FR, 3), 'matrix': (matrix, FR, 3),
    'atardecer': (atardecer, 1, 0), 'sakura': (sakura, 1, 0), 'oceano': (oceano, 1, 0), 'bosque': (bosque, 1, 0), 'hielo': (hielo, 1, 0),
}
