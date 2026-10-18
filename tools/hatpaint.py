"""Pintura procedural para los sombreros (todo en espacio UV: arrays float RGBA de HxW)."""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from hatlib import *

def solid(W, H, c, a=1.0):
    arr = np.zeros((H, W, 4), np.float32)
    arr[..., :3] = c; arr[..., 3] = a
    return arr

def vgrad(W, H, top, bot, gamma=1.0):
    t = (np.linspace(0, 1, H) ** gamma)[:, None, None]
    c = np.asarray(top, np.float32) * (1 - t) + np.asarray(bot, np.float32) * t
    arr = np.ones((H, W, 4), np.float32)
    arr[..., :3] = np.repeat(c, W, axis=1)
    return arr

def mul(arr, k):
    out = arr.copy()
    out[..., :3] = np.clip(out[..., :3] * (k[..., None] if np.ndim(k) == 2 else k), 0, 1)
    return out

def tint_add(arr, c, k):
    out = arr.copy()
    out[..., :3] = np.clip(out[..., :3] + np.asarray(c, np.float32) * (k[..., None] if np.ndim(k) == 2 else k), 0, 1)
    return out

def fabric(W, H, c, seed=0, period=3.0, amt=0.10, nap=0.10):
    """Tela con trama fina y manchas suaves."""
    base = solid(W, H, c)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    weave = 0.5 + 0.5 * np.sin(xx * 2 * math.pi / period) * np.sin(yy * 2 * math.pi / period)
    n = fbm(W, H, scale=max(4, W / 24), octaves=4, seed=seed)
    fine = np.random.RandomState(seed + 5).rand(H, W).astype(np.float32)
    k = 1 + (weave - 0.5) * amt + (n - 0.5) * 0.28 + (fine - 0.5) * nap
    return mul(base, k)

