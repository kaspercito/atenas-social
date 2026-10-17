#!/usr/bin/env python3
"""Genera capas animadas de Atenas: 12 fotogramas en bucle perfecto, tira vertical de 512x(256*12).
Uso: python3 gen_capes.py <carpeta_del_proyecto_cliente> <carpeta_del_proyecto_mod>
Cada fotograma es la hoja 64x32 de siempre a 8x; la cara exterior de la capa (10x16) ocupa x 8..88, y 8..136."""
import os, sys
import numpy as np
from PIL import Image

W, H, FR, TAU = 40, 64, 12, 2 * np.pi
yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
X, Y = (xx + 0.5) / W, (yy + 0.5) / H
AX, AY = X * 10.0, Y * 16.0


def pal(stops, t):
    t = np.clip(t, 0, 1)
    pos = [s[0] for s in stops]
    out = np.zeros(t.shape + (3,), np.float32)
    for c in range(3):
        out[..., c] = np.interp(t, pos, [s[1][c] / 255.0 for s in stops])
    return out


def mix(a, b, t):
    t = np.asarray(t, np.float32)[..., None] if np.ndim(t) == 2 else t
    return a * (1 - t) + np.asarray(b, np.float32) * t


def waves(seed, n, t, kmax=3):
    rng = np.random.RandomState(seed)
    v = np.zeros((H, W), np.float32)
    tot = 0.0
    for _ in range(n):
        kx, ky = rng.randint(-kmax, kmax + 1), rng.randint(-kmax, kmax + 1)
        if kx == 0 and ky == 0:
            kx = 1
        m = rng.choice([-2, -1, 1, 2])
        ph, a = rng.rand() * TAU, rng.rand() * 0.5 + 0.5
        v += a * np.sin(TAU * (kx * X * 1.1 + ky * Y * 0.7) + ph + TAU * m * t)
        tot += a
    return v / tot


def hsv(h, s, v):
    h = (h % 1.0) * 6
    i = np.floor(h).astype(int) % 6
    f = h - np.floor(h)
    p, q, t = v * (1 - s), v * (1 - s * f), v * (1 - s * (1 - f))
    r = np.choose(i, [v, q, p, p, t, v]); g = np.choose(i, [t, v, v, q, p, p]); b = np.choose(i, [p, p, t, v, v, q])
    return np.stack([r, g, b], -1)


def put(col, x, y, c, k=1.0):
    xi, yi = int(round(x)), int(round(y))
    if 0 <= xi < W and 0 <= yi < H:
        col[yi, xi] = np.clip(col[yi, xi] * (1 - k) + np.asarray(c, np.float32) * k, 0, 1.6)


def stars(col, seed, n, t, strength=0.9, ymax=H):
    rng = np.random.RandomState(seed)
    for _ in range(n):
        x, y, ph = rng.randint(0, W), rng.randint(0, ymax), rng.rand()
        b = 0.35 + 0.65 * (0.5 + 0.5 * np.sin(TAU * (t + ph)))
        put(col, x, y, (0.85, 0.9, 1.0), strength * b * 0.7)


