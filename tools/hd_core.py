"""Utilidades comunes del generador de capas HD.
Lienzo de trabajo 320x512 (2x la cara final de 160x256). Las capas que antes eran de 40x64 conservan sus formulas en
'pixeles viejos' (OW x OH): xx, yy, X, Y, AX, AY valen lo mismo que antes pero son continuos (K = 8 px de trabajo por pixel viejo)."""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HW, HH = 320, 512      # lienzo de trabajo
FW, FH = 160, 256      # cara final de la capa
OW, OH = 40, 64        # unidades de los disenos originales
K = HW // OW           # 8
FR = 12
TAU = 2 * math.pi
SHP = (HH, HW)

_yi, _xi = np.mgrid[0:HH, 0:HW].astype(np.float32)
xx = (_xi + 0.5) / K - 0.5
yy = (_yi + 0.5) / K - 0.5
X = (xx + 0.5) / OW
Y = (yy + 0.5) / OH
AX, AY = X * 10.0, Y * 16.0
W, H = OW, OH          # las formulas viejas usan W,H = 40,64
del _yi, _xi


def hexc(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], np.float32)


def pal(stops, t):
    """stops [(pos, (r,g,b)|hex)], t array 0..1 -> RGB. Colores en 0..255 si son tuplas; el resultado va en 0..1."""
    t = np.clip(t, 0, 1)
    pos = [s[0] for s in stops]
    cols = [hexc(s[1]) if isinstance(s[1], int) else np.array(s[1], np.float32) for s in stops]
    out = np.zeros(np.shape(t) + (3,), np.float32)
    for c in range(3):
        out[..., c] = np.interp(t, pos, [col[c] / 255.0 for col in cols])
    return out


def vgrad(stops):
    """Degradado vertical (en Y) con colores hex; resultado 0..1."""
    return pal([(p, c) for p, c in stops], Y)


def mix(a, b, t):
    t = np.asarray(t, np.float32)
    if t.ndim == 2:
        t = t[..., None]
    return a * (1 - t) + np.asarray(b, np.float32) * t


def put(col, x, y, c, k=1.0, sig=0.5, hi=1.6):
    """Mancha suave en coordenadas viejas (x,y) con el color c (misma escala que col). Fuera de rango no hace nada."""
    cx, cy = (x + 0.5) * K - 0.5, (y + 0.5) * K - 0.5
    r = int(sig * K * 3.2) + 1
    x0, x1 = max(0, int(cx) - r), min(HW, int(cx) + r + 1)
    y0, y1 = max(0, int(cy) - r), min(HH, int(cy) + r + 1)
    if x0 >= x1 or y0 >= y1:
        return
    gx = np.arange(x0, x1, dtype=np.float32)[None, :]
    gy = np.arange(y0, y1, dtype=np.float32)[:, None]
    g = (np.exp(-(((gx - cx) ** 2 + (gy - cy) ** 2) / (2 * (sig * K) ** 2))) * k)[..., None]
    sub = col[y0:y1, x0:x1]
    col[y0:y1, x0:x1] = np.clip(sub * (1 - g) + np.asarray(c, np.float32) * g, 0, hi)


def star_cross(col, x, y, c, k, arm=1.6, hi=1.6):
    """Estrella con destello en cruz (para brillos)."""
    put(col, x, y, c, k, 0.45, hi)
    if k > 0.35:
        for d in np.linspace(0.6, arm, 3):
            for sx, sy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                put(col, x + sx * d, y + sy * d, c, k * (1 - d / (arm + 0.5)) * 0.7, 0.35, hi)


def smooth_noise(seed, gw, gh):
    """Ruido suave 0..1 en el lienzo de trabajo (gw x gh celdas de rejilla)."""
    r = np.random.RandomState(seed)
    g = (r.rand(gh, gw) * 255).astype(np.uint8)
    return np.asarray(Image.fromarray(g).resize((HW, HH), Image.BICUBIC), np.float32) / 255.0


def fbm(seed, gw, gh, octaves=4):
    out = np.zeros(SHP, np.float32); amp, tot = 1.0, 0.0
    for o in range(octaves):
        out += amp * smooth_noise(seed + o * 17, gw * 2 ** o, gh * 2 ** o); tot += amp; amp *= 0.5
    return out / tot


def waves(seed, n, t, kmax=3):
    rng = np.random.RandomState(seed)
    v = np.zeros(SHP, np.float32); tot = 0.0
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


def stars(col, seed, n, t, strength=0.9, ymax=H, color=(0.85, 0.9, 1.0), big=0.25):
    rng = np.random.RandomState(seed)
    for _ in range(n):
        x, y, ph = rng.randint(0, W), rng.randint(0, ymax), rng.rand()
        kk = rng.randint(1, 3)
        b = 0.3 + 0.7 * (0.5 + 0.5 * np.sin(TAU * (t * kk + ph)))
        if rng.rand() < big:
            star_cross(col, x + rng.rand() - 0.5, y + rng.rand() - 0.5, color, strength * b)
        else:
            put(col, x + rng.rand() - 0.5, y + rng.rand() - 0.5, color, strength * b * 0.8, 0.3)


def to_rgb(col):
    """array 0..1 (HH,HW,3) -> PIL RGB 320x512."""
    return Image.fromarray((np.clip(col, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGB')