def metal(W, H, light, dark, seed=0, bands=2.2, brushed=True, vertical=True):
    """Metal pulido (oro/plata): bandas de reflejo + rayado + destellos."""
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    n = fbm(W, H, scale=6, octaves=3, seed=seed)
    t = (yy / H) if vertical else (xx / W)
    u = xx / W
    inten = 0.5 + 0.5 * np.sin(t * math.pi * 2 * bands + n * 3.0 + u * 2.5)
    inten = inten ** 1.4
    if brushed:
        br = fbm(W, H, scale=max(60, W // 2), octaves=1, seed=seed + 11, tile_x=True)
        br2 = np.random.RandomState(seed + 3).rand(1, W).astype(np.float32)
        inten = np.clip(inten * 0.9 + (br - 0.5) * 0.18 + (br2 - 0.5) * 0.06, 0, 1)
    c = np.asarray(dark, np.float32) * (1 - inten[..., None]) + np.asarray(light, np.float32) * inten[..., None]
    spec = np.clip((inten - 0.82) / 0.18, 0, 1) ** 2
    c = np.clip(c + spec[..., None] * 0.35, 0, 1)
    arr = np.ones((H, W, 4), np.float32); arr[..., :3] = c
    return arr

def stitch_v(arr, u_frac, color=(1, 1, 1), dash=5, gap=4, width=1, a=0.8):
    H, W = arr.shape[:2]
    x = int(u_frac * W) % W
    for y in range(0, H, dash + gap):
        for w in range(width):
            arr[y:y + dash, (x + w) % W, :3] = color
            arr[y:y + dash, (x + w) % W, 3] = 1

def vignette_edges(arr, amt=0.35, power=2.2):
    """Oscurece suave el borde de abajo y de arriba (ambient occlusion pintado)."""
    H = arr.shape[0]
    t = np.linspace(-1, 1, H)
    k = 1 - amt * np.abs(t) ** power
    return mul(arr, k[:, None] * np.ones((1, arr.shape[1]), np.float32))

def sparkle(arr, n, seed, color=(1, 1, 1), size=1.5, minb=0.6):
    H, W = arr.shape[:2]
    r = np.random.RandomState(seed)
    cv = Canvas(W, H, arr)
    pts = [(r.rand() * W, r.rand() * H, size * (0.5 + r.rand()), minb + (1 - minb) * r.rand()) for _ in range(n)]
    def fn(d, S):
        for x, y, s, b in pts:
            col = tuple(int(255 * c) for c in color) + (int(255 * b),)
            d.ellipse([(x - s) * S, (y - s) * S, (x + s) * S, (y + s) * S], fill=col)
    cv.draw(fn)
    return cv.arr

def star4(d, S, x, y, r, col, thin=0.18):
    pts = []
    for i in range(8):
        a = i * math.pi / 4
        rr = r if i % 2 == 0 else r * thin
        pts.append(((x + math.cos(a) * rr) * S, (y + math.sin(a) * rr) * S))
    d.polygon(pts, fill=col)

def star5(d, S, x, y, r, col, rot=-math.pi / 2):
    pts = []
    for i in range(10):
        a = rot + i * math.pi / 5
        rr = r if i % 2 == 0 else r * 0.42
        pts.append(((x + math.cos(a) * rr) * S, (y + math.sin(a) * rr) * S))
    d.polygon(pts, fill=col)

def gem(W, H, color, dark=None, light=None, facets=8):
    """Gema tallada (vista de frente) para un decal: facetas radiales con brillo."""
    c = np.asarray(color, np.float32)
    dk = np.asarray(dark if dark is not None else c * 0.35, np.float32)
    lt = np.asarray(light if light is not None else np.clip(c * 0.5 + 0.55, 0, 1), np.float32)
    cv = Canvas(W, H)
    cx, cy = W / 2, H / 2
    def fn(d, S):
        R = min(W, H) / 2 - 2
        pts = [(cx + math.cos(i * 2 * math.pi / facets + math.pi / facets) * R, cy + math.sin(i * 2 * math.pi / facets + math.pi / facets) * R) for i in range(facets)]
        d.polygon([(x * S, y * S) for x, y in pts], fill=tuple(int(255 * v) for v in dk) + (255,))
        for i in range(facets):
            a, b = pts[i], pts[(i + 1) % facets]
            k = 0.5 + 0.5 * math.cos(i * 2 * math.pi / facets - 0.9)
            col = c * (0.55 + 0.75 * k) + (lt - c) * (0.5 * k * k)
            col = tuple(int(255 * min(1, v)) for v in col) + (255,)
            d.polygon([(cx * S, cy * S), (a[0] * S, a[1] * S), (b[0] * S, b[1] * S)], fill=col)
        r2 = R * 0.52
        pts2 = [(cx + math.cos(i * 2 * math.pi / facets + math.pi / facets) * r2, cy + math.sin(i * 2 * math.pi / facets + math.pi / facets) * r2) for i in range(facets)]
        for i in range(facets):
            a, b = pts2[i], pts2[(i + 1) % facets]
            k = 0.5 + 0.5 * math.cos(i * 2 * math.pi / facets + 1.7)
            col = c * (0.7 + 0.6 * k) + (lt - c) * (0.45 * k)
            col = tuple(int(255 * min(1, v)) for v in col) + (255,)
            d.polygon([(cx * S, cy * S), (a[0] * S, a[1] * S), (b[0] * S, b[1] * S)], fill=col)
        # brillo
        d.ellipse([(cx - R * 0.45) * S, (cy - R * 0.62) * S, (cx - R * 0.1) * S, (cy - R * 0.32) * S], fill=(255, 255, 255, 200))
        d.polygon([(x * S, y * S) for x, y in pts], outline=tuple(int(255 * v) for v in dk * 0.6) + (255,))
    cv.draw(fn)
    return cv.arr

def disc(W, H, c1, c2, rim=None):
    """Perla / boton: esfera pintada con luz arriba a la izquierda."""
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    cx, cy = W / 2, H / 2
    R = min(W, H) / 2 - 1
    dx, dy = (xx - cx) / R, (yy - cy) / R
    d2 = dx * dx + dy * dy
    z = np.sqrt(np.clip(1 - d2, 0, 1))
    lam = np.clip(-0.4 * dx - 0.5 * dy + 0.75 * z, 0, 1)
    c1, c2 = np.asarray(c1, np.float32), np.asarray(c2, np.float32)
    col = c2 + (c1 - c2) * lam[..., None] ** 0.9
    spec = np.clip(1 - np.sqrt((dx + 0.35) ** 2 + (dy + 0.4) ** 2) / 0.28, 0, 1) ** 2
    col = np.clip(col + spec[..., None] * 0.7, 0, 1)
    arr = np.ones((H, W, 4), np.float32); arr[..., :3] = col
    arr[..., 3] = np.clip((1 - np.sqrt(d2)) * R, 0, 1)
    return arr

def soft_glow(W, H, color, power=2.0, ring=False, rr=0.55):
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    dx, dy = (xx - W / 2) / (W / 2), (yy - H / 2) / (H / 2)
    d = np.sqrt(dx * dx + dy * dy)
    a = np.clip(1 - d, 0, 1) ** power
    arr = np.zeros((H, W, 4), np.float32)
    arr[..., :3] = color; arr[..., 3] = a
    return arr

def mk_surface(model, part, kind, W, H, painter, wrap=True, **kw):
    """Reserva una region del atlas, pinta con painter(W,H) y devuelve el rect."""
    r = model.atlas.alloc(W, H)
    model.atlas.paste(r, painter(W, H), wrap_x=wrap)
    return r