# ------------------------------------------------------------------ capas
def eclipse(f):
    t = f / FR
    cx, cy, R = 5.0, 6.8, 2.6
    dx, dy = AX - cx, AY - cy
    r, th = np.hypot(dx, dy), np.arctan2(dy, dx)
    col = pal([(0, (5, 3, 14)), (0.7, (16, 6, 32)), (1, (56, 18, 40))], Y)
    stars(col, 3, 22, t, ymax=40)
    out = np.clip(r - R, 0, None)
    pulse = 0.86 + 0.14 * np.sin(TAU * t)
    glow = np.exp(-out / 1.7) * 0.6 * pulse
    rays = np.clip(np.cos(14 * th - TAU * t), 0, 1) ** 5 * np.exp(-out / 2.8) * (r > R)
    rays2 = np.clip(np.cos(9 * th + TAU * t + 1.0), 0, 1) ** 6 * np.exp(-out / 2.0) * (r > R) * 0.6
    gold, orange, white = np.array([1.0, 0.74, 0.28]), np.array([1.0, 0.45, 0.12]), np.array([1.0, 0.97, 0.86])
    col = col + glow[..., None] * gold + (rays * pulse)[..., None] * orange + rays2[..., None] * gold * 0.8
    ring = np.exp(-((r - R) / 0.3) ** 2)
    col = mix(col, white, ring * 0.95 * pulse)
    disc = r < R - 0.12
    col[disc] = np.array([0.01, 0.008, 0.03]) + 0.03 * np.exp(-((r[disc] - (R - 0.12)) / 0.5) ** 2)[..., None]
    a = TAU * t - np.pi / 2
    d = np.hypot(AX - (cx + R * np.cos(a)), AY - (cy + R * np.sin(a)))
    col += (np.exp(-(d / 0.55) ** 2) * 1.3)[..., None] * white
    col += (np.exp(-(1.0 - Y) * 11) * 0.5)[..., None] * np.array([0.9, 0.32, 0.12])
    return col


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
        dist = np.full((H, W), 99.0, np.float32)
        for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
            vx, vy = x2 - x1, y2 - y1
            u = np.clip(((xx - x1) * vx + (yy - y1) * vy) / (vx * vx + vy * vy), 0, 1)
            dist = np.minimum(dist, np.hypot(xx - (x1 + u * vx), yy - (y1 + u * vy)))
        col += (np.exp(-(dist / 0.9) ** 2) * 1.4 * flash)[..., None] * np.array([0.95, 0.97, 1.0])
        col += (np.exp(-(dist / 3.2) ** 2) * 0.35 * flash)[..., None] * np.array([0.5, 0.6, 1.0])
    rng = np.random.RandomState(5)
    for _ in range(38):
        x0, y0, k = rng.randint(-4, W), rng.randint(0, H), rng.choice([1, 2])
        y = (y0 + k * H * f / FR) % H
        for j in range(4):
            put(col, x0 + (y - j) * 0.18, (y - j) % H, (0.62, 0.78, 1.0), 0.6 - 0.13 * j)
    return col


def nieve(f):
    t = f / FR
    col = pal([(0, (6, 10, 34)), (0.55, (14, 34, 76)), (1, (44, 98, 128))], Y)
    band = np.exp(-((Y - 0.42 + 0.05 * np.sin(TAU * (X * 1.5 + t))) / 0.09) ** 2) * (0.5 + 0.5 * np.sin(TAU * (X * 2 - t)))
    col += band[..., None] * np.array([0.05, 0.38, 0.32])
    stars(col, 9, 16, t, ymax=34)
    rng = np.random.RandomState(21)
    for _ in range(44):
        x0, y0, k, ph = rng.randint(0, W), rng.randint(0, H), rng.choice([1, 1, 2]), rng.rand()
        x = x0 + 1.6 * np.sin(TAU * (t + ph))
        y = (y0 + k * H * f / FR) % H
        b = 0.55 if k == 1 else 1.0
        put(col, x, y, (0.93, 0.97, 1.0), b)
        if k == 2 and (x0 + y0) % 3 == 0:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                put(col, x + dx, (y + dy) % H, (0.8, 0.9, 1.0), 0.45)
    drift = 56 + 2.2 * np.sin(TAU * (X * 2 + 0.2)) + 1.2 * np.sin(TAU * X * 5)
    m = yy > drift
    snow = pal([(0, (170, 205, 245)), (1, (110, 150, 205))], np.clip((yy - drift) / 8, 0, 1))
    col[m] = snow[m]
    return col


