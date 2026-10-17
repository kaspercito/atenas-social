"""Alas (atlas de plumas, malla propia) y capas ANIMADAS.
Uso:  python3 tools/gen_wings.py   (necesita Pillow y numpy)

ATLAS DE ALAS: una sola imagen de 256 x (26 * 40). Cada pluma ocupa una franja de 256 x 40 px: la raiz a la izquierda (u=0)
y la punta a la derecha (u=1). El orden y las medidas deben coincidir con WingMesh.java (LEN, WID):
  0 = hueso (borde de ataque) | 1..11 = primarias | 12..19 = secundarias | 20..25 = coberteras.
Ya no son cajas de Minecraft: el mod dibuja cada pluma como una tira de cuadrados doblada y animada.
"""
import os, math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources", "assets")
ASSET = "atenasclient" if os.path.isdir(os.path.join(RES, "atenasclient")) else "atenassocial"
ROOT = os.path.join(RES, ASSET, "textures", "cosmetics") + os.sep

# ======================= ALAS =======================
LEN = [16] + [26, 26, 25, 24, 23, 22, 20, 18, 16, 14, 12] + [17, 17, 16, 15, 14, 12.5, 11, 9.5] + [10, 10, 9, 8, 7, 6]
WID = [2.6] + [5.2] * 11 + [4.4] * 8 + [3.6] * 6
N = len(LEN)               # 26
AW, BH = 256, 40           # franja por pluma

def smooth(a, b, x):
    t = np.clip((x - a) / (b - a + 1e-9), 0, 1); return t * t * (3 - 2 * t)

def mixc(a, b, t):
    t = t[..., None]; return np.array(a, np.float32) * (1 - t) + np.array(b, np.float32) * t

