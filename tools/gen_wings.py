"""Alas nuevas (plumas con forma, alta resolucion) y capas ANIMADAS para Atenas 1.20.1 (todas de 12 fotogramas).
Uso:  python3 tools/gen_wings.py   (necesita Pillow y numpy)
La geometria de las alas debe coincidir con WingsModel.java (P_LEN, C_LEN, ...)."""
import os, math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "atenassocial", "textures", "cosmetics") + os.sep

# ======================= ALAS =======================
P_LEN = [22, 22, 21, 20, 19, 17, 15, 13, 11, 9]; P_H = 4
C_LEN = [12, 12, 11, 10, 9, 8, 7]; C_H = 3
BONE_LEN, BONE_H = 15, 2
P_V0 = 0
C_V0 = P_V0 + len(P_LEN) * (P_H + 1)
BONE_V = C_V0 + len(C_LEN) * (C_H + 1)
S = 8                      # pixeles reales por unidad del modelo (la textura declarada es 64x128)
TW, TH = 64, 128

def mix(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def feather_face(w, h, main, tip, outline, tip_right, bone=False):
    """Cara w x h (unidades) de una pluma: hoja puntiaguda con raquis, barbas y degradado. Raiz a un lado, punta al otro."""
    W, H = w * S, h * S
    k = 3                                  # supermuestreo para bordes suaves
    im = Image.new("RGBA", (W * k, H * k), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    top, bot = [], []
    n = 40
    for i in range(n + 1):
        t = i / n                          # 0 raiz -> 1 punta
        if bone:
            half = (0.5 - 0.18 * t) * H * k   # hueso: casi recto, afinandose
        else:
            half = (0.5 * (1 - t ** 2.4) ** 0.9) * H * k * (0.55 + 0.45 * min(1, t * 6))  # hoja: ancha en el medio, punta fina
        x = t * W * k
        cy = H * k / 2 + (0 if bone else math.sin(t * math.pi) * 0.06 * H * k)
        top.append((x, cy - half)); bot.append((x, cy + half))
    poly = top + bot[::-1]
    d.polygon(poly, fill=outline + (255,))
    # relleno interior (mas pequeno que el contorno)
    inner = Image.new("RGBA", im.size, (0, 0, 0, 0)); di = ImageDraw.Draw(inner)
    ins = []
    for (x, y), (_, y2) in zip(top, bot):
        c = (y + y2) / 2; ins.append((x, c - (c - y) * 0.72))
    ins2 = [(x, (y + y2) / 2 + ((y2 - y) / 2) * 0.72) for (x, y), (_, y2) in zip(top, bot)]
    di.polygon(ins + ins2[::-1], fill=(255, 255, 255, 255))
    px = inner.load(); base = im.load()
    for yy in range(inner.size[1]):
        for xx in range(inner.size[0]):
            if px[xx, yy][3]:
                t = xx / (W * k)
                col = mix(main, tip, max(0.0, (t - 0.65) / 0.35) ** 1.5)
                # barbas: lineas diagonales suaves
                shade = 0.88 if ((xx // k + yy // k * 1) // 3) % 2 else 1.0
                base[xx, yy] = tuple(int(c * shade) for c in col) + (255,)
    # raquis (nervio central) mas claro
    cy = H * k / 2
    d.line([(0, cy), (W * k * 0.97, cy)], fill=mix(main, (255, 255, 255), 0.55) + (255,), width=max(1, k))
    im = im.resize((W, H), Image.LANCZOS)
    return im.transpose(Image.FLIP_LEFT_RIGHT) if tip_right is False else im

def paste_box_strip(sheet, u0, v0, w, h, face_back, face_front):
    """Caja w x h x 1: cara trasera (exterior) en (u0+1+w+1, v0+1), delantera en (u0+1, v0+1). Bordes transparentes."""
    sheet.paste(face_back, ((u0 + 1 + w + 1) * S, (v0 + 1) * S))
    sheet.paste(face_front, ((u0 + 1) * S, (v0 + 1) * S))

def wings(name, main, tip, outline, bone_col):
    sheet = Image.new("RGBA", (TW * S, TH * S), (0, 0, 0, 0))
    for i, w in enumerate(P_LEN):
        t = i / (len(P_LEN) - 1)
        c = mix(main, tuple(max(0, v - 25) for v in main), t * 0.5)
        back = feather_face(w, P_H, c, tip, outline, True)       # exterior: raiz a la izquierda, punta a la derecha
        front = feather_face(w, P_H, mix(c, outline, 0.35), tip, outline, False)  # interior: espejo, algo mas oscuro
        paste_box_strip(sheet, 0, P_V0 + i * (P_H + 1), w, P_H, back, front)
    for i, w in enumerate(C_LEN):
        c = mix(main, (255, 255, 255), 0.18 + 0.05 * i)
        back = feather_face(w, C_H, c, mix(tip, c, 0.4), outline, True)
        front = feather_face(w, C_H, mix(c, outline, 0.4), tip, outline, False)
        paste_box_strip(sheet, 0, C_V0 + i * (C_H + 1), w, C_H, back, front)
    back = feather_face(BONE_LEN, BONE_H, bone_col, bone_col, outline, True, bone=True)
    front = feather_face(BONE_LEN, BONE_H, mix(bone_col, outline, .4), bone_col, outline, False, bone=True)
    paste_box_strip(sheet, 0, BONE_V, BONE_LEN, BONE_H, back, front)
    sheet.save(ROOT + "wings/%s.png" % name, optimize=True)
    # icono 32x32: el abanico de plumas vista desde atras (raiz abajo-izquierda)
    BIG = 256; ic = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0)); sc = BIG * 0.46 / (22 * S)
    def put(face, ang, ox, oy):
        f = face.resize((int(face.width * sc), max(2, int(face.height * sc))), Image.LANCZOS)
        sq = Image.new("RGBA", (f.width * 2 + 4, f.width * 2 + 4), (0, 0, 0, 0)); cx = sq.width // 2
        sq.paste(f, (cx, cx - f.height // 2)); sq = sq.rotate(math.degrees(ang), resample=Image.BICUBIC)
        ic.alpha_composite(sq, (int(ox - sq.width / 2), int(oy - sq.height / 2)))
    for i, w in enumerate(P_LEN):
        t = i / (len(P_LEN) - 1); c = mix(main, tuple(max(0, v - 25) for v in main), t * .5)
        put(feather_face(w, P_H, c, tip, outline, True), 0.50 - i * 0.15, 40, 190)
    for i, w in enumerate(C_LEN):
        c = mix(main, (255, 255, 255), 0.18 + 0.05 * i)
        put(feather_face(w, C_H, c, mix(tip, c, .4), outline, True), 0.42 - i * 0.19, 40, 190)
    put(feather_face(BONE_LEN, BONE_H, bone_col, bone_col, outline, True, bone=True), 0.66, 40, 190)
    bb = ic.getbbox() or (0, 0, BIG, BIG); ic = ic.crop(bb)
    k = 30 / max(ic.size); ic = ic.resize((max(1, int(ic.width * k)), max(1, int(ic.height * k))), Image.LANCZOS)
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); out.alpha_composite(ic, ((32 - ic.width) // 2, (32 - ic.height) // 2))
    out.save(ROOT + "icons/wings_%s.png" % name)

WINGS = {
    "doradas":   ((245, 197, 66), (255, 245, 190), (150, 105, 20), (200, 150, 40)),
    "oscuras":   ((60, 30, 95), (210, 70, 255), (18, 8, 32), (40, 20, 60)),
    "angel":     ((245, 250, 255), (255, 255, 255), (150, 180, 215), (200, 215, 235)),
    "atenas":    ((139, 92, 246), (236, 72, 153), (50, 25, 110), (95, 60, 190)),
    "fuego":     ((255, 110, 20), (255, 235, 90), (130, 30, 10), (200, 70, 20)),
    "hielo":     ((170, 225, 255), (255, 255, 255), (60, 120, 190), (120, 180, 230)),
    "neon":      ((20, 230, 230), (255, 60, 200), (10, 40, 70), (15, 150, 170)),
    "esmeralda": ((46, 204, 113), (210, 255, 225), (10, 90, 50), (30, 140, 80)),
    "demonio":   ((170, 20, 30), (255, 120, 40), (35, 5, 10), (90, 10, 18)),
}
for n, c in WINGS.items():
    wings(n, *c)

# ======================= CAPAS ANIMADAS =======================
# Tira vertical de N fotogramas de 64x32 unidades (x4 de resolucion). Cada cara externa es 10x16 unidades = 40x64 px.
CS = 4
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