def luciernagas(f):
    t = f / FR
    col = pal([(0, (3, 10, 18)), (0.6, (6, 30, 30)), (1, (8, 44, 36))], Y)
    moon = np.exp(-(np.hypot(AX - 7.6, AY - 2.2) / 1.0) ** 2)
    col += (np.exp(-(np.hypot(AX - 7.6, AY - 2.2) / 3.0) ** 2) * 0.12)[..., None] * np.array([0.6, 0.9, 0.9])
    col = mix(col, (0.85, 0.95, 0.9), moon * 0.9)
    fog = 0.5 + 0.5 * np.sin(TAU * (X * 1.3 + t))
    col += (np.exp(-((Y - 0.8) / 0.08) ** 2) * fog * 0.12)[..., None] * np.array([0.3, 0.9, 0.7])
    rng = np.random.RandomState(4)
    sil = np.zeros((H, W), bool)
    for _ in range(9):
        cx, h = rng.uniform(-1, W + 1), rng.randint(16, 30)
        top = H - h
        w = np.clip((yy - top) * 0.34, 0, None)
        sil |= (yy > top) & (np.abs(xx - cx) < w * (1.0 - 0.18 * ((yy - top) % 6) / 6))
    col[sil] = np.array([0.008, 0.025, 0.03])
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


def oroliquido(f):
    t = f / FR
    v = waves(13, 7, t)
    c = 0.5 + 0.5 * v
    ridge = 1 - np.abs(2 * c - 1)
    k = 0.55 * c + 0.45 * ridge ** 2
    col = pal([(0, (52, 26, 4)), (0.3, (140, 82, 10)), (0.6, (226, 160, 34)), (0.82, (255, 218, 104)), (1, (255, 250, 200))], k ** 1.15)
    s = ((t * 1.6) % 1.6) - 0.3
    u = X * 0.6 + Y * 0.4
    col += (np.exp(-((u - s) / 0.07) ** 2) * 0.5)[..., None] * np.array([1.0, 0.95, 0.7])
    rng = np.random.RandomState(2)
    for i in range(9):
        x, y, ph = rng.randint(2, W - 2), rng.randint(2, H - 2), rng.randint(0, FR)
        a = max(0.0, 1 - abs(((f - ph + FR / 2) % FR) - FR / 2) / 1.6)
        if a > 0:
            for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                put(col, x + dx, y + dy, (1, 1, 0.85), a * (1.0 if dx == dy == 0 else 0.5))
    col *= (0.82 + 0.18 * np.clip(np.minimum(xx, W - 1 - xx) / 2.0, 0, 1))[..., None]
    return col


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
    col = np.zeros((H, W, 3), np.float32)
    for i in range(n):
        m = i1 == i
        br = 0.5 + 0.5 * np.sin(TAU * (t + ph[i]))
        shade = 0.8 + 0.5 * np.clip(((AX - sx[i]) * -0.12 + (AY - sy[i]) * -0.08) + 0.5, 0, 1)
        col[m] = np.clip(base[i] * (0.38 + 0.7 * br ** 2) * shade[m][..., None], 0, 1.2)
    edge = np.clip(1 - (d2 - d1) / 0.32, 0, 1) ** 2
    col = mix(col, (0.9, 0.97, 1.0), edge * 0.85)
    s = ((t * 1.6) % 1.6) - 0.3
    u = X * 0.55 + Y * 0.45
    sw = np.exp(-((u - s) / 0.12) ** 2)
    col += (sw * 0.42)[..., None] * hsv(u * 2.2, 0.55, 1.0)
    for i in range(0, n, 3):
        a = max(0.0, 1 - abs(((f - int(ph[i] * FR) + FR / 2) % FR) - FR / 2) / 1.5)
        if a > 0:
            x, y = sx[i] * 4, sy[i] * 4
            for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                put(col, x + dx, y + dy, (1, 1, 1), a * (1.0 if dx == dy == 0 else 0.55))
    return col


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


