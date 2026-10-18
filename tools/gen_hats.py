#!/usr/bin/env python3
"""Sombreros HD de Atenas: genera malla (.atm), atlas 1024x1024 e icono 3D de cada uno.
Uso: python3 gen_hats.py <carpeta_assets_atenasclient> [id ...]     (sin ids = todos)   |   python3 gen_hats.py --preview <salida> [id ...]"""
import os, sys, math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hatlib import *
from hatpaint import *

SIZE = 1024
HATS = {}
def hat(f):
    HATS[f.__name__[4:]] = f
    return f

def squ(n_hi=6.0, n_lo=2.4, t0=0.3, t1=1.0):
    def shape(th, t):
        n = lerp(n_hi, n_lo, float(smoothstep(t0, t1, t)))
        return superellipse(th, 1.0, 1.0, n)
    return shape

def latherect(m, profile, shape, den, minw=64, maxw=None, maxh=None):
    """Reserva la region de un solido de revolucion; si no cabe a esa densidad, la baja."""
    L, C = circ_len(profile, shape)
    maxw = maxw or (m.atlas.size - 50)
    maxh = maxh or (m.atlas.size * 0.62)
    den = min(den, maxw / max(C, 1e-6), maxh / max(L, 1e-6))
    return m.atlas.alloc(max(minw, int(C * den)), max(32, int(L * den)))

# =============================================================================== GORRA
@hat
def hat_gorra():
    m = Model(SIZE)
    body = m.part('body')
    P1 = hexc('#6a3fe0'); P2 = hexc('#2f1a7a'); W = hexc('#f4f1ff')
    prof = [(4.4, 4.5), (4.64, 5.5), (4.8, 7.0), (4.62, 7.9), (4.05, 8.55), (3.0, 9.0), (1.6, 9.25), (0.06, 9.32)]
    shape = squ(5.6, 1.8, 0.45, 0.66)
    rect = latherect(m, prof, shape, 46)
    rw, rh = rect[2], rect[3]
    def paint(Wd, Hd):
        a = fabric(Wd, Hd, P1, seed=3, period=3.0, amt=0.16, nap=0.10)
        # sombra suave del borde inferior (banda sudadera) y de la parte alta
        a = vignette_edges(a, 0.18, 3)
        yy, xx = np.mgrid[0:Hd, 0:Wd].astype(np.float32)
        band = smoothstep(0.0, 0.10, yy / Hd)
        a = mul(a, 0.62 + 0.38 * band)
        for k in range(6):
            stitch_v(a, (k + 0.5) / 6, color=(0.86, 0.82, 1.0), dash=7, gap=5, width=1)
        # costuras dobles junto a cada tira
        for k in range(6):
            stitch_v(a, (k + 0.5) / 6 + 0.006, color=P2, dash=7, gap=5, width=1)
        # ojales
        cv = Canvas(Wd, Hd, a)
        def fn(d, S):
            for k in range(6):
                x = (k + 0.5) / 6 * Wd + Wd * 0.03
                y = Hd * 0.78
                d.ellipse([(x - 6) * S, (y - 6) * S, (x + 6) * S, (y + 6) * S], fill=(26, 12, 70, 255))
                d.ellipse([(x - 3) * S, (y - 3) * S, (x + 3) * S, (y + 3) * S], fill=(8, 4, 28, 255))
                d.ellipse([(x - 6) * S, (y - 6) * S, (x + 6) * S, (y + 6) * S], outline=(190, 170, 255, 255), width=S)
        cv.draw(fn)
        return cv.arr
    m.atlas.paste(rect, paint(rw, rh), wrap_x=True)
    lat = lathe(body, m, prof, rect, nu=72, nv=18, shape=shape)

    # boton
    br = m.atlas.alloc(48, 48); m.atlas.paste(br, disc(48, 48, hexc('#b99cff'), hexc('#3a1d92')))
    ellipsoid(body, m, (0, 9.22, 0), (0.95, 0.5, 0.95), br, nu=20, nv=8, lat0=0.0)

    # visera
    vt = m.atlas.alloc(320, 288)
    def paint_v(Wd, Hd):
        a = fabric(Wd, Hd, P2, seed=8, period=3, amt=0.14, nap=0.08)
        yy, xx = np.mgrid[0:Hd, 0:Wd].astype(np.float32)
        # costuras en paralelo al borde
        for off in (14, 24):
            a[int(Hd - off):int(Hd - off) + 2, :, :3] = np.minimum(1, P1 * 1.25)
        return a
    m.atlas.paste(vt, paint_v(320, 288))
    vb = m.atlas.alloc(160, 144)
    m.atlas.paste(vb, mul(fabric(160, 144, P2 * 0.55, seed=9), 0.85))
    ve = m.atlas.alloc(128, 16)
    m.atlas.paste(ve, solid(128, 16, P1 * 0.8))
    def vfn(a, b):
        wid = 4.95 * (1 - 0.30 * b ** 2.2)
        x = (a - 0.5) * 2 * wid
        z = 4.3 + 5.2 * b
        y = 5.0 - 0.45 * b - 1.0 * b * (2 * a - 1) ** 2 * 0.85
        return (x, y, z)
    vis = m.part('visera')
    slab(vis, m, vfn, 24, 12, 0.42, vt, vb, ve)

    # logo Atenas (decal)
    lg = m.atlas.alloc(192, 192)
    cv = Canvas(192, 192)
    def fn(d, S):
        c = (255, 255, 255, 255)
        d.polygon([(96 * S, 14 * S), (172 * S, 52 * S), (172 * S, 140 * S), (96 * S, 178 * S), (20 * S, 140 * S), (20 * S, 52 * S)], outline=(205, 190, 255, 255), width=5 * S)
        d.line([(58 * S, 138 * S), (96 * S, 44 * S), (134 * S, 138 * S)], fill=c, width=14 * S, joint='curve')
        d.line([(72 * S, 108 * S), (120 * S, 108 * S)], fill=c, width=11 * S)
    cv.draw(fn)
    cv.glow(96, 96, 90, (0.7, 0.55, 1.0), 0.28, 2.4)
    m.atlas.paste(lg, cv.arr)
    nrm = (0, 0.33, 1.0)
    pos, nn, uu = lat.at(0.0, 7.2, 0.06)
    decal(body, pos, nn, uu, 4.2, 4.2, lg, off=0.0)
    return m, None

# ------------------------------------------------------------------------------- ayudas comunes
def sprite(m, W, H, arr, wrap=False):
    r = m.atlas.alloc(W, H)
    m.atlas.paste(r, arr, wrap_x=wrap)
    return r

def star_sprite(W=64, color=(1.0, 0.95, 0.7), core=(1, 1, 1)):
    cv = Canvas(W, W)
    cv.glow(W / 2, W / 2, W * 0.5, color, 0.55, 2.2)
    def fn(d, S):
        star4(d, S, W / 2, W / 2, W * 0.46, tuple(int(255 * c) for c in color) + (255,), 0.10)
        star4(d, S, W / 2, W / 2, W * 0.22, tuple(int(255 * c) for c in core) + (255,), 0.38)
    cv.draw(fn)
    return cv.arr

def glint_parts(m, part, lat, spots, size, rect, off=0.12):
    """Estrellitas emisivas pegadas a un solido de revolucion. spots = [(theta_deg, y, escala)]"""
    for th, y, k in spots:
        pos, nn, uu = lat.at(math.radians(th), y, 0.0)
        decal(part, pos, nn, uu, size * k, size * k, rect, off=off)

def pil_text_A(d, S, cx, cy, h, col, w):
    d.line([((cx - h * 0.42) * S, (cy + h * 0.5) * S), (cx * S, (cy - h * 0.5) * S), ((cx + h * 0.42) * S, (cy + h * 0.5) * S)], fill=col, width=int(w * S), joint='curve')
    d.line([((cx - h * 0.22) * S, (cy + h * 0.18) * S), ((cx + h * 0.22) * S, (cy + h * 0.18) * S)], fill=col, width=int(w * 0.8 * S))

def ring_shape(a, n=6.0):
    return lambda th, t: superellipse(th, 1.0, 1.0, n)