def feather(k, pal, seed):
    """Franja RGBA (BH x AW) de la pluma k. Hoja con raquis, barbas, borde y degradado raiz -> punta."""
    root, mid, tip, rim, shaft = pal
    rng = np.random.default_rng(seed * 131 + k)
    u = ((np.arange(AW) + .5) / AW)[None, :]
    v = ((np.arange(BH) + .5) / BH * 2 - 1)[:, None]
    bone = (k == 0)
    if bone:
        hw = 0.78 * (1 - 0.55 * u) + 0 * v
        c = 0 * u + 0 * v
    else:
        grow = np.clip(u / 0.10, 0, 1) ** 0.6
        fall = (1 - np.clip((u - 0.50) / 0.50, 0, 1) ** 2.1) ** 0.62
        hw = 0.94 * grow * fall + 0 * v
        c = 0.10 * np.sin(np.pi * u) + 0 * v           # la pluma se curva un poco
    d = np.abs(v - c)
    hw = np.where(v - c < 0, hw, hw * 0.86)            # borde de ataque mas recto
    alpha = np.clip((hw - d) * (BH / 2) + .5, 0, 1)
    layer = 0.0 if k == 0 else (0.0 if k <= 11 else (0.5 if k <= 19 else 1.0))
    # degradado: oscuro en la raiz, color pleno en el medio, punta clara
    t = np.broadcast_to(u, alpha.shape)
    col = mixc(root, mid, smooth(0.0, 0.5, t) ** 0.9)
    col = mixc(col, tip, smooth(0.62, 1.0, t) ** 1.35) if not bone else col
    col = col + 0 * alpha[..., None]
    # barbas: lineas oblicuas desde el raquis hacia atras
    b = u * AW / 5.5 + np.abs(v - c) * BH * 0.22
    bar = 0.5 + 0.5 * np.cos(b * 2 * math.pi)
    col = col * (0.88 + 0.12 * bar[..., None])
    # borde (rim) y brillo del borde de ataque
    edge = smooth(0.62, 1.0, d / np.maximum(hw, 1e-3))
    col = col * (1 - 0.55 * edge[..., None]) + np.array(rim, np.float32) * 0.55 * edge[..., None]
    lead = smooth(0.78, 0.98, d / np.maximum(hw, 1e-3)) * (v - c < 0) * (1 - smooth(0.5, 1, t))
    col = col + lead[..., None] * 60
    # raquis (nervio central)
    shw = (0.07 * (1 - 0.7 * u) + 0.012) + 0 * v
    sh = np.clip((shw - d) * (BH / 2) + .5, 0, 1) * (u < 0.96)
    col = col * (1 - sh[..., None] * .85) + np.array(shaft, np.float32) * sh[..., None] * .85
    # variacion por pluma y ruido fino
    col = col * (0.93 + 0.14 * ((k * 37) % 11) / 10.0) * (1 + 0.04 * layer)
    col = col + rng.normal(0, 3.0, col.shape).astype(np.float32)
    out = np.dstack([np.clip(col, 0, 255), alpha * 255]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")

def wings(name, pal, seed):
    sheet = Image.new("RGBA", (AW, BH * N), (0, 0, 0, 0))
    for k in range(N):
        sheet.paste(feather(k, pal, seed), (0, k * BH))
    sheet.save(ROOT + "wings/%s.png" % name, optimize=True)
    # icono 32x32: abanico de plumas (mismos angulos que WingMesh)
    BIG = 320; ic = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0)); px, py = 40, 250
    def put(k, ang):
        f = feather(k, pal, seed); sc = 190 / 26.0 * LEN[k] / AW
        f = f.resize((max(2, int(AW * sc)), max(2, int(BH * sc * 1.25))), Image.LANCZOS)
        sq = Image.new("RGBA", (f.width * 2 + 8, f.width * 2 + 8), (0, 0, 0, 0)); cx = sq.width // 2
        sq.paste(f, (cx, cx - f.height // 2)); sq = sq.rotate(math.degrees(ang), resample=Image.BICUBIC)
        ic.alpha_composite(sq, (int(px - sq.width / 2), int(py - sq.height / 2)))
    for i in range(11): put(1 + i, 0.58 - i * 0.145)
    for j in range(8): put(12 + j, 0.50 - j * 0.18)
    for m in range(6): put(20 + m, 0.42 - m * 0.21)
    put(0, 0.78)
    ic = ic.crop(ic.getbbox()); kk = 30 / max(ic.size)
    ic = ic.resize((max(1, int(ic.width * kk)), max(1, int(ic.height * kk))), Image.LANCZOS)
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); out.alpha_composite(ic, ((32 - ic.width) // 2, (32 - ic.height) // 2))
    out.save(ROOT + "icons/wings_%s.png" % name)

#            raiz              medio             punta             borde            raquis
WINGS = {
    "doradas":   ((150, 100, 22), (245, 197, 66), (255, 244, 180), (122, 80, 10), (255, 232, 150)),
    "oscuras":   ((20, 8, 36), (74, 32, 114), (215, 72, 255), (12, 4, 22), (160, 90, 210)),
    "angel":     ((200, 220, 245), (240, 248, 255), (255, 255, 255), (165, 196, 232), (255, 255, 255)),
    "atenas":    ((50, 25, 110), (139, 92, 246), (236, 72, 153), (35, 15, 85), (225, 205, 255)),
    "fuego":     ((120, 20, 8), (255, 110, 20), (255, 236, 100), (90, 15, 5), (255, 205, 110)),
    "hielo":     ((90, 150, 210), (170, 225, 255), (255, 255, 255), (60, 120, 190), (236, 250, 255)),
    "neon":      ((8, 60, 90), (20, 230, 230), (255, 60, 200), (5, 30, 50), (205, 255, 255)),
    "esmeralda": ((8, 80, 45), (46, 204, 113), (215, 255, 230), (6, 60, 32), (205, 255, 225)),
    "demonio":   ((30, 4, 8), (170, 20, 30), (255, 120, 40), (25, 3, 6), (255, 150, 80)),
}
for i, (n, pal) in enumerate(WINGS.items()):
    wings(n, pal, i + 1)

# ======================= CAPAS ANIMADAS =======================
# Tira vertical de N fotogramas de 64x32 unidades (x6 de resolucion). Cada cara externa es 10x16 unidades = 40x64 px.
CS = 6
FW, FH = 10 * CS, 16 * CS
yy, xx = np.mgrid[0:FH, 0:FW].astype(np.float32)
Y, X = yy / FH, xx / FW

def pal(t, stops):
    """t en [0,1] -> color interpolado entre paradas [(pos, (r,g,b)), ...]."""
    t = np.clip(t, 0, 1); out = np.zeros(t.shape + (3,), np.float32)
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        m = (t >= p0) & (t <= p1); f = ((t - p0) / (p1 - p0 + 1e-9))[m][:, None]
        out[m] = np.array(c0) * (1 - f) + np.array(c1) * f
    return out

def rnd2(ix, iy):  # ruido hash determinista
    return (np.sin(ix * 12.9898 + iy * 78.233) * 43758.5453) % 1.0

def f_llamas(k, N):
    ph = 2 * math.pi * k / N
    # lenguas de fuego que suben; periodicas en el tiempo (sumas de senos con frecuencias enteras)
    v = (np.sin(X * 17 + ph * 2) * .25 + np.sin(X * 31 - ph * 3 + Y * 6) * .2 + np.sin(X * 9 + ph) * .15)
    height = 0.55 + v                          # altura de la llama en cada columna
    fire = np.clip((Y - (1 - height)) / height, 0, 1)    # 0 punta .. 1 base
    flick = np.sin(Y * 22 + ph * 4 + X * 8) * .06
    c = pal(fire ** .8 + flick, [(0, (20, 4, 6)), (.25, (170, 20, 8)), (.6, (255, 110, 10)), (1, (255, 240, 120))])
    ember = (rnd2(np.floor(X * 20), np.floor((Y * 20 + k * 3) % 20)) > .985)[..., None]
    return np.where(ember, np.array([255, 230, 150]), c)

def f_aurora(k, N):
    ph = 2 * math.pi * k / N
    w = np.sin(Y * 9 + np.sin(X * 6 + ph) * 1.6 + ph) * .5 + np.sin(Y * 5 - X * 4 - ph) * .3
    band = np.clip(.5 + w, 0, 1)
    c = pal(band, [(0, (6, 10, 40)), (.35, (20, 140, 120)), (.65, (60, 230, 160)), (.85, (150, 100, 240)), (1, (230, 140, 255))])
    star = (rnd2(np.floor(X * 24), np.floor(Y * 24)) > .993) * (0.5 + .5 * np.sin(ph * 2 + rnd2(np.floor(X * 24), np.floor(Y * 24)) * 20))
    return np.clip(c + star[..., None] * 200, 0, 255)

def f_cosmos(k, N):
    ph = 2 * math.pi * k / N
    n = (np.sin(X * 8 + Y * 5 + ph) + np.sin(X * 13 - Y * 9 - ph * 2) * .6 + np.sin(Y * 11 + ph)) / 2.6
    c = pal(n * .5 + .5, [(0, (8, 4, 28)), (.5, (60, 20, 120)), (.8, (150, 50, 190)), (1, (240, 120, 230))])
    sx, sy = np.floor(X * 26), np.floor(Y * 26)
    tw = np.sin(ph + rnd2(sx, sy) * 40) * .5 + .5
    star = (rnd2(sx, sy) > .975) * (.35 + .65 * tw)
    return np.clip(c + star[..., None] * 230, 0, 255)

def f_lluvia(k, N):
    # gotas que caen: el patron se repite cada 1 unidad de alto => bucle perfecto
    cols = np.floor(X * 10)
    speed = 1 + np.floor(rnd2(cols, 1.0) * 1)  # todas bajan 1 vuelta por ciclo
    off = rnd2(cols, 3.0)
    t = (Y - k / N * speed + off) % 1.0
    head = np.clip(1 - t * 5.5, 0, 1) ** 1.6   # cabeza brillante y cola que se apaga
    glyph = (rnd2(cols, np.floor((Y * 16 + 0) )) > .45).astype(np.float32) * .35 + .65
    g = head * glyph
    c = np.stack([20 + g * 150 * (g > .8), 12 + g * 235, 10 + g * 90 + (g > .85) * 120], -1)
    return np.clip(c, 0, 255)

def f_mareas(k, N):
    ph = 2 * math.pi * k / N
    wave = lambda a, b, p: np.sin(Y * a * 6.283 + X * b + p)
    h = wave(2, 3, ph) * .35 + wave(3, -4, -ph * 2) * .25 + wave(5, 2, ph * 3) * .12
    t = np.clip(Y * .9 + h * .22, 0, 1)
    c = pal(t, [(0, (150, 230, 250)), (.25, (30, 150, 220)), (.7, (8, 60, 150)), (1, (4, 20, 70))])
    foam = (h > .62).astype(np.float32)[..., None]
    return np.clip(c * (1 - foam * .8) + foam * .8 * np.array([235, 250, 255]), 0, 255)

GLYPH = ["01110", "10001", "10001", "11111", "10001", "10001", "10001"]
def f_pulso(k, N):
    ph = 2 * math.pi * k / N
    base = pal(Y, [(0, (10, 8, 34)), (1, (40, 10, 70))])
    # lineas de neon que se desplazan
    lines = (np.sin((X * 30 + Y * 12) + ph * 4) > .93).astype(np.float32)
    # glifo A pulsante, centrado
    gx0, gy0, sc = 2.5 * CS, 4 * CS, CS
    A = np.zeros((FH, FW), np.float32)
    for ry, row in enumerate(GLYPH):
        for rx, ch in enumerate(row):
            if ch == "1":
                A[int(gy0 + ry * sc):int(gy0 + (ry + 1) * sc), int(gx0 + rx * sc):int(gx0 + (rx + 1) * sc)] = 1
    pulse = .55 + .45 * math.sin(ph)
    from PIL import Image as _I
    glow = np.array(_I.fromarray((A * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(5))).astype(np.float32) / 255
    scan = np.exp(-(((Y - ((k / N) % 1.0)) * FH) ** 2) / 18)[..., None] * np.array([120, 255, 255])
    c = base + lines[..., None] * np.array([236, 72, 153]) * .55 + glow[..., None] * np.array([34, 211, 238]) * 1.1 * pulse + A[..., None] * np.array([255, 255, 255]) * (.6 + .4 * pulse) + scan * .25
    c[:2, :] = (255, 255, 255); c[-2:, :] = (255, 255, 255); c[:, :2] = (255, 255, 255); c[:, -2:] = (255, 255, 255)
    return np.clip(c, 0, 255)

def cape_frame(face):
    """face: (FH, FW, 3) -> imagen 64x32 (x CS) con cara externa, interna oscura y bordes."""
    outer = Image.fromarray(face.astype(np.uint8)).convert("RGBA")
    ia = (face * .55).astype(np.uint8)
    inner = Image.fromarray(ia).convert("RGBA")
    tex = Image.new("RGBA", (64 * CS, 32 * CS), (0, 0, 0, 0))
    tex.paste(outer, (1 * CS, 1 * CS)); tex.paste(inner, (12 * CS, 1 * CS))
    top = outer.crop((0, 0, FW, CS)); bot = outer.crop((0, FH - CS, FW, FH))
    tex.paste(top.resize((FW, CS)), (1 * CS, 0)); tex.paste(bot.resize((FW, CS)), (11 * CS, 0))
    side = outer.crop((0, 0, CS, FH)); tex.paste(side, (0, 1 * CS)); tex.paste(side, (11 * CS, 1 * CS))
    return tex

ANIM = {"llamas": (f_llamas, 12), "aurora": (f_aurora, 12), "cosmos": (f_cosmos, 12),
        "lluvia": (f_lluvia, 12), "mareas": (f_mareas, 12), "pulso": (f_pulso, 12)}
for name, (fn, N) in ANIM.items():
    strip = Image.new("RGBA", (64 * CS, 32 * CS * N), (0, 0, 0, 0))
    for k in range(N):
        strip.paste(cape_frame(fn(k, N)), (0, k * 32 * CS))
    strip.save(ROOT + "capes/%s.png" % name, optimize=True)
    # icono 32x32: cara externa del fotograma 0 (con borde)
    ic = Image.fromarray(fn(0, N).astype(np.uint8)).convert("RGBA").resize((20, 32), Image.LANCZOS)
    icon = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); icon.paste(ic, (6, 0))
    icon.save(ROOT + "icons/cape_%s.png" % name)

print("alas y capas animadas listas")
