"""Capas HD basadas en campos numericos (antes 40x64). Cada funcion f(frame) -> array HHxHWx3 en 0..1 (lienzo 320x512).
Las formulas son las de siempre, evaluadas en coordenadas continuas (ver hd_core); los puntitos de un pixel pasan a manchas suaves."""
import math
import numpy as np
from hd_core import *


def smooth(a, b, v):
    t = np.clip((v - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


# ===================================================================== eclipse
def eclipse(f):
    t = f / FR
    cx, cy, R = 5.0, 6.8, 2.6
    dx, dy = AX - cx, AY - cy
    r, th = np.hypot(dx, dy), np.arctan2(dy, dx)
    col = pal([(0, (5, 3, 14)), (0.7, (16, 6, 32)), (1, (56, 18, 40))], Y)
    col += 0.05 * (fbm(3, 5, 8, 4) - 0.5)[..., None] * np.array([0.6, 0.3, 1.0])
    stars(col, 3, 22, t, ymax=40)
    out = np.clip(r - R, 0, None)
    pulse = 0.86 + 0.14 * np.sin(TAU * t)
    glow = np.exp(-out / 1.7) * 0.6 * pulse
    rays = np.clip(np.cos(14 * th - TAU * t), 0, 1) ** 5 * np.exp(-out / 2.8) * (r > R)
    rays2 = np.clip(np.cos(9 * th + TAU * t + 1.0), 0, 1) ** 6 * np.exp(-out / 2.0) * (r > R) * 0.6
    gold, orange, white = np.array([1.0, 0.74, 0.28]), np.array([1.0, 0.45, 0.12]), np.array([1.0, 0.97, 0.86])
    col = col + glow[..., None] * gold + (rays * pulse)[..., None] * orange + rays2[..., None] * gold * 0.8
    ring = np.exp(-((r - R) / 0.22) ** 2)
    col = mix(col, white, ring * 0.95 * pulse)
    disc = smooth(R - 0.05, R - 0.2, r)
    inner = np.array([0.01, 0.008, 0.03]) + 0.03 * np.exp(-((r - (R - 0.12)) / 0.5) ** 2)[..., None]
    col = col * (1 - disc[..., None]) + inner * disc[..., None]
    a = TAU * t - np.pi / 2
    d = np.hypot(AX - (cx + R * np.cos(a)), AY - (cy + R * np.sin(a)))
    col += (np.exp(-(d / 0.5) ** 2) * 1.3)[..., None] * white
    col += (np.exp(-(1.0 - Y) * 11) * 0.5)[..., None] * np.array([0.9, 0.32, 0.12])
    return col


# ===================================================================== tormenta
def tormenta(f):
    t = f / FR
    v = 0.5 + 0.5 * waves(7, 7, t)
    col = pal([(0, (6, 8, 20)), (0.45, (26, 33, 60)), (0.8, (70, 82, 116)), (1, (120, 135, 170))], v * (1.05 - 0.45 * Y))
    flash = {3: 1.0, 4: 0.55, 5: 0.22}.get(f, 0.0)
    col += flash * 0.38 * (1.0 - 0.5 * Y)[..., None] * np.array([0.7, 0.8, 1.0])
    if flash >= 0.5:
        rng = np.random.RandomState(11)
        px, py, pts = 22.0, 0.0, []
        while py < 46:
            pts.append((px, py)); px += rng.uniform(-3.5, 3.5); py += rng.uniform(4, 8)
        pts.append((px, py))
        dist = np.full(SHP, 99.0, np.float32)
        for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
            vx, vy = x2 - x1, y2 - y1
            u = np.clip(((xx - x1) * vx + (yy - y1) * vy) / (vx * vx + vy * vy), 0, 1)
            dist = np.minimum(dist, np.hypot(xx - (x1 + u * vx), yy - (y1 + u * vy)))
        col += (np.exp(-(dist / 0.55) ** 2) * 1.5 * flash)[..., None] * np.array([0.95, 0.97, 1.0])
        col += (np.exp(-(dist / 3.2) ** 2) * 0.35 * flash)[..., None] * np.array([0.5, 0.6, 1.0])
    rng = np.random.RandomState(5)
    for _ in range(38):
        x0, y0, k = rng.randint(-4, W), rng.randint(0, H), rng.choice([1, 2])
        y = (y0 + k * H * f / FR) % H
        for j in range(8):
            put(col, x0 + (y - j * 0.5) * 0.18, (y - j * 0.5) % H, (0.62, 0.78, 1.0), 0.55 - 0.065 * j, 0.28)
    return col


# ===================================================================== nieve
def nieve(f):
    t = f / FR
    col = pal([(0, (6, 10, 34)), (0.55, (14, 34, 76)), (1, (44, 98, 128))], Y)
    band = np.exp(-((Y - 0.42 + 0.05 * np.sin(TAU * (X * 1.5 + t))) / 0.09) ** 2) * (0.5 + 0.5 * np.sin(TAU * (X * 2 - t)))
    col += band[..., None] * np.array([0.05, 0.38, 0.32])
    stars(col, 9, 16, t, ymax=34)
    rng = np.random.RandomState(21)
    for _ in range(60):
        x0, y0, k, ph = rng.randint(0, W), rng.randint(0, H), rng.choice([1, 1, 2]), rng.rand()
        x = x0 + 1.6 * np.sin(TAU * (t + ph))
        y = (y0 + k * H * f / FR) % H
        if k == 2:
            put(col, x, y, (0.93, 0.97, 1.0), 0.95, 0.5)
            if (x0 + y0) % 3 == 0:
                star_cross(col, x, y, (0.85, 0.93, 1.0), 0.8, arm=1.4)
        else:
            put(col, x, y, (0.93, 0.97, 1.0), 0.55, 0.36)
    drift = 56 + 2.2 * np.sin(TAU * (X * 2 + 0.2)) + 1.2 * np.sin(TAU * X * 5) + 0.6 * np.sin(TAU * X * 11)
    m = smooth(-0.2, 0.2, yy - drift)
    snow = pal([(0, (190, 220, 250)), (1, (110, 150, 205))], np.clip((yy - drift) / 8, 0, 1))
    col = col * (1 - m[..., None]) + snow * m[..., None]
    return col


# ===================================================================== luciernagas
def luciernagas(f):
    t = f / FR
    col = pal([(0, (3, 10, 18)), (0.6, (6, 30, 30)), (1, (8, 44, 36))], Y)
    moon = np.exp(-(np.hypot(AX - 7.6, AY - 2.2) / 1.0) ** 4)
    col += (np.exp(-(np.hypot(AX - 7.6, AY - 2.2) / 3.0) ** 2) * 0.12)[..., None] * np.array([0.6, 0.9, 0.9])
    col = mix(col, (0.85, 0.95, 0.9), moon * 0.9)
    fog = 0.5 + 0.5 * np.sin(TAU * (X * 1.3 + t))
    col += (np.exp(-((Y - 0.8) / 0.08) ** 2) * fog * 0.12)[..., None] * np.array([0.3, 0.9, 0.7])
    rng = np.random.RandomState(4)
    sil = np.zeros(SHP, np.float32)
    for _ in range(9):
        cx, h = rng.uniform(-1, W + 1), rng.randint(16, 30)
        top = H - h
        w = np.clip((yy - top) * 0.34, 0, None)
        wob = 1.0 - 0.18 * ((yy - top) % 6) / 6
        sil = np.maximum(sil, smooth(-0.3, 0.3, yy - top) * smooth(0.0, -0.4, np.abs(xx - cx) - w * wob))
    col = col * (1 - sil[..., None]) + np.array([0.008, 0.025, 0.03]) * sil[..., None]
    rng = np.random.RandomState(8)
    for _ in range(16):
        cx, cy = rng.uniform(3, W - 3), rng.uniform(16, 56)
        ax, ay = rng.uniform(2, 7), rng.uniform(1.5, 5)
        fx, fy, p1, p2, p3 = rng.choice([1, 2]), rng.choice([1, 2]), rng.rand(), rng.rand(), rng.rand()
        x, y = cx + ax * np.sin(TAU * (fx * t + p1)), cy + ay * np.sin(TAU * (fy * t + p2))
        pulse = (0.5 + 0.5 * np.sin(TAU * (rng.choice([1, 2]) * t + p3))) ** 2 * 0.75 + 0.25
        d2 = (xx - x) ** 2 + (yy - y) ** 2
        col += (np.exp(-d2 / (2 * 1.7 ** 2)) * 0.55 * pulse)[..., None] * np.array([0.7, 1.0, 0.3])
        col += (np.exp(-d2 / 0.45) * pulse)[..., None] * np.array([1.0, 1.0, 0.65])
    return col


# ===================================================================== oro liquido
def oroliquido(f):
    t = f / FR
    v = waves(13, 7, t)
    c = 0.5 + 0.5 * v
    ridge = 1 - np.abs(2 * c - 1)
    k = 0.55 * c + 0.45 * ridge ** 2
    col = pal([(0, (52, 26, 4)), (0.3, (140, 82, 10)), (0.6, (226, 160, 34)), (0.82, (255, 218, 104)), (1, (255, 250, 200))], k ** 1.15)
    col *= (0.93 + 0.14 * fbm(13, 9, 14, 3))[..., None]
    s = ((t * 1.6) % 1.6) - 0.3
    u = X * 0.6 + Y * 0.4
    col += (np.exp(-((u - s) / 0.07) ** 2) * 0.5)[..., None] * np.array([1.0, 0.95, 0.7])
    rng = np.random.RandomState(2)
    for i in range(9):
        x, y, ph = rng.randint(2, W - 2), rng.randint(2, H - 2), rng.randint(0, FR)
        a = max(0.0, 1 - abs(((f - ph + FR / 2) % FR) - FR / 2) / 1.6)
        if a > 0:
            star_cross(col, x, y, (1, 1, 0.85), a, arm=1.8)
    col *= (0.82 + 0.18 * np.clip(np.minimum(xx, W - 1 - xx) / 2.0, 0, 1))[..., None]
    return col


# ===================================================================== cristal
def cristal(f):
    t = f / FR
    rng = np.random.RandomState(17)
    n = 17
    sx, sy = rng.uniform(0, 10, n), rng.uniform(0, 16, n)
    cols = [np.array(c) / 255.0 for c in [(70, 200, 255), (120, 110, 255), (210, 100, 255), (255, 120, 200), (90, 235, 225), (110, 150, 255)]]
    base = [cols[rng.randint(len(cols))] * rng.uniform(0.55, 1.0) for _ in range(n)]
    ph = rng.rand(n)
    d = np.stack([np.hypot(AX - sx[i], AY - sy[i]) for i in range(n)])
    order = np.argsort(d, axis=0)
    i1 = order[0]
    d1 = np.take_along_axis(d, order[:1], 0)[0]
    d2 = np.take_along_axis(d, order[1:2], 0)[0]
    col = np.zeros(SHP + (3,), np.float32)
    for i in range(n):
        m = i1 == i
        br = 0.5 + 0.5 * np.sin(TAU * (t + ph[i]))
        shade = 0.8 + 0.5 * np.clip(((AX - sx[i]) * -0.12 + (AY - sy[i]) * -0.08) + 0.5, 0, 1)
        col[m] = np.clip(base[i] * (0.38 + 0.7 * br ** 2) * shade[m][..., None], 0, 1.2)
    edge = np.clip(1 - (d2 - d1) / 0.22, 0, 1) ** 2
    col = mix(col, (0.9, 0.97, 1.0), edge * 0.85)
    s = ((t * 1.6) % 1.6) - 0.3
    u = X * 0.55 + Y * 0.45
    sw = np.exp(-((u - s) / 0.12) ** 2)
    col += (sw * 0.42)[..., None] * hsv(u * 2.2, 0.55, 1.0)
    for i in range(0, n, 3):
        a = max(0.0, 1 - abs(((f - int(ph[i] * FR) + FR / 2) % FR) - FR / 2) / 1.5)
        if a > 0:
            star_cross(col, sx[i] * 4, sy[i] * 4, (1, 1, 1), a, arm=2.0)
    return col


# ===================================================================== portal
def portal(f):
    t = f / FR
    dx, dy = (AX - 5.0) / 5.0, (AY - 8.0) / 8.0
    r, th = np.hypot(dx, dy), np.arctan2(dy, dx)
    arms = 0.5 + 0.5 * np.sin(3 * th + 10 * r - TAU * t)
    arms2 = 0.5 + 0.5 * np.sin(5 * th - 6 * r + TAU * 2 * t)
    k = (0.7 * arms + 0.3 * arms2) * (1.05 - 0.4 * r)
    col = pal([(0, (8, 2, 26)), (0.35, (70, 24, 170)), (0.6, (170, 50, 230)), (0.82, (240, 80, 200)), (1, (110, 230, 255))], k)
    pulse = 0.85 + 0.15 * np.sin(TAU * t)
    col += (np.exp(-(r / 0.24) ** 2) * 1.25 * pulse)[..., None] * np.array([0.85, 0.75, 1.0])
    col += (np.exp(-((r - 0.88) / 0.07) ** 2) * 0.55)[..., None] * np.array([0.3, 0.9, 1.0])
    col *= np.clip(1.25 - r * 0.7, 0.15, 1.0)[..., None]
    stars(col, 6, 18, t)
    return col


# ===================================================================== dragon
_DRAGON = {}


def _dragon_scales():
    if _DRAGON:
        return _DRAGON['col'], _DRAGON['inside']
    col = np.zeros(SHP + (3,), np.float32) + np.array([0.03, 0.004, 0.01])
    inside = np.zeros(SHP, np.float32)
    R, pitch = 5.2, 3.6
    rowi, cy = 0, -2.0
    while cy < H + 4:
        off = 4.0 if rowi % 2 else 0.0
        cx = -4.0 + off
        while cx < W + 4:
            x0, x1 = max(0, int((cx - R - 1 + 0.5) * K)), min(HW, int((cx + R + 1 + 0.5) * K))
            y0, y1 = max(0, int((cy - R - 1 + 0.5) * K)), min(HH, int((cy + R + 1 + 0.5) * K))
            if x0 < x1 and y0 < y1:
                gx, gy = xx[y0:y1, x0:x1], yy[y0:y1, x0:x1]
                d = np.hypot(gx - cx, gy - cy) / R
                m = smooth(1.0, 0.97, d)
                if m.any():
                    rnd = np.sin(cx * 12.9898 + cy * 78.233) * 43758.5453
                    var = 0.8 + 0.4 * (rnd - np.floor(rnd))
                    lit = np.clip(1.15 - d * 1.1 + 0.25 * ((cy - gy) / R), 0, 1) * var
                    body = pal([(0, (36, 3, 8)), (0.5, (122, 14, 20)), (1, (214, 48, 32))], lit)
                    rim = np.clip((d - 0.84) / 0.16, 0, 1)[..., None]
                    body = body * (1 - 0.8 * rim)
                    hl = np.exp(-(np.hypot(gx - (cx - 1.2), gy - (cy - 1.8)) / 1.5) ** 2) * 0.3
                    body = body + hl[..., None] * np.array([1.0, 0.55, 0.4])
                    sub = col[y0:y1, x0:x1]
                    col[y0:y1, x0:x1] = sub * (1 - m[..., None]) + body * m[..., None]
                    ins = inside[y0:y1, x0:x1]
                    inside[y0:y1, x0:x1] = np.where(m > 0.5, np.clip(1.0 - d * 1.15, 0, 1), ins)
            cx += 8.0
        cy += pitch
        rowi += 1
    _DRAGON['col'], _DRAGON['inside'] = col, inside
    return col, inside


def dragon(f):
    t = f / FR
    base, inside = _dragon_scales()
    col = base.copy()
    w = (0.5 + 0.5 * np.sin(TAU * (t - (X * 0.6 + Y * 0.9)))) ** 4
    col += (w * inside * 1.5)[..., None] * np.array([1.0, 0.5, 0.08])
    col += (w * 0.25 * (inside < 0.05))[..., None] * np.array([1.0, 0.35, 0.05])
    rng = np.random.RandomState(6)
    for _ in range(12):
        x0, y0, k, ph = rng.randint(0, W), rng.randint(0, H), rng.choice([1, 2]), rng.rand()
        y = (y0 - k * H * f / FR) % H
        x = x0 + 1.2 * np.sin(TAU * (t + ph))
        put(col, x, y, (1.0, 0.8, 0.3), 0.95, 0.4)
        put(col, x, (y + 1) % H, (1.0, 0.45, 0.1), 0.5, 0.35)
    return col


# ===================================================================== aurora
def aurora(f):
    t = f / FR
    img = vgrad([(0, 0x040818), (.55, 0x09263F), (1, 0x061A2C)])
    stars(img, 5, 26, t, ymax=44)
    n = fbm(31, 6, 9, 3)
    for bi, (base, ph, wd) in enumerate(((20, 0.0, 6.5), (31, 0.5, 5.0))):
        c = base + 5 * np.sin(TAU * (X * 1.0 + t + ph)) + 2.5 * np.sin(TAU * (X * 2.0 - 2 * t + ph))
        inten = np.exp(-((yy - c) / wd) ** 2)
        streak = 0.65 + 0.35 * np.sin(TAU * (X * 5.0 + t * 2 + bi * .3))
        rays = 0.78 + 0.22 * np.sin(TAU * (X * 34.0 + n * 2.5 + bi))
        col = pal([(0, 0x35FFB0), (.55, 0x2CC4FF), (1, 0xB45CFF)], np.clip((yy - c) / (2 * wd) + .5, 0, 1))
        img = img + col * (inten * streak * rays * 0.9)[..., None]
    r1 = smooth_noise(4, 9, 3)[0]
    r2 = smooth_noise(14, 13, 4)[0]
    for top0, amp, hexcol, sh in ((50, 12, 0x07101C, 1.0), (55.5, 8, 0x040A12, 1.0)):
        rr = r1 if top0 < 52 else r2
        top = top0 - rr * amp
        m = smooth(-0.15, 0.15, yy - top[None, :])
        shade = np.array(hexc(hexcol) / 255.0) * (0.8 + 0.3 * np.clip((yy - 44) / 20, 0, 1))[..., None]
        img = img * (1 - m[..., None]) + shade * m[..., None]
    return img


# ===================================================================== galaxia
def galaxia(f):
    t = f / FR
    img = vgrad([(0, 0x080420), (.5, 0x1C0B4A), (1, 0x0A1038)])
    n1 = np.sin(xx * 0.33 + yy * 0.21 + TAU * t) + np.sin(xx * 0.17 - yy * 0.29 - TAU * t) + np.sin(yy * 0.38 + TAU * 2 * t + 1)
    n1 = (n1 + 3) / 6.0
    dust = fbm(77, 4, 6, 5)
    img += np.array([120, 40, 190]) / 255.0 * (np.clip(n1 - 0.45, 0, 1) * 1.6 * (0.6 + 0.8 * dust))[..., None]
    n2 = (np.sin(xx * 0.25 - yy * 0.4 + TAU * t + 2) + np.sin(xx * 0.4 + yy * 0.12 - TAU * t) + 2) / 4.0
    img += np.array([30, 90, 200]) / 255.0 * (np.clip(n2 - 0.5, 0, 1) * 1.3 * (0.6 + 0.8 * dust))[..., None]
    stars(img, 9, 44, t, color=(1, 1, 1), big=0.3)
    if t < 0.62:
        p = t / 0.62
        hx, hy = 38 - p * 44, -6 + p * 34
        for k in range(18):
            put(img, hx + k * 0.45, hy - k * 0.31, (1, 1, 1), (1 - k / 18.0), 0.42 - 0.012 * k)
    return img


# ===================================================================== lava
def lava(f):
    t = f / FR
    n = smooth_noise(11, 7, 11) * 0.65 + smooth_noise(12, 14, 22) * 0.35
    n = n * 0.9 + 0.1 * fbm(18, 20, 32, 3)
    crack = np.clip(1 - np.abs(n - 0.5) * 7.0, 0, 1)
    img = np.ones(SHP + (3,), np.float32) * (hexc(0x1A1210) / 255.0) * (0.55 + 0.9 * fbm(3, 8, 12, 4))[..., None]
    ph = smooth_noise(21, 6, 9)
    glow = crack * (0.65 + 0.35 * np.sin(TAU * (t + ph * 2)))
    col = pal([(0, 0x5A0A00), (.45, 0xE0300A), (.75, 0xFF8A12), (1, 0xFFE46A)], glow)
    m = smooth(0.22, 0.34, crack)[..., None]
    img = img * (1 - m) + col * m
    img += np.array([255, 80, 10]) / 255.0 * (np.clip(crack - 0.1, 0, 1) * 0.28 * (0.6 + 0.4 * np.sin(TAU * t)))[..., None]
    r = np.random.RandomState(5)
    for _ in range(18):
        x0, y0, k = r.randint(0, W), r.randint(0, H), r.randint(1, 3)
        y = (y0 - H * t * k) % H
        x = x0 + 2 * math.sin(TAU * (t * k + x0 / 7.0))
        put(img, x, y, (1.0, 0.75, 0.25), 0.55 + 0.45 * math.sin(TAU * t * 3 + x0), 0.4)
    return img


# ===================================================================== llamas
def llamas(f):
    t = f / FR
    img = vgrad([(0, 0x090203), (.6, 0x1C0504), (1, 0x2E0A04)])
    layers = [(0.0, 1.0, -0.12, 24, 0.0), (0.37, 0.8, 0.0, 27, 0.7), (0.71, 0.6, 0.18, 31, 1.4)]
    for li, (ph, amp, shift, base, pshift) in enumerate(layers):
        top = base + amp * (7 * np.sin(TAU * (X * 2 + t + ph)) + 4 * np.sin(TAU * (X * 5 - 2 * t + ph) + 1) + 3 * np.sin(TAU * (X * 9 + 3 * t + ph)))
        top = top - np.abs(np.sin(TAU * (X * (4 + li) - t + 0.5 + ph))) * 8 * amp
        depth = np.clip((yy - top) / np.maximum(1, H - top), 0, 1)
        inside = smooth(-0.25, 0.25, yy - top)
        col = pal([(0, 0x6E0C08), (.25, 0xD01E0A), (.55, 0xFF6A0C), (.8, 0xFFB81E), (1, 0xFFF2A0)], np.clip(depth * 1.25 + shift, 0, 1))
        img = img * (1 - inside[..., None]) + col * inside[..., None]
        img += np.array([255, 70, 10]) / 255.0 * (np.clip(1 - np.abs(yy - top) / 5.0, 0, 1) * 0.2 * (1 - inside))[..., None]
    r = np.random.RandomState(8)
    for _ in range(18):
        x0, y0, kk = r.randint(0, W), r.randint(0, H), r.randint(1, 3)
        y = (y0 - H * t * kk) % H
        x = (x0 + 1.5 * math.sin(TAU * (t * kk + x0 / 5.0))) % W
        put(img, x, y, (1.0, 0.8, 0.3), 0.9, 0.4)
    return img


# ===================================================================== pulso
def pulso(f):
    t = f / FR
    img = vgrad([(0, 0x0A0430), (.4, 0x3A0F6B), (.56, 0xB0307A), (.57, 0x10062A), (1, 0x1A0A44)])
    stars(img, 3, 20, t, ymax=26, color=(1, 1, 1))
    cy = 29
    dd = np.sqrt((xx - 19.5) ** 2 + (yy - cy) ** 2)
    sc = pal([(0, 0xFFE15A), (.5, 0xFF7A8E), (1, 0xFF2DA6)], np.clip((yy - (cy - 11)) / 22.0, 0, 1))
    m = ((yy - cy + 12) - 4.0 * t) % 4
    width = np.clip((yy - (cy - 6)) / 5.0, 0, 3) * (yy > cy - 4)
    slit = np.clip((width - m) / 0.25 + 0.5, 0, 1)
    sun = smooth(11.15, 10.85, dd) * (1 - slit)
    img = img * (1 - sun[..., None]) + sc * sun[..., None]
    img += np.array([255, 60, 160]) / 255.0 * (np.exp(-np.clip(dd - 11, 0, None) / 3.0) * 0.28 * (dd > 11))[..., None]
    ridge = 36 - (smooth_noise(2, 8, 3)[0] * 6)
    rm = smooth(-0.15, 0.15, yy - ridge[None, :]) * smooth(37.15, 36.85, yy)
    img = img * (1 - rm[..., None]) + (hexc(0x120633) / 255.0) * rm[..., None]
    fl = smooth(36.85, 37.15, yy)
    img = img * (1 - fl[..., None]) + (hexc(0x12063A) / 255.0) * fl[..., None]
    for k in range(8):
        yk = 37 + (H - 37) * (((k + t) / 8.0) ** 2)
        sg = 0.35 + 0.4 * (yk - 37) / 27.0
        img += np.array([255, 60, 190]) / 255.0 * (np.exp(-((yy - yk) / sg) ** 2) * fl * 0.95)[..., None]
    for j in range(-9, 10):
        xl = 19.5 + j * 4.2 * (yy - 36) / 27.0
        img += np.array([40, 220, 255]) / 255.0 * (np.exp(-((xx - xl) / 0.4) ** 2) * fl * 0.8)[..., None]
    img += (np.exp(-((yy - 36.5) / .6) ** 2) * 0.35 * (0.6 + 0.4 * math.sin(TAU * t)))[..., None]
    return img


# ===================================================================== arcoiris
def arcoiris(f):
    t = f / FR
    h = ((xx * 0.8 + yy * 0.55 + 2.2 * np.sin(yy / 9.0 + TAU * t)) / 40.0 - t) % 1.0
    img = hsv(h, 0.62, 0.97) * (0.84 + 0.16 * np.sin((xx + yy) * 1.2 + TAU * t))[..., None]
    img *= (0.92 + 0.12 * fbm(40, 6, 10, 3))[..., None]
    stars(img, 17, 22, t, color=(1, 1, 1), big=0.5)
    edge = np.minimum(np.minimum(xx, W - 1 - xx), np.minimum(yy, H - 1 - yy))
    img *= (0.45 + 0.55 * smooth(0, 2.2, edge))[..., None]
    return img


# ===================================================================== mareas
def mareas(f):
    t = f / FR
    hz = 27
    img = vgrad([(0, 0x1A1B52), (.25, 0x5A3B8A), (.42, 0xF08A62), (.43, 0x0F2A5C), (1, 0x061434)])
    stars(img, 6, 14, t, ymax=14)
    rad = 9 + 0.4 * math.sin(TAU * t)
    sun = smooth(rad + 0.2, rad - 0.2, np.hypot(xx - 19.5, yy - hz)) * smooth(hz + 0.15, hz - 0.15, yy)
    sc = pal([(0, 0xFFF1A8), (1, 0xFF8A5C)], np.clip((yy - (hz - 9)) / 9.0, 0, 1))
    img = img * (1 - sun[..., None]) + sc * sun[..., None]
    img += np.array([255, 150, 90]) / 255.0 * (np.exp(-np.clip(np.hypot(xx - 19.5, yy - hz) - rad, 0, None) / 3) * 0.3 * (yy < hz))[..., None]
    sea = smooth(hz - 0.15, hz + 0.15, yy)
    d = np.clip((yy - hz) / (H - hz), 0, 1)
    wave = np.sin(TAU * (X * (3 + d * 5) + d * 3 - t))
    wm = smooth(0.5, 0.6, wave) * sea
    img = img * (1 - wm[..., None]) + (hexc(0x2E6FC4) / 255.0 * (0.6 + 0.6 * d)[..., None] + 0.055) * wm[..., None]
    gl = np.exp(-((xx - 19.5) / (2.2 + d * 7)) ** 2) * smooth(0.1, 0.2, np.sin(TAU * (yy * 0.33 - 2 * t))) * sea * np.clip(1 - d * 1.1, 0, 1)
    img += np.array([255, 170, 90]) / 255.0 * (gl * 1.1)[..., None]
    foam = smooth(0.9, 0.95, np.sin(TAU * (X * 6 - t + d * 4))) * sea * (d > 0.25)
    img = np.clip(img + (foam * 0.28)[..., None], 0, 1.5)
    return img


FIELDS = {
    'eclipse': eclipse, 'tormenta': tormenta, 'nieve': nieve, 'luciernagas': luciernagas, 'oroliquido': oroliquido,
    'cristal': cristal, 'portal': portal, 'dragon': dragon, 'aurora': aurora, 'galaxia': galaxia, 'lava': lava,
    'llamas': llamas, 'pulso': pulso, 'arcoiris': arcoiris, 'mareas': mareas,
}