# =============================================================================== MAGO
@hat
def hat_mago():
    m = Model(2048)
    cone = m.part('cono'); brim = m.part('ala'); band = m.part('cinta'); gold = m.part('oro')
    gl = m.part('brillos', F_EMIT | F_PULSE)
    shape = squ(6.0, 2.0, 0.0, 0.10)
    def bend(y):
        t = max(0.0, (y - 13.0) / 15.0)
        return (1.1 * t * t, -4.2 * t * t)
    prof = [(4.5, 8.0), (4.25, 9.6), (3.55, 12.2), (2.8, 15.5), (2.1, 19.0), (1.5, 22.5), (0.95, 25.8), (0.45, 28.2), (0.05, 29.6)]
    rect = latherect(m, prof, shape, 34)
    rw, rh = rect[2], rect[3]
    def paint(W, H):
        top = hexc('#7b3ff0'); bot = hexc('#1c1260'); mid = hexc('#3e22a8')
        t = np.linspace(0, 1, H)[:, None, None]
        c = np.where(t < 0.5, bot * (1 - t * 2) + mid * (t * 2), mid * (1 - (t - 0.5) * 2) + top * ((t - 0.5) * 2))
        a = np.ones((H, W, 4), np.float32); a[..., :3] = np.repeat(c, W, axis=1)
        n = fbm(W, H, scale=10, octaves=4, seed=21)
        a = mul(a, 0.8 + 0.4 * n)
        a = mul(a, 1 + 0.08 * np.sin(np.arange(W) * 2 * math.pi / 3.0)[None, :] * np.sin(np.arange(H) * 2 * math.pi / 3.0)[:, None])
        # nebulosa dorada
        neb = fbm(W, H, scale=5, octaves=5, seed=33) ** 3
        a = tint_add(a, hexc('#ffb0f0'), neb * 0.22)
        r_ = np.random.RandomState(5)
        cv = Canvas(W, H, a)
        pts_r = prof
        def r_at(v):
            return float(np.interp(v, np.linspace(0, 1, len(pts_r)), [p[0] for p in pts_r]))
        stars = []
        for i in range(34):
            u = r_.rand(); v = 0.08 + 0.88 * r_.rand()
            stars.append((u * W, v * H, 5 + 9 * r_.rand(), r_.rand()))
        def fn(d, S):
            for x, y, rad, k in stars:
                col = (255, 245, 200, 255) if k > 0.35 else (255, 255, 255, 235)
                sx = min(3.2, 4.5 / max(0.5, r_at(y / H)))
                pts = []
                for i in range(8):
                    an = i * math.pi / 4
                    rr = rad if i % 2 == 0 else rad * 0.22
                    pts.append(((x + math.cos(an) * rr * sx) * S, (y + math.sin(an) * rr) * S))
                d.polygon(pts, fill=col)
            # luna creciente grande en el frente (u ~ 0)
            for ux in (0.0, 1.0):
                cx_, cy_ = ux * W, H * 0.40
                R = H * 0.085
                sxx = 1.6
                d.ellipse([(cx_ - R * sxx) * S, (cy_ - R) * S, (cx_ + R * sxx) * S, (cy_ + R) * S], fill=(255, 226, 120, 255))
                d.ellipse([(cx_ - R * sxx + R * 0.7 * sxx) * S, (cy_ - R * 1.05) * S, (cx_ + R * sxx + R * 0.7 * sxx) * S, (cy_ + R * 0.95) * S], fill=(66, 36, 160, 255))
        cv.draw(fn)
        for x, y, rad, k in stars:
            if k > 0.6: cv.glow(x, y, rad * 2.4, (1.0, 0.9, 0.6), 0.35, 2)
        a = cv.arr
        return a
    m.atlas.paste(rect, paint(rw, rh), wrap_x=True)
    lat = lathe(cone, m, prof, rect, nu=64, nv=40, shape=shape, bend=bend)

    # ala: anillo ondulado con borde
    br = m.atlas.alloc(1200, 170)
    def paint_brim(W, H):
        a = np.ones((H, W, 4), np.float32)
        t = np.linspace(0, 1, H)[:, None, None]
        c = hexc('#2a1a78') * (1 - t) + hexc('#3a2399') * t
        a[..., :3] = np.repeat(c, W, axis=1)
        n = fbm(W, H, scale=24, octaves=4, seed=44)
        a = mul(a, 0.8 + 0.4 * n)
        a = mul(a, 1 + 0.07 * np.sin(np.arange(W) * 2 * math.pi / 3.0)[None, :] * np.sin(np.arange(H) * 2 * math.pi / 3.0)[:, None])
        cv = Canvas(W, H, a)
        def fn(d, S):
            d.rectangle([0, (H - 12) * S, W * S, (H - 7) * S], fill=(255, 215, 110, 255))   # ribete de oro en el borde externo
            d.rectangle([0, (H - 24) * S, W * S, (H - 21) * S], fill=(255, 215, 110, 160))
            for k in range(30):
                x = (k + 0.5) / 30 * W
                star5(d, S, x, H * 0.55, 9, (255, 226, 130, 255))
        cv.draw(fn)
        return cv.arr
    m.atlas.paste(br, paint_brim(1200, 170), wrap_x=True)
    bb = m.atlas.alloc(1200, 170)
    m.atlas.paste(bb, mul(paint_brim(1200, 170), 0.55), wrap_x=True)
    be = m.atlas.alloc(1200, 10)
    m.atlas.paste(be, solid(1200, 10, hexc('#ffd770')), wrap_x=True)
    rin = lambda th: 4.55 * superellipse(th, 1, 1, 6.0)
    def bfn(a, b):
        th = a * 2 * math.pi
        r = lerp(rin(th), 9.4, b)
        y = 7.9 - 0.9 * b * b + 0.5 * b * math.sin(3 * th + 0.8) + 0.35 * b * math.sin(5 * th)
        return (r * math.sin(th), y, r * math.cos(th))
    i0 = len(brim.quads)
    slab(brim, m, bfn, 72, 10, 0.32, br, bb, be, sides=('b1',))
    # cinta
    prof_b = [(4.62, 8.3), (4.62, 9.1), (4.4, 10.2)]
    bandrect = latherect(m, [(4.6, 8.3), (4.4, 10.2)], shape, 44)
    def paint_band(W, H):
        a = fabric(W, H, hexc('#a8142c'), seed=9, period=3, amt=0.2, nap=0.06)
        a = vignette_edges(a, 0.25, 2.5)
        a[0:6, :, :3] = hexc('#ffd770'); a[H - 6:H, :, :3] = hexc('#ffd770')
        for k in range(0, W, 24):
            a[H // 2 - 2:H // 2 + 2, k:k + 10, :3] = hexc('#ffd770')
        return a
    m.atlas.paste(bandrect, paint_band(bandrect[2], bandrect[3]), wrap_x=True)
    bshape = lambda th, t: shape(th, 0.014 + 0.086 * t)
    latb = lathe(band, m, [(4.66, 8.3), (4.62, 9.3), (4.46, 10.3)], bandrect, nu=64, nv=6, shape=bshape, smooth=False)
    # hebilla
    hb = m.atlas.alloc(192, 192)
    def paint_hb(W, H):
        cv = Canvas(W, H, metal(W, H, hexc('#fff0a0'), hexc('#a0661a'), seed=3, bands=1.2, brushed=False))
        def fn(d, S):
            d.rectangle([14 * S, 14 * S, (W - 14) * S, (H - 14) * S], fill=(0, 0, 0, 0))
        cv.arr[:, :, 3] = 1
        mask = np.zeros((H, W), np.float32)
        mask[8:H - 8, 8:W - 8] = 1; mask[40:H - 40, 40:W - 40] = 0
        out = cv.arr.copy(); out[..., 3] = mask
        hole = np.zeros_like(out)
        return out
    m.atlas.paste(hb, paint_hb(192, 192))
    pos, nn, uu = latb.at(0.0, 9.3, 0.0)
    decal(gold, pos, nn, uu, 3.4, 3.2, hb, off=0.1)
    gm = m.atlas.alloc(96, 96); m.atlas.paste(gm, gem(96, 96, hexc('#2ff0b0'), facets=8))
    decal(gold, pos, nn, uu, 1.6, 1.6, gm, off=0.14)
    # estrellitas que laten
    st = m.atlas.alloc(64, 64); m.atlas.paste(st, star_sprite(64))
    spots = [(-25, 12.5, 1.0), (40, 14.2, 0.8), (110, 11.6, 0.9), (-80, 16.0, 0.8), (170, 15.0, 0.7), (-140, 13.4, 0.9), (20, 19.5, 0.7), (-60, 21.5, 0.6), (95, 22.5, 0.6), (-10, 25.2, 0.6), (150, 18.8, 0.7), (-150, 20.5, 0.6)]
    glint_parts(m, gl, lat, spots, 1.8, st, off=0.1)
    # punta brillante
    pos, nn, uu = lat.at(0.0, 29.0, 0.0)
    decal(gl, np.array(lat.fn(0.0, 0.985)), (0, 0.2, 1), (0, 1, 0), 2.6, 2.6, st, off=0.2)
    return m, None

# =============================================================================== CORONA
@hat
def hat_corona():
    m = Model(SIZE)
    gold = m.part('oro'); vel = m.part('terciopelo'); pearl = m.part('perlas'); gems = m.part('gemas')
    gl = m.part('brillos', F_EMIT | F_PULSE)
    shape = squ(6.0, 6.0)
    GOLD_L, GOLD_D = hexc('#ffe9a0'), hexc('#a3651c')
    # banda
    prof = [(4.7, 8.0), (4.72, 9.0), (4.6, 10.2)]
    rect = latherect(m, [(4.7, 8.0), (4.6, 10.2)], shape, 46)
    rw, rh = rect[2], rect[3]
    def paint_band(W, H):
        a = metal(W, H, GOLD_L, GOLD_D, seed=14, bands=1.6)
        cv = Canvas(W, H, a)
        def fn(d, S):
            # filigrana: ondas y puntos grabados
            d.rectangle([0, 0, W * S, 9 * S], fill=(120, 70, 12, 255))
            d.rectangle([0, (H - 9) * S, W * S, H * S], fill=(120, 70, 12, 255))
            for x in range(0, W, 28):
                d.ellipse([(x + 10) * S, (H / 2 - 5) * S, (x + 20) * S, (H / 2 + 5) * S], fill=(126, 74, 14, 255))
                d.arc([x * S, (H / 2 - 20) * S, (x + 28) * S, (H / 2 + 6) * S], 200, 340, fill=(120, 70, 12, 255), width=2 * S)
                d.arc([x * S, (H / 2 - 6) * S, (x + 28) * S, (H / 2 + 20) * S], 20, 160, fill=(120, 70, 12, 255), width=2 * S)
        cv.draw(fn)
        return cv.arr
    m.atlas.paste(rect, paint_band(rw, rh), wrap_x=True)
    lat = lathe(gold, m, [(4.72, 8.0), (4.76, 9.0), (4.62, 10.2)], rect, nu=72, nv=4, shape=shape, smooth=False)
    # aro de abajo (tubo)
    tr = m.atlas.alloc(512, 40); m.atlas.paste(tr, mul(metal(512, 40, GOLD_L, GOLD_D, seed=2, bands=1), 1.0), wrap_x=True)
    # picos
    spike_rect = m.atlas.alloc(256, 256); m.atlas.paste(spike_rect, metal(256, 256, GOLD_L, GOLD_D, seed=6, bands=1.4))
    pe = m.atlas.alloc(64, 64); m.atlas.paste(pe, disc(64, 64, hexc('#ffffff'), hexc('#b9b4c8')))
    # terciopelo (cupula interior)
    vr = m.atlas.alloc(256, 256)
    def paint_vel(W, H):
        a = fabric(W, H, hexc('#b01226'), seed=12, period=2.5, amt=0.3, nap=0.15)
        return a
    m.atlas.paste(vr, paint_vel(256, 256), wrap_x=True)
    ellipsoid(vel, m, (0, 9.1, 0), (4.3, 2.0, 4.3), vr, nu=36, nv=10, lat0=0.0)
    # picos: 8 alrededor, alternando alto/bajo
    N = 8
    for k in range(N):
        th = k * 2 * math.pi / N
        tall = (k % 2 == 0)
        h = 3.6 if tall else 2.4
        rr = 4.65 * superellipse(th, 1, 1, 6.0)
        cx, cz = rr * math.sin(th), rr * math.cos(th)
        # base de 4 puntos
        tx, tz = math.cos(th), -math.sin(th)   # tangente
        nx, nz = math.sin(th), math.cos(th)    # radial
        w = 1.25 if tall else 1.0; t = 0.45
        base = [(cx - tx * w - nx * t, 9.9, cz - tz * w - nz * t), (cx + tx * w - nx * t, 9.9, cz + tz * w - nz * t),
                (cx + tx * w + nx * t, 9.9, cz + tz * w + nz * t), (cx - tx * w + nx * t, 9.9, cz - tz * w + nz * t)]
        tip = (cx, 9.9 + h, cz)
        sr = spike_rect
        uvs = [(sr[0] + 4, sr[1] + sr[3] - 4), (sr[0] + sr[2] - 4, sr[1] + sr[3] - 4), (sr[0] + sr[2] / 2, sr[1] + 4)]
        for i in range(4):
            poly(gold, [base[i], base[(i + 1) % 4], tip], uvs)
        # perla en la punta
        pr_ = 0.62 if tall else 0.5
        ellipsoid(pearl, m, (cx, 9.9 + h + pr_ * 0.6, cz), (pr_, pr_, pr_), pe, nu=14, nv=8)
    # gemas en la banda: frente (rubi grande), lados y atras
    cols = ['#ff1f4d', '#25e08b', '#2f7bff', '#ff1f4d', '#c04bff', '#2f7bff', '#25e08b', '#ffd23a']
    for k in range(N):
        th = (k + 0.5) * 2 * math.pi / N if k else 0.0
        th = k * 2 * math.pi / N
        if k % 2 == 0: continue
        pos, nn, uu = lat.at(th, 9.0, 0.0)
        g = m.atlas.alloc(96, 96); m.atlas.paste(g, gem(96, 96, hexc(cols[k]), facets=8))
        decal(gems, pos, nn, uu, 1.7, 1.7, g, off=0.12)
    # rubi grande frontal
    pos, nn, uu = lat.at(0.0, 9.0, 0.0)
    g = m.atlas.alloc(128, 128); m.atlas.paste(g, gem(128, 128, hexc('#ff1740'), facets=10))
    decal(gems, pos, nn, uu, 2.4, 2.4, g, off=0.14)
    # destellos
    st = m.atlas.alloc(64, 64); m.atlas.paste(st, star_sprite(64, (1.0, 0.92, 0.6)))
    for k in range(0, N, 2):
        th = k * 2 * math.pi / N
        rr = 4.65 * superellipse(th, 1, 1, 6.0)
        decal(gl, (rr * math.sin(th) * 1.02, 9.9 + 3.6 + 0.9, rr * math.cos(th) * 1.02), (math.sin(th), 0.3, math.cos(th)), (0, 1, 0), 1.9, 1.9, st, off=0.0)
    pos, nn, uu = lat.at(0.0, 9.0, 0.0)
    decal(gl, pos, nn, uu, 2.9, 2.9, st, off=0.28)
    return m, None

# =============================================================================== GALERA
@hat
def hat_galera():
    m = Model(SIZE)
    body = m.part('copa'); brim = m.part('ala'); band = m.part('cinta'); gold = m.part('oro')
    gl = m.part('brillos', F_EMIT | F_PULSE)
    shape = squ(6.0, 4.0, 0.80, 0.98)
    prof = [(4.55, 7.9), (4.62, 9.5), (4.7, 12.5), (4.75, 15.0), (4.62, 15.55), (3.8, 15.75), (2.0, 15.82), (0.05, 15.84)]
    rect = latherect(m, prof, shape, 36)
    rw, rh = rect[2], rect[3]
    def paint(W, H):
        a = solid(W, H, hexc('#1a1a22'))
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        n = fbm(W, H, scale=14, octaves=3, seed=18)
        sheen = np.exp(-((((xx / W) % 1.0) - 0.20) ** 2) / 0.004) * 0.55 + np.exp(-((((xx / W) % 1.0) - 0.68) ** 2) / 0.003) * 0.35
        k = 0.7 + 0.5 * n + sheen
        a = mul(a, k)
        a = tint_add(a, (0.5, 0.52, 0.7), sheen * 0.25)
        for x in range(0, W, 10):
            a[:, x:x + 1, :3] = np.clip(a[:, x:x + 1, :3] * 1.3 + 0.02, 0, 1)
        a = vignette_edges(a, 0.15, 3)
        return a
    m.atlas.paste(rect, paint(rw, rh), wrap_x=True)
    lat = lathe(body, m, prof, rect, nu=72, nv=26, shape=shape)
    # ala con curl lateral
    br = m.atlas.alloc(800, 110); bb = m.atlas.alloc(800, 110); be = m.atlas.alloc(800, 10)
    def paint_brim(W, H, dark=1.0):
        a = solid(W, H, hexc('#17171f'))
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        n = fbm(W, H, scale=24, octaves=3, seed=25)
        a = mul(a, (0.75 + 0.5 * n) * dark)
        a = tint_add(a, (0.5, 0.5, 0.7), (np.exp(-((((xx / W) % 1.0) - 0.25) ** 2) / 0.004) * 0.3 + np.exp(-((((xx / W) % 1.0) - 0.75) ** 2) / 0.004) * 0.3) * (yy / H) * dark)
        a[H - 14:H - 8, :, :3] = hexc('#2d2d3c')
        return a
    m.atlas.paste(br, paint_brim(800, 110), wrap_x=True)
    m.atlas.paste(bb, paint_brim(800, 110, 0.6), wrap_x=True)
    m.atlas.paste(be, solid(800, 10, hexc('#101016')), wrap_x=True)
    rin = lambda th: 4.55 * superellipse(th, 1, 1, 6.0)
    def bfn(a, b):
        th = a * 2 * math.pi
        r = lerp(rin(th), 8.7, b)
        y = 8.0 + 1.9 * b ** 2.2 * math.sin(th) ** 2 - 0.15 * b * (1 - math.sin(th) ** 2) * 1.2
        return (r * math.sin(th), y, r * math.cos(th))
    slab(brim, m, bfn, 72, 10, 0.3, br, bb, be, sides=('b1',))
    # cinta satinada
    bandrect = latherect(m, [(4.7, 8.2), (4.76, 10.6)], shape, 40)
    def paint_band(W, H):
        a = solid(W, H, hexc('#b3162d'))
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        n = fbm(W, H, scale=12, octaves=3, seed=27)
        sheen = np.exp(-((((xx / W) % 1.0) - 0.22) ** 2) / 0.005) * 0.7
        a = mul(a, 0.8 + 0.4 * n + sheen * 0.6)
        a = tint_add(a, (1, 0.7, 0.7), sheen * 0.25)
        a[0:7, :, :3] = hexc('#e7c35a'); a[H - 7:H, :, :3] = hexc('#e7c35a')
        a[H // 2 - 1:H // 2 + 1, :, :3] = hexc('#f2d98a')
        return a
    m.atlas.paste(bandrect, paint_band(bandrect[2], bandrect[3]), wrap_x=True)
    latb = lathe(band, m, [(4.8, 8.2), (4.86, 10.6)], bandrect, nu=72, nv=4, shape=lambda th, t: superellipse(th, 1, 1, 6.0), smooth=False)
    # hebilla de oro con gema
    hb = m.atlas.alloc(128, 128)
    def paint_hb(W, H):
        a = metal(W, H, hexc('#fff0a0'), hexc('#a0661a'), seed=3, bands=1.2, brushed=False)
        mask = np.zeros((H, W), np.float32)
        mask[6:H - 6, 14:W - 14] = 1; mask[30:H - 30, 38:W - 38] = 0
        a[..., 3] = mask
        return a
    m.atlas.paste(hb, paint_hb(128, 128))
    pos, nn, uu = latb.at(0.0, 9.4, 0.0)
    decal(gold, pos, nn, uu, 3.0, 2.6, hb, off=0.1)
    g = m.atlas.alloc(64, 64); m.atlas.paste(g, gem(64, 64, hexc('#ff2a5a'), facets=8))
    decal(gold, pos, nn, uu, 1.2, 1.2, g, off=0.13)
    st = m.atlas.alloc(64, 64); m.atlas.paste(st, star_sprite(64))
    decal(gl, pos, nn, uu, 2.2, 2.2, st, off=0.2)
    return m, None

# =============================================================================== GATO
def tube_ring_path(a, n, y0, y1, z=0.0, gap=0.0):
    return None

def fur_texture(W, H, base, tip, seed, stroke=(1, 1, 1), dens=0.9, along='v'):
    """Pelaje: degradado de base a punta + trazos finos a lo largo de v."""
    a = vgrad(W, H, base, tip, gamma=1.2)
    n = fbm(W, H, scale=18, octaves=4, seed=seed)
    a = mul(a, 0.75 + 0.5 * n)
    r = np.random.RandomState(seed)
    cv = Canvas(W, H, a)
    segs = []
    for i in range(int(W * H / 260 * dens)):
        x = r.rand() * W; y = r.rand() * H; L = 6 + r.rand() * 14
        segs.append((x, y, L, r.rand()))
    def fn(d, S):
        for x, y, L, k in segs:
            col = tuple(int(255 * c * (0.75 + 0.25 * k)) for c in stroke) + (int(60 + 80 * k),)
            d.line([(x * S, y * S), ((x + (k - 0.5) * 3) * S, (y + L) * S)], fill=col, width=S)
    cv.draw(fn)
    return cv.arr

@hat
def hat_gato():
    m = Model(SIZE)
    fur = m.part('pelo'); inn = m.part('interior'); bandp = m.part('diadema'); bell = m.part('detalles')
    gl = m.part('brillos', F_EMIT | F_PULSE)
    BASE, TIP = hexc('#15141c'), hexc('#2b2a38')
    fr = m.atlas.alloc(300, 300); m.atlas.paste(fr, fur_texture(300, 300, TIP, BASE, 5, stroke=(0.55, 0.55, 0.75)), wrap_x=True)
    pr = m.atlas.alloc(200, 200)
    def paint_pink(W, H):
        a = vgrad(W, H, hexc('#ffc2d9'), hexc('#ff6fa0'), gamma=0.9)
        n = fbm(W, H, scale=10, octaves=3, seed=6)
        a = mul(a, 0.85 + 0.3 * n)
        # pelusa blanca en el borde exterior (u 0 y 1) y base
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        a = tint_add(a, (1, 1, 1), np.clip((yy / H - 0.7) * 3, 0, 1) * 0.5)
        return a
    m.atlas.paste(pr, paint_pink(200, 200), wrap_x=True)
    for sx in (-1, 1):
        cx = sx * 2.85
        def ear(b0=0.0, b1=1.0, sc=1.0, dz=0.0, dx=0.0):
            def fn(a, b):
                th = a * 2 * math.pi
                bb = lerp(b0, b1, b)
                s = (1 - bb) ** 0.9 * sc
                x = cx + dx + (1.55 * math.cos(th)) * s + sx * 0.9 * bb ** 1.6
                z = 0.2 + dz + (1.0 * math.sin(th)) * s - 0.35 * bb ** 1.4
                y = 7.55 + 4.3 * bb
                return (x, y, z)
            return fn
        surface(fur, ear(), 28, 14, fr, SIZE, closed_u=True, outward=(cx, 9.0, 0.2))
        surface(inn, ear(0.08, 0.80, 0.66, 0.30, -sx * 0.05), 28, 12, pr, SIZE, closed_u=True, outward=(cx, 9.0, 0.5))
    # diadema
    hr = m.atlas.alloc(512, 64)
    def paint_hb(W, H):
        a = vgrad(W, H, hexc('#33324a'), hexc('#0f0e16'))
        a[:, :, :3] *= (0.85 + 0.3 * fbm(W, H, scale=30, octaves=2, seed=3))[..., None]
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        a = tint_add(a, (0.6, 0.6, 0.9), np.exp(-((yy / H - 0.28) ** 2) / 0.01) * 0.55)
        return a
    m.atlas.paste(hr, paint_hb(512, 64), wrap_x=True)
    path = [(-4.55, 5.2, 0.0), (-4.55, 7.0, 0.0), (-3.2, 8.35, 0.0), (0, 8.8, 0.0), (3.2, 8.35, 0.0), (4.55, 7.0, 0.0), (4.55, 5.2, 0.0)]
    tube(bandp, m, path, lambda b: 0.5, hr, nu=14, nv=40, squash=1.4)
    # cascabel dorado con brillo en una oreja
    gd = m.atlas.alloc(128, 128); m.atlas.paste(gd, metal(128, 128, hexc('#fff2a8'), hexc('#a8681c'), seed=4, bands=1.5), wrap_x=True)
    ellipsoid(bell, m, (-4.7, 5.0, 1.9), (0.8, 0.8, 0.8), gd, nu=18, nv=10)
    st = m.atlas.alloc(64, 64); m.atlas.paste(st, star_sprite(64, (1, 0.8, 0.9)))
    decal(gl, (-4.75, 5.4, 2.6), (-0.3, 0.1, 1), (0, 1, 0), 1.6, 1.6, st, off=0.1)
    return m, None

# =============================================================================== CUERNOS
@hat
def hat_cuernos():
    m = Model(SIZE)
    hr = m.part('cuerno'); base = m.part('base'); gl = m.part('grietas', F_EMIT | F_PULSE)
    TW, THh = 360, 600
    horn = m.atlas.alloc(TW, THh)
    def paint_horn(W, H):
        t = np.linspace(0, 1, H)[:, None, None]
        c0, c1, c2, c3 = hexc('#14090c'), hexc('#6a0d1a'), hexc('#d8321c'), hexc('#ffb454')
        c = np.where(t < 0.35, c0 + (c1 - c0) * (t / 0.35), np.where(t < 0.75, c1 + (c2 - c1) * ((t - 0.35) / 0.4), c2 + (c3 - c2) * ((t - 0.75) / 0.25)))
        a = np.ones((H, W, 4), np.float32); a[..., :3] = np.repeat(c, W, axis=1)
        n = fbm(W, H, scale=8, octaves=5, seed=51)
        a = mul(a, 0.55 + 0.9 * n)
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        # anillos de crecimiento
        ring = 0.5 + 0.5 * np.cos(yy / H * 2 * math.pi * 11)
        a = mul(a, 0.72 + 0.28 * ring)
        # brillo especular a lo largo del cuerno
        a = tint_add(a, (1, 0.8, 0.7), np.exp(-((((xx / W) % 1.0) - 0.17) ** 2) / 0.0025) * 0.38)
        return a
    m.atlas.paste(horn, paint_horn(TW, THh), wrap_x=True)
    # grietas de lava (emisivas)
    lv = m.atlas.alloc(TW, THh)
    def paint_lava(W, H):
        r = np.random.RandomState(9)
        cv = Canvas(W, H)
        def fn(d, S):
            for i in range(26):
                x = r.rand() * W; y = r.rand() * H * 0.85 + H * 0.1
                pts = [(x, y)]
                for k in range(r.randint(3, 7)):
                    x += (r.rand() - 0.5) * 36; y += 10 + r.rand() * 34
                    pts.append((x, y))
                d.line([(px * S, py * S) for px, py in pts], fill=(255, 150, 40, 255), width=2 * S, joint='curve')
                d.line([(px * S, py * S) for px, py in pts], fill=(255, 235, 150, 255), width=S)
        cv.draw(fn, blur=0)
        cv.add(np.dstack([cv.arr[..., :3], cv.arr[..., 3] * 0]))
        g = Canvas(W, H, cv.arr.copy())
        glow = from_img(to_img(cv.arr).filter(ImageFilter.GaussianBlur(2.5)))
        out = over(glow, cv.arr)
        out[..., 3] = np.clip(out[..., 3], 0, 1)
        return out
    m.atlas.paste(lv, paint_lava(TW, THh), wrap_x=True)
    for sx in (-1, 1):
        path = [(sx * 3.1, 7.4, 0.2), (sx * 4.5, 9.4, 0.4), (sx * 6.1, 12.2, 0.9), (sx * 7.0, 15.4, 1.8), (sx * 6.7, 18.4, 3.0)]
        def rad(b):
            return 0.08 + 1.5 * (1 - b) ** 0.85 * (1 + 0.05 * math.cos(b * math.pi * 22))
        tube(hr, m, path, rad, horn, nu=18, nv=46)
        def rad2(b):
            return rad(b) + 0.045
        tube(gl, m, path, rad2, lv, nu=18, nv=46)
        # cuernito pequeno delantero
        p2 = [(sx * 1.6, 7.8, 2.6), (sx * 2.2, 9.2, 3.6), (sx * 2.5, 11.2, 4.4)]
        tube(hr, m, p2, lambda b: 0.06 + 0.78 * (1 - b) ** 0.9, horn, nu=14, nv=16)
        ellipsoid(base, m, (sx * 3.0, 7.9, 0.2), (1.9, 0.9, 1.9), horn, nu=20, nv=8, lat0=0.0)
    return m, None

# =============================================================================== AURICULARES
@hat
def hat_auriculares():
    m = Model(SIZE)
    shell = m.part('cascos'); padp = m.part('almohadillas'); bandp = m.part('arco'); mic = m.part('mic')
    rgb = m.part('rgb', F_EMIT | F_PULSE)
    # texturas
    gr = m.atlas.alloc(400, 220)
    def paint_shell(W, H):
        a = vgrad(W, H, hexc('#3b3d52'), hexc('#14151f'))
        n = fbm(W, H, scale=20, octaves=3, seed=3)
        a = mul(a, 0.8 + 0.4 * n)
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        a = mul(a, 0.9 + 0.1 * np.sin(xx * 2 * math.pi / 3.0)[...])
        a = tint_add(a, (0.7, 0.75, 1.0), np.exp(-((yy / H - 0.28) ** 2) / 0.006) * 0.35)
        return a
    m.atlas.paste(gr, paint_shell(400, 220), wrap_x=True)
    pd = m.atlas.alloc(300, 120)
    def paint_pad(W, H):
        a = fabric(W, H, hexc('#16161c'), seed=8, period=2.5, amt=0.12, nap=0.1)
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        a = tint_add(a, (0.6, 0.6, 0.7), np.exp(-((yy / H - 0.3) ** 2) / 0.01) * 0.28)
        return a
    m.atlas.paste(pd, paint_pad(300, 120), wrap_x=True)
    rr = m.atlas.alloc(512, 64)
    def paint_rgb(W, H):
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        h = (xx / W) % 1.0
        cols = np.zeros((H, W, 3), np.float32)
        # arcoiris cyan -> magenta -> amarillo
        cols[..., 0] = 0.5 + 0.5 * np.sin(h * 2 * math.pi + 0.0)
        cols[..., 1] = 0.5 + 0.5 * np.sin(h * 2 * math.pi + 2.1)
        cols[..., 2] = 0.5 + 0.5 * np.sin(h * 2 * math.pi + 4.2)
        cols = np.clip(cols * 0.8 + 0.25, 0, 1)
        core = np.exp(-((yy / H - 0.5) ** 2) / 0.05)
        a = np.ones((H, W, 4), np.float32); a[..., :3] = np.clip(cols * 0.8 + core[..., None] * 0.45, 0, 1)
        a[..., 3] = 0.35 + 0.65 * core
        return a
    m.atlas.paste(rr, paint_rgb(512, 64), wrap_x=True)
    # arco: tubo plano sobre la cabeza
    pts = []
    for k in range(0, 11):
        phi = math.pi * (k / 10.0)
        pts.append((-5.15 * math.cos(phi), 3.8 + 5.4 * math.sin(phi) * 0.93, 0.0))
    tube(bandp, m, pts, lambda b: 0.55, gr, nu=14, nv=40, squash=2.2)
    pts2 = []
    for k in range(0, 11):
        phi = math.pi * (k / 10.0)
        pts2.append((-4.55 * math.cos(phi), 3.8 + 4.75 * math.sin(phi) * 0.93, 0.0))
    # almohadilla interior (arco de abajo)
    tube(padp, m, pts2, lambda b: 0.42, pd, nu=12, nv=40, squash=2.6)
    # cascos
    for sx in (-1, 1):
        cx = sx * 5.15
        i0 = len(shell.quads)
        cup_prof = [(0.05, 0.9), (2.0, 0.9), (2.5, 1.2), (2.65, 2.0), (2.3, 2.9), (1.6, 3.25), (0.05, 3.3)]
        cr = latherect(m, cup_prof, None, 34, maxw=360)
        m.atlas.paste(cr, paint_shell(cr[2], cr[3]), wrap_x=True)
        lathe(shell, m, cup_prof, cr, nu=40, nv=22, smooth=True)
        xform(shell, i0, rotmat(0, 0, -sx * math.pi / 2), (cx - sx * 0.2, 3.6, 0.0))
        # almohadilla toroidal del lado de la cabeza
        i1 = len(padp.quads)
        pad_prof = [(1.0, 0.9), (1.9, 0.95), (2.4, 0.7), (2.45, 0.2), (2.05, -0.15), (1.1, -0.2)]
        pr_ = latherect(m, pad_prof, None, 30, maxw=300)
        m.atlas.paste(pr_, paint_pad(pr_[2], pr_[3]), wrap_x=True)
        lathe(padp, m, pad_prof, pr_, nu=36, nv=14, smooth=True, flip=True)
        xform(padp, i1, rotmat(0, 0, -sx * math.pi / 2), (cx - sx * 0.2, 3.6, 0.0))
        # anillo rgb en la cara externa
        i2 = len(rgb.quads)
        ring_prof = [(1.25, 3.33), (1.5, 3.36), (1.8, 3.36), (2.05, 3.33)]
        lathe(rgb, m, ring_prof, rr, nu=48, nv=3, smooth=False, flip=False)
        xform(rgb, i2, rotmat(0, 0, -sx * math.pi / 2), (cx - sx * 0.2, 3.6, 0.0))
    # logo A al centro de cada casco (emisivo)
    lg = m.atlas.alloc(128, 128)
    cv = Canvas(128, 128)
    cv.glow(64, 64, 62, (0.6, 0.45, 1.0), 0.5, 2.0)
    def fn(d, S): pil_text_A(d, S, 64, 64, 70, (255, 255, 255, 255), 12)
    cv.draw(fn)
    m.atlas.paste(lg, cv.arr)
    for sx in (-1, 1):
        decal(rgb, (sx * 5.15 + sx * 3.1, 3.6, 0.0), (sx, 0, 0), (0, 1, 0), 2.4, 2.4, lg, off=0.0, uv=((1, 1), (0, 1), (0, 0), (1, 0)) if sx > 0 else ((0, 1), (1, 1), (1, 0), (0, 0)))
    # microfono
    mp = [(-5.7, 2.2, 0.8), (-5.4, 1.1, 2.8), (-4.2, 0.6, 5.0), (-2.7, 1.0, 5.9)]
    tube(mic, m, mp, lambda b: 0.2, gr, nu=10, nv=24)
    foam = m.atlas.alloc(128, 128); m.atlas.paste(foam, fabric(128, 128, hexc('#18181e'), seed=2, period=2, amt=0.2, nap=0.2), wrap_x=True)
    ellipsoid(mic, m, (-2.3, 1.05, 6.0), (0.62, 0.62, 1.0), foam, nu=16, nv=10)
    return m, None

# =============================================================================== CONEJO
@hat
def hat_conejo():
    m = Model(SIZE)
    fur = m.part('pelo'); inn = m.part('interior'); bandp = m.part('diadema'); bow = m.part('lazo')
    gl = m.part('brillos', F_EMIT | F_PULSE)
    fr = m.atlas.alloc(300, 520)
    def paint_fur(W, H):
        a = vgrad(W, H, hexc('#fbf8ff'), hexc('#e4def2'), gamma=1.1)
        n = fbm(W, H, scale=22, octaves=4, seed=61)
        a = mul(a, 0.86 + 0.28 * n)
        r = np.random.RandomState(61)
        cv = Canvas(W, H, a)
        segs = [(r.rand() * W, r.rand() * H, 5 + r.rand() * 12, r.rand()) for _ in range(int(W * H / 140))]
        def fn(d, S):
            for x, y, L, k in segs:
                v = 205 + int(40 * k)
                d.line([(x * S, y * S), ((x + (k - 0.5) * 3) * S, (y + L) * S)], fill=(v, v - 6, v + 8, 70), width=S)
        cv.draw(fn)
        return cv.arr
    m.atlas.paste(fr, paint_fur(300, 520), wrap_x=True)
    pk = m.atlas.alloc(240, 440)
    def paint_pk(W, H):
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        a = np.ones((H, W, 4), np.float32)
        t = (yy / H)[..., None]
        a[..., :3] = hexc('#ff9bc0') * (1 - t * 0.5) + hexc('#ffd6e6') * (t * 0.5)
        n = fbm(W, H, scale=14, octaves=4, seed=62)
        a = mul(a, 0.88 + 0.24 * n)
        edge = np.clip((np.abs((xx / W) % 1.0 - 0.5) * 2 - 0.45) * 2.0, 0, 1)
        a = tint_add(a, (1, 1, 1), edge * 0.3)
        return a
    m.atlas.paste(pk, paint_pk(240, 440), wrap_x=True)
    for sx in (-1, 1):
        path = [(sx * 1.9, 7.2, -0.3), (sx * 2.2, 10.5, -0.4), (sx * 2.9, 14.5, -0.9), (sx * 3.7, 18.2, -2.1), (sx * 4.2, 20.8, -3.6)]
        def rad(b):
            return 0.14 + 1.5 * max(0.0, math.sin(math.pi * (0.1 + 0.9 * b))) ** 0.78
        tube(fur, m, path, rad, fr, nu=22, nv=40, squash=0.42)
        p2 = [(p[0] * 1.0, p[1] + 0.15, p[2] + 0.34) for p in path[:-1]]
        p2.append((path[-1][0], path[-1][1] - 0.7, path[-1][2] + 0.3))
        tube(inn, m, p2[:-1] + [(path[-1][0] * 0.99, path[-1][1] - 1.4, path[-1][2] + 0.34)], lambda b: 0.1 + 0.88 * max(0.0, math.sin(math.pi * (0.14 + 0.82 * b))) ** 0.8, pk, nu=18, nv=34, squash=0.42)
    # diadema blanca esponjosa
    hr = m.atlas.alloc(512, 64); m.atlas.paste(hr, mul(paint_fur(512, 64), 0.98), wrap_x=True)
    p = [(-4.55, 5.2, 0.0), (-4.55, 7.0, 0.0), (-3.2, 8.35, 0.0), (0, 8.8, 0.0), (3.2, 8.35, 0.0), (4.55, 7.0, 0.0), (4.55, 5.2, 0.0)]
    tube(bandp, m, p, lambda b: 0.55, hr, nu=14, nv=40, squash=1.4)
    # lazo rosa
    bw = m.atlas.alloc(256, 128)
    def paint_bow(W, H):
        a = fabric(W, H, hexc('#ff5c9a'), seed=4, period=3, amt=0.2, nap=0.05)
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        a = tint_add(a, (1, 0.8, 0.9), np.exp(-((yy / H - 0.3) ** 2) / 0.02) * 0.35)
        return a
    m.atlas.paste(bw, paint_bow(256, 128), wrap_x=True)
    for sx in (-1, 1):
        ellipsoid(bow, m, (sx * 1.7 + 3.4 * 0 + 2.0 * sx, 8.7, 3.2), (1.6, 0.9, 0.9), bw, nu=18, nv=10)
    ellipsoid(bow, m, (0.0, 8.7, 3.2), (0.7, 0.7, 0.7), bw, nu=14, nv=8)
    # hmm: lazo centrado al frente
    st = m.atlas.alloc(64, 64); m.atlas.paste(st, star_sprite(64, (1, 0.8, 0.95)))
    for k, (x, y, z) in enumerate([(-3.4, 15.5, 0.4), (3.2, 12.0, 0.6), (-2.6, 11.0, 0.9), (4.4, 16.5, -0.3)]):
        decal(gl, (x, y, z), (0, 0, 1), (0, 1, 0), 1.3, 1.3, st, off=0.0)
    return m, None

# =============================================================================== HALO
@hat
def hat_halo():
    m = Model(SIZE)
    core = m.part('nucleo', F_EMIT | F_PULSE | F_SPIN)
    soft = m.part('resplandor', F_EMIT | F_SOFT | F_SPIN)
    stars = m.part('estrellas', F_EMIT | F_PULSE | F_SPIN)
    cr = m.atlas.alloc(960, 96)
    def paint_core(W, H):
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        v = yy / H
        center = np.exp(-((v - 0.5) ** 2) / 0.012)
        wide = np.exp(-((v - 0.5) ** 2) / 0.09)
        u = xx / W
        shimmer = 0.78 + 0.22 * np.sin(u * 2 * math.pi * 14 + 1.0) * np.sin(u * 2 * math.pi * 5)
        col = np.zeros((H, W, 3), np.float32)
        gold = np.asarray(hexc('#ffcf5a')); white = np.asarray(hexc('#fffbe8'))
        col[:] = gold * (1 - center[..., None]) + white * center[..., None]
        a = np.ones((H, W, 4), np.float32)
        a[..., :3] = np.clip(col * shimmer[..., None] * (0.7 + 0.5 * wide[..., None]), 0, 1)
        a[..., 3] = np.clip(0.25 + wide * 0.75 + center * 0.5, 0, 1)
        return a
    m.atlas.paste(cr, paint_core(960, 96), wrap_x=True)
    sr = m.atlas.alloc(960, 128)
    def paint_soft(W, H):
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        v = np.abs(yy / H - 0.5) * 2
        a = np.ones((H, W, 4), np.float32)
        a[..., :3] = hexc('#ffd878')
        a[..., 3] = np.clip(1 - v, 0, 1) ** 2.4 * 0.5
        return a
    m.atlas.paste(sr, paint_soft(960, 128), wrap_x=True)
    y0 = 13.2
    torus(core, m, (0, y0, 0), 5.35, 0.42, cr, nu=96, nv=14, tilt=(-0.07, 0.0))
    torus(soft, m, (0, y0, 0), 5.35, 1.25, sr, nu=96, nv=12, tilt=(-0.07, 0.0))
    torus(soft, m, (0, y0 + 0.0, 0), 5.35, 2.1, sr, nu=96, nv=12, tilt=(-0.07, 0.0))
    st = m.atlas.alloc(64, 64); m.atlas.paste(st, star_sprite(64, (1.0, 0.92, 0.55)))
    for k in range(9):
        th = k * 2 * math.pi / 9
        x, z = 5.35 * math.sin(th), 5.35 * math.cos(th)
        for rot in (0, 1):
            nrm = (math.sin(th) if rot == 0 else 0, 0 if rot == 0 else 1, math.cos(th) if rot == 0 else 0)
        decal(stars, (x, y0 + 0.9 + 0.5 * math.sin(k * 1.7), z), (0, 0, 1), (0, 1, 0), 1.6 if k % 2 else 1.1, 1.6 if k % 2 else 1.1, st, off=0.0)
        decal(stars, (x, y0 + 0.9 + 0.5 * math.sin(k * 1.7), z), (1, 0, 0), (0, 1, 0), 1.6 if k % 2 else 1.1, 1.6 if k % 2 else 1.1, st, off=0.0)
    return m, None

# =============================================================================== PIRATA (tricornio)
def skull_sprite(W=192):
    cv = Canvas(W, W)
    def fn(d, S):
        bone = (240, 232, 210, 255); sh = (176, 166, 140, 255); k = (14, 12, 16, 255)
        # huesos cruzados detras
        for sgn in (-1, 1):
            x0, y0, x1, y1 = W * 0.12, W * (0.78 if sgn > 0 else 0.30), W * 0.88, W * (0.30 if sgn > 0 else 0.78)
            d.line([(x0 * S, y0 * S), (x1 * S, y1 * S)], fill=sh, width=int(W * 0.075 * S))
            d.line([(x0 * S, y0 * S), (x1 * S, y1 * S)], fill=bone, width=int(W * 0.05 * S))
            for (ex, ey) in ((x0, y0), (x1, y1)):
                for dx in (-0.035, 0.035):
                    d.ellipse([(ex + (dx - 0.05) * W) * S, (ey - 0.05 * W) * S, (ex + (dx + 0.05) * W) * S, (ey + 0.05 * W) * S], fill=bone, outline=sh)
        # craneo
        d.ellipse([W * 0.26 * S, W * 0.12 * S, W * 0.74 * S, W * 0.62 * S], fill=bone, outline=sh, width=2 * S)
        d.rounded_rectangle([W * 0.34 * S, W * 0.48 * S, W * 0.66 * S, W * 0.72 * S], radius=int(W * 0.05 * S), fill=bone, outline=sh, width=2 * S)
        d.ellipse([W * 0.31 * S, W * 0.30 * S, W * 0.47 * S, W * 0.48 * S], fill=k)
        d.ellipse([W * 0.53 * S, W * 0.30 * S, W * 0.69 * S, W * 0.48 * S], fill=k)
        d.polygon([(W * 0.5 * S, W * 0.47 * S), (W * 0.455 * S, W * 0.58 * S), (W * 0.545 * S, W * 0.58 * S)], fill=k)
        for i in range(5):
            x = W * (0.375 + i * 0.0625)
            d.line([(x * S, W * 0.63 * S), (x * S, W * 0.72 * S)], fill=sh, width=2 * S)
        d.line([(W * 0.35 * S, W * 0.63 * S), (W * 0.65 * S, W * 0.63 * S)], fill=sh, width=2 * S)
    cv.draw(fn)
    return cv.arr

@hat
def hat_pirata():
    m = Model(2048)
    crown = m.part('copa'); brim = m.part('ala'); band = m.part('cinta'); gold = m.part('oro'); plume = m.part('pluma')
    FELT = hexc('#17171d'); GOLD_L, GOLD_D = hexc('#ffe9a0'), hexc('#a3651c')
    shape = squ(6.0, 3.0, 0.78, 0.97)
    prof = [(4.5, 7.9), (4.62, 9.3), (4.6, 11.0), (4.3, 12.2), (3.4, 12.9), (1.8, 13.15), (0.05, 13.2)]
    rect = latherect(m, prof, shape, 34)
    def paint_crown(W, H):
        a = solid(W, H, FELT)
        n = fbm(W, H, scale=16, octaves=4, seed=71)
        a = mul(a, 0.6 + 0.8 * n)
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        a = mul(a, 0.94 + 0.06 * np.sin(xx * 2 * math.pi / 3.0))
        sheen = np.exp(-((((xx / W) % 1.0) - 0.18) ** 2) / 0.004) * 0.35
        a = tint_add(a, (0.55, 0.55, 0.75), sheen)
        return vignette_edges(a, 0.12, 3)
    m.atlas.paste(rect, paint_crown(rect[2], rect[3]), wrap_x=True)
    lat = lathe(crown, m, prof, rect, nu=72, nv=22, shape=shape)
    # ala de tres picos (tricornio): esquinas en 60, 180 y 300 grados; los lados se pliegan hacia arriba
    br = m.atlas.alloc(900, 130); bb = m.atlas.alloc(900, 130); be = m.atlas.alloc(900, 10)
    def paint_brim(W, H, dark=1.0):
        a = solid(W, H, FELT)
        n = fbm(W, H, scale=24, octaves=4, seed=72)
        a = mul(a, (0.62 + 0.8 * n) * dark)
        cv = Canvas(W, H, a)
        def fn(d, S):
            if dark >= 1.0:
                d.rectangle([0, (H - 17) * S, W * S, (H - 5) * S], fill=(212, 164, 60, 255))     # galon dorado
                d.rectangle([0, (H - 13) * S, W * S, (H - 11) * S], fill=(255, 232, 150, 255))
                for x in range(0, W, 14):
                    d.line([(x * S, (H - 17) * S), ((x + 7) * S, (H - 5) * S)], fill=(150, 100, 24, 255), width=2 * S)
        cv.draw(fn)
        return cv.arr
    m.atlas.paste(br, paint_brim(900, 130), wrap_x=True)
    m.atlas.paste(bb, paint_brim(900, 130, 0.55), wrap_x=True)
    m.atlas.paste(be, solid(900, 10, hexc('#d4a43c')), wrap_x=True)
    rin = lambda th: 4.55 * superellipse(th, 1, 1, 6.0)
    th0 = math.radians(60)
    def bfn(a, b):
        th = a * 2 * math.pi
        fold = 0.5 - 0.5 * math.cos(3 * (th - th0))          # 0 en las esquinas, 1 en el centro de cada lado
        R = 9.3 - 2.3 * fold
        r = lerp(rin(th), R, b)
        y = 8.0 + 3.1 * fold * b ** 1.5 - 0.2 * b * (1 - fold)
        return (r * math.sin(th), y, r * math.cos(th))
    slab(brim, m, bfn, 96, 10, 0.3, br, bb, be, sides=('b1',))
    # cinta
    bandrect = latherect(m, [(4.7, 8.2), (4.76, 9.6)], shape, 40)
    def paint_band(W, H):
        a = fabric(W, H, hexc('#7a1020'), seed=9, period=3, amt=0.2, nap=0.06)
        a[0:5, :, :3] = hexc('#d4a43c'); a[H - 5:H, :, :3] = hexc('#d4a43c')
        return vignette_edges(a, 0.2, 2.5)
    m.atlas.paste(bandrect, paint_band(bandrect[2], bandrect[3]), wrap_x=True)
    lathe(band, m, [(4.66, 8.2), (4.70, 9.6)], bandrect, nu=72, nv=4, shape=lambda th, t: superellipse(th, 1, 1, 6.0) * 1.0, smooth=False)
    # calavera con huesos en el pliegue frontal
    sk = m.atlas.alloc(192, 192); m.atlas.paste(sk, skull_sprite(192))
    decal(gold, (0, 9.55, 6.05), (0, 0.62, 0.78), (0, 0.78, -0.62), 3.7, 3.7, sk, off=0.12)
    # botones dorados en las dos esquinas traseras y la delantera
    gd = m.atlas.alloc(96, 96); m.atlas.paste(gd, disc(96, 96, GOLD_L, GOLD_D))
    for ang in (60, 180, 300):
        t = math.radians(ang)
        r = 5.7 * superellipse(t, 1, 1, 6.0) / 1.2
        ellipsoid(gold, m, (r * math.sin(t), 8.4, r * math.cos(t)), (0.38, 0.38, 0.38), gd, nu=12, nv=8)
    # pluma roja que cae por el lado derecho de la copa
    fr = m.atlas.alloc(256, 384)
    def paint_feather(W, H):
        a = vgrad(W, H, hexc('#ff4a5a'), hexc('#8d0f26'), gamma=1.0)
        a = mul(a, 0.8 + 0.4 * fbm(W, H, scale=14, octaves=3, seed=75))
        cv = Canvas(W, H, a)
        def fn(d, S):
            d.line([(W / 2 * S, 0), (W / 2 * S, H * S)], fill=(255, 230, 220, 255), width=3 * S)
            for y in range(6, H, 9):
                d.line([(W / 2 * S, y * S), (W * 0.02 * S, (y - 18) * S)], fill=(255, 200, 200, 120), width=S)
                d.line([(W / 2 * S, y * S), (W * 0.98 * S, (y - 18) * S)], fill=(255, 200, 200, 120), width=S)
        cv.draw(fn)
        return cv.arr
    m.atlas.paste(fr, paint_feather(256, 384), wrap_x=True)
    pp = [(-3.4, 10.4, -2.4), (-5.6, 12.2, -2.2), (-7.2, 11.0, -1.0), (-7.6, 8.4, 0.6), (-6.6, 6.4, 2.0)]
    tube(plume, m, pp, lambda b: 0.12 + 1.0 * max(0.0, math.sin(math.pi * (0.06 + 0.94 * b))) ** 0.7, fr, nu=16, nv=34, squash=0.28)
    return m, None

# =============================================================================== VIKINGO
@hat
def hat_vikingo():
    m = Model(2048)
    dome = m.part('casco'); brow = m.part('frente'); horn = m.part('cuernos'); nasal = m.part('nasal'); riv = m.part('remaches'); strap = m.part('correas')
    STEEL_L, STEEL_D = hexc('#c4cad6'), hexc('#4a505e')
    BR_L, BR_D = hexc('#e9b36a'), hexc('#7a4a16')
    shape = squ(5.0, 1.7, 0.15, 0.75)
    prof = [(4.5, 7.6), (4.72, 9.0), (4.62, 10.8), (4.0, 12.3), (2.6, 13.4), (1.0, 13.9), (0.05, 14.0)]
    rect = latherect(m, prof, shape, 40)
    def paint_dome(W, H):
        a = metal(W, H, STEEL_L, STEEL_D, seed=31, bands=1.8)
        n = fbm(W, H, scale=20, octaves=4, seed=32)
        a = mul(a, 0.78 + 0.45 * n)
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        a = tint_add(a, (0.9, 0.95, 1.0), np.exp(-((((xx / W) % 1.0) - 0.16) ** 2) / 0.003) * 0.4)
        # marcas de golpes/rayones
        r_ = np.random.RandomState(33)
        cv = Canvas(W, H, a)
        def fn(d, S):
            for i in range(60):
                x = r_.rand() * W; y = r_.rand() * H; L = 6 + r_.rand() * 22; an = r_.rand() * math.pi
                d.line([(x * S, y * S), ((x + math.cos(an) * L) * S, (y + math.sin(an) * L) * S)], fill=(40, 44, 54, 120), width=S)
        cv.draw(fn)
        return cv.arr
    m.atlas.paste(rect, paint_dome(rect[2], rect[3]), wrap_x=True)
    lat = lathe(dome, m, prof, rect, nu=72, nv=26, shape=shape)
    # banda frontal de bronce con grabado
    brect = latherect(m, [(4.78, 7.9), (4.86, 9.8)], shape, 46)
    def paint_brow(W, H):
        a = metal(W, H, BR_L, BR_D, seed=34, bands=1.4)
        cv = Canvas(W, H, a)
        def fn(d, S):
            d.rectangle([0, 0, W * S, 6 * S], fill=(110, 66, 16, 255)); d.rectangle([0, (H - 6) * S, W * S, H * S], fill=(110, 66, 16, 255))
            # nudo celta simple: zigzag de runas
            for x in range(0, W, 30):
                d.line([(x * S, (H * 0.30) * S), ((x + 15) * S, (H * 0.70) * S), ((x + 30) * S, (H * 0.30) * S)], fill=(110, 66, 16, 255), width=3 * S, joint='curve')
        cv.draw(fn)
        return cv.arr
    m.atlas.paste(brect, paint_brow(brect[2], brect[3]), wrap_x=True)
    lathe(brow, m, [(4.8, 7.9), (4.88, 9.8)], brect, nu=72, nv=4, shape=shape, smooth=False)
    # remaches
    rv = m.atlas.alloc(64, 64); m.atlas.paste(rv, disc(64, 64, hexc('#fff0c0'), hexc('#8a5a1e')))
    for k in range(14):
        t = k * 2 * math.pi / 14
        rr = 4.9 * superellipse(t, 1, 1, 6.0)
        ellipsoid(riv, m, (rr * math.sin(t), 8.85, rr * math.cos(t)), (0.26, 0.26, 0.26), rv, nu=10, nv=6)
    # nasal (protector de nariz)
    nrect = m.atlas.alloc(128, 256); m.atlas.paste(nrect, metal(128, 256, BR_L, BR_D, seed=36, bands=1.0))
    tube(nasal, m, [(0, 9.0, 4.9), (0, 7.6, 5.3), (0, 6.2, 5.2)], lambda b: 0.55 - 0.15 * b, nrect, nu=12, nv=14, squash=0.22)
    # cuernos de hueso con anillos y punta oscura
    hr = m.atlas.alloc(360, 600)
    def paint_horn(W, H):
        t = np.linspace(0, 1, H)[:, None, None]
        c0, c1, c2 = hexc('#f1e6c8'), hexc('#d8c493'), hexc('#4a3a28')
        c = np.where(t < 0.7, c0 + (c1 - c0) * (t / 0.7), c1 + (c2 - c1) * ((t - 0.7) / 0.3))
        a = np.ones((H, W, 4), np.float32); a[..., :3] = np.repeat(c, W, axis=1)
        a = mul(a, 0.7 + 0.6 * fbm(W, H, scale=9, octaves=5, seed=37))
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        a = mul(a, 0.78 + 0.22 * (0.5 + 0.5 * np.cos(yy / H * 2 * math.pi * 9)))
        a = tint_add(a, (1, 0.95, 0.85), np.exp(-((((xx / W) % 1.0) - 0.17) ** 2) / 0.0025) * 0.3)
        return a
    m.atlas.paste(hr, paint_horn(360, 600), wrap_x=True)
    for sx in (-1, 1):
        path = [(sx * 4.4, 9.4, 0.0), (sx * 6.2, 9.8, 0.2), (sx * 7.9, 11.4, 0.5), (sx * 8.5, 14.0, 1.1), (sx * 8.0, 17.0, 2.0)]
        tube(horn, m, path, lambda b: 0.10 + 1.15 * (1 - b) ** 0.8, hr, nu=18, nv=40)
        ellipsoid(riv, m, (sx * 4.5, 9.4, 0.0), (0.7, 0.7, 0.7), nrect, nu=12, nv=8)
    # correas de cuero colgando a cada lado
    lr = m.atlas.alloc(64, 256)
    def paint_leather(W, H):
        a = fabric(W, H, hexc('#5a3a22'), seed=40, period=2.5, amt=0.2, nap=0.15)
        a[:, 0:3, :3] *= 0.6; a[:, W - 3:W, :3] *= 0.6
        return a
    m.atlas.paste(lr, paint_leather(64, 256))
    for sx in (-1, 1):
        tube(strap, m, [(sx * 4.85, 7.8, 0.4), (sx * 4.9, 5.0, 0.5), (sx * 4.7, 2.6, 0.9)], lambda b: 0.5, lr, nu=8, nv=10, squash=0.2)
    return m, None

# =============================================================================== LAUREL
@hat
def hat_laurel():
    m = Model(1024)
    leaves = m.part('hojas'); twig = m.part('ramita'); berry = m.part('bayas'); rib = m.part('cinta')
    gl = m.part('brillos', F_EMIT | F_PULSE)
    GOLD_L, GOLD_D = hexc('#fff0a8'), hexc('#a8701c')
    lf = m.atlas.alloc(128, 256)
    def paint_leaf(W, H):
        a = metal(W, H, GOLD_L, GOLD_D, seed=51, bands=2.0, brushed=False)
        a = mul(a, 0.85 + 0.3 * fbm(W, H, scale=10, octaves=3, seed=52))
        cv = Canvas(W, H, a)
        def fn(d, S):
            d.line([(W / 2 * S, 0), (W / 2 * S, H * S)], fill=(140, 86, 18, 255), width=3 * S)
            for y in range(14, H - 10, 20):
                d.line([(W / 2 * S, (y + 14) * S), (W * 0.12 * S, (y - 6) * S)], fill=(160, 100, 24, 200), width=S)
                d.line([(W / 2 * S, (y + 14) * S), (W * 0.88 * S, (y - 6) * S)], fill=(160, 100, 24, 200), width=S)
        cv.draw(fn)
        return cv.arr
    m.atlas.paste(lf, paint_leaf(128, 256))
    tw = m.atlas.alloc(128, 64); m.atlas.paste(tw, metal(128, 64, GOLD_L, GOLD_D, seed=53, bands=1.5))
    bb = m.atlas.alloc(64, 64); m.atlas.paste(bb, disc(64, 64, hexc('#ff6a7a'), hexc('#8a1228')))
    y0 = 8.35
    def ring_pt(th):
        r = 4.78 * superellipse(th, 1, 1, 6.0)
        return np.array([r * math.sin(th), y0, r * math.cos(th)])
    N = 15
    for s in (-1, 1):
        # rama: del centro de la nuca (th=180) hacia la frente (th=~25)
        ths = [s * (math.pi - 0.0 - k * (math.pi - 0.42) / (N - 1)) for k in range(N)]
        # ramita
        tube(twig, m, [tuple(ring_pt(t) + np.array([0, 0.05 * math.sin(i), 0])) for i, t in enumerate(ths[::2])], lambda b: 0.16, tw, nu=8, nv=36)
        for i, t in enumerate(ths):
            p = ring_pt(t)
            q = ring_pt(t - s * 0.05)
            T = norm(q - p) * -1 if False else norm(ring_pt(t - s * 0.06) - p)   # hacia la frente
            Rd = norm(np.array([p[0], 0, p[2]]))
            for side in (-1, 1):
                size = 2.05 * (1.0 - 0.25 * i / N)
                Lv = norm(T * 0.80 + np.array([0, 1, 0]) * 0.42 * side + Rd * 0.28)
                Sv = norm(np.cross(Lv, Rd))
                Wd = size * 0.21
                def lfn(a, b, p=p, Lv=Lv, Sv=Sv, Rd=Rd, size=size, Wd=Wd):
                    w = Wd * max(0.0, math.sin(math.pi * (0.04 + 0.92 * b))) ** 0.8
                    c = p + Lv * (size * b) + Rd * (0.25 * b * b) - np.array([0, 0.35 * b * b * (1 if side > 0 else -1) * 0.3, 0])
                    return tuple(c + Sv * ((a - 0.5) * 2 * w) + Rd * (0.12 * ((a - 0.5) * 2) ** 2 * w * 2))
                surface(leaves, lfn, 4, 8, lf, m.atlas.size, outward=None, two_sided=True)
            if i % 5 == 4:
                ellipsoid(berry, m, tuple(p + Rd * 0.2 + np.array([0, 0.35, 0])), (0.32, 0.32, 0.32), bb, nu=10, nv=6)
    # lazo y cintas en la nuca
    rr = m.atlas.alloc(128, 256)
    def paint_rib(W, H):
        a = fabric(W, H, hexc('#c01832'), seed=60, period=3, amt=0.16, nap=0.05)
        a[:, 0:4, :3] = hexc('#ffd770'); a[:, W - 4:W, :3] = hexc('#ffd770')
        return a
    m.atlas.paste(rr, paint_rib(128, 256))
    for sx, ph in ((-1, 0.0), (1, 0.6)):
        tube(rib, m, [(sx * 0.3, 8.3, -4.9), (sx * 1.2, 6.2, -5.4 - ph), (sx * 2.3, 3.6, -5.2 - ph), (sx * 3.0, 1.2, -4.6 - ph)], lambda b: 0.55 + 0.1 * b, rr, nu=8, nv=20, squash=0.12)
    ellipsoid(rib, m, (0, 8.3, -4.95), (0.6, 0.55, 0.45), rr, nu=12, nv=8)
    st = m.atlas.alloc(64, 64); m.atlas.paste(st, star_sprite(64, (1.0, 0.92, 0.55)))
    for (x, y, z) in [(-4.2, 9.6, 2.4), (4.4, 9.4, 1.6), (0.3, 9.7, 4.9), (-3.6, 9.5, -3.0)]:
        decal(gl, (x, y, z), (0, 0, 1), (0, 1, 0), 1.3, 1.3, st, off=0.0)
    return m, None

def build(name):
    m, _ = HATS[name]()
    return m

if __name__ == '__main__':
    args = sys.argv[1:]
    assets = args[0]; ids = args[1:] or list(HATS)
    for d in ('models/hats', 'textures/cosmetics/hats', 'textures/cosmetics/icons'):
        os.makedirs(os.path.join(assets, d), exist_ok=True)
    for n in ids:
        m = build(n)
        m.save(os.path.join(assets, 'models/hats/%s.atm' % n))
        m.atlas.image().save(os.path.join(assets, 'textures/cosmetics/hats/%s.png' % n), optimize=True)
        ic = render(m, m.atlas.arr, yaw=28, pitch=16, size=128, ss=3, head=False)
        ic.save(os.path.join(assets, 'textures/cosmetics/icons/hat_%s.png' % n), optimize=True)
        print(n, sum(len(p.quads) for p in m.parts), 'quads')