def dragon(f):
    """Escamas de dragon superpuestas (pintadas de arriba abajo) con una ola de brasas que las recorre."""
    t = f / FR
    col = np.zeros((H, W, 3), np.float32) + np.array([0.03, 0.004, 0.01])
    inside = np.zeros((H, W), np.float32)
    R, pitch = 5.2, 3.6
    rowi = 0
    cy = -2.0
    while cy < H + 4:
        off = 4.0 if rowi % 2 else 0.0
        cx = -4.0 + off
        while cx < W + 4:
            d = np.hypot(xx - cx, yy - cy) / R
            m = d < 1.0
            if m.any():
                rnd = np.sin(cx * 12.9898 + cy * 78.233) * 43758.5453
                var = 0.8 + 0.4 * (rnd - np.floor(rnd))
                lit = np.clip(1.15 - d * 1.1 + 0.25 * ((cy - yy) / R), 0, 1) * var
                body = pal([(0, (36, 3, 8)), (0.5, (122, 14, 20)), (1, (214, 48, 32))], lit)
                rim = np.clip((d - 0.84) / 0.16, 0, 1)[..., None]
                body = body * (1 - 0.8 * rim)
                hl = np.exp(-(np.hypot(xx - (cx - 1.2), yy - (cy - 1.8)) / 1.5) ** 2) * 0.3
                body = body + hl[..., None] * np.array([1.0, 0.55, 0.4])
                col[m] = body[m]
                inside[m] = np.clip(1.0 - d[m] * 1.15, 0, 1)
            cx += 8.0
        cy += pitch
        rowi += 1
    w = (0.5 + 0.5 * np.sin(TAU * (t - (X * 0.6 + Y * 0.9)))) ** 4
    col += (w * inside * 1.5)[..., None] * np.array([1.0, 0.5, 0.08])
    col += (w * 0.25 * (inside < 0.05))[..., None] * np.array([1.0, 0.35, 0.05])
    rng = np.random.RandomState(6)
    for _ in range(12):
        x0, y0, k, ph = rng.randint(0, W), rng.randint(0, H), rng.choice([1, 2]), rng.rand()
        y = (y0 - k * H * f / FR) % H
        put(col, x0 + 1.2 * np.sin(TAU * (t + ph)), y, (1.0, 0.8, 0.3), 0.9)
        put(col, x0 + 1.2 * np.sin(TAU * (t + ph)), (y + 1) % H, (1.0, 0.45, 0.1), 0.5)
    return col


CAPES = {  # id: (funcion, fotograma para el icono)
    'eclipse': (eclipse, 0), 'tormenta': (tormenta, 3), 'nieve': (nieve, 4), 'luciernagas': (luciernagas, 2),
    'oroliquido': (oroliquido, 4), 'cristal': (cristal, 3), 'portal': (portal, 0), 'dragon': (dragon, 5),
}


def to_img(col):
    a = (np.clip(col, 0, 1) * 255 + 0.5).astype(np.uint8)
    return np.repeat(np.repeat(a, 2, axis=0), 2, axis=1)  # 128 x 80


def frame_sheet(outer):
    inner = (outer * 0.62).astype(np.uint8)
    fr = np.zeros((256, 512, 4), np.uint8)
    po = np.pad(outer, ((8, 8), (8, 8), (0, 0)), mode='edge')
    fr[0:144, 0:96, :3] = po
    fr[0:144, 0:96, 3] = 255
    pi = np.pad(inner, ((8, 8), (0, 8), (0, 0)), mode='edge')
    fr[0:144, 96:184, :3] = pi
    fr[0:144, 96:184, 3] = 255
    return fr


def icon(outer):
    im = Image.fromarray(outer).resize((18, 30), Image.LANCZOS).convert('RGBA')
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.paste(Image.new('RGBA', (20, 32), (0, 0, 0, 120)), (6, 0))
    out.paste(im, (7, 1))
    return out


def main(client_dir, mod_dir):
    targets = [
        (os.path.join(client_dir, 'src/main/resources/assets/atenasclient/textures/cosmetics')),
        (os.path.join(mod_dir, 'src/main/resources/assets/atenassocial/textures/cosmetics')),
    ]
    for cid, (fn, ic) in CAPES.items():
        outers = [to_img(fn(f)) for f in range(FR)]
        strip = np.concatenate([frame_sheet(o) for o in outers], axis=0)
        for base in targets:
            os.makedirs(os.path.join(base, 'capes'), exist_ok=True)
            os.makedirs(os.path.join(base, 'icons'), exist_ok=True)
            Image.fromarray(strip, 'RGBA').save(os.path.join(base, 'capes', cid + '.png'), optimize=True)
            icon(outers[ic]).save(os.path.join(base, 'icons', 'cape_' + cid + '.png'))
        print(cid, 'ok')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
