"""Texturas e iconos de las ALAS DE MEMBRANA (10 variantes).
Uso:  python3 tools/gen_membrane.py     (necesita Pillow y numpy)

La geometria (WX, WY, ANG, LEN, BX, BY, SUB, mapeo plano MAP_*) DEBE coincidir con MembraneWing.java.
Textura 128 x 128:
  - filas 0..V_MEM*128  : membrana, proyeccion plana del ala (u = (x-MAP_X0)/MAP_W ; v = V_MEM*(MAP_Y1-y)/MAP_H)
  - filas BONE_V0..BONE_V1 (franja inferior): color liso de los huesos (arm + dedos)
"""
import math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "atenasclient", "textures", "cosmetics") + os.sep
os.makedirs(ROOT + "wings", exist_ok=True); os.makedirs(ROOT + "icons", exist_ok=True)

# ---- geometria (igual que MembraneWing.java) ----
WX, WY = 11.0, 12.0
ANG = [0.70, 0.30, -0.12, -0.55]
LEN = [17.0, 24.0, 24.0, 19.0]
BX, BY = 3.0, -14.0
SUB = 4
SCALLOP_FINGER, SCALLOP_BODY = 0.22, 0.12
MAP_X0, MAP_W, MAP_Y1, MAP_H = -1.0, 37.0, 23.5, 38.5
V_MEM, BONE_V0, BONE_V1 = 0.90, 0.93, 0.99
SIZE = 128
SS = 4  # supersampling

def tips():
    return [(WX + l * math.cos(a), WY + l * math.sin(a)) for a, l in zip(ANG, LEN)]

def outline():
    """Contorno de la membrana (pivote, muneca, puntas con festones, punto del cuerpo)."""
    pts = [(0.0, 0.0), (WX, WY)]
    ang = ANG + [math.atan2(BY - WY, BX - WX)]
    ln = LEN + [math.hypot(BX - WX, BY - WY)]
    for p in range(4):
        d = SCALLOP_FINGER if p < 3 else SCALLOP_BODY
        for j in range(SUB + 1):
            t = j / SUB
            a = ang[p] + (ang[p + 1] - ang[p]) * t
            r = (ln[p] + (ln[p + 1] - ln[p]) * t) * (1 - d * math.sin(math.pi * t))
            pts.append((WX + r * math.cos(a), WY + r * math.sin(a)))
    return pts

def to_px(x, y):
    u = (x - MAP_X0) / MAP_W
    v = V_MEM * (MAP_Y1 - y) / MAP_H
    return u * SIZE * SS, v * SIZE * SS

# nombre -> paleta
V = {
 "murcielago": dict(name="Alas de Murci\u00e9lago", root=(56, 42, 84),  edge=(120, 96, 168), vein=(30, 20, 48),  glow=(190, 150, 255), bone=(32, 24, 44),  alpha=235),
 "vampiro":    dict(name="Alas de Vampiro",        root=(88, 10, 28),  edge=(170, 30, 52),  vein=(40, 4, 14),   glow=(255, 90, 110),  bone=(20, 10, 14),  alpha=240),
 "dragon":     dict(name="Alas de Drag\u00f3n",    root=(150, 30, 20), edge=(255, 140, 40), vein=(90, 14, 10),  glow=(255, 210, 90),  bone=(60, 20, 14),  alpha=235),
 "abismo":     dict(name="Alas del Abismo",        root=(6, 40, 74),   edge=(20, 170, 190), vein=(4, 24, 50),   glow=(90, 255, 230),  bone=(10, 30, 50),  alpha=225),
 "fantasma":   dict(name="Alas Fantasma",          root=(170, 200, 220), edge=(235, 250, 255), vein=(120, 150, 180), glow=(255, 255, 255), bone=(150, 170, 190), alpha=150),
 "jade":       dict(name="Alas de Jade",           root=(14, 84, 58),  edge=(90, 215, 150), vein=(8, 52, 36),   glow=(170, 255, 200), bone=(10, 50, 36),  alpha=235),
 "solar":      dict(name="Alas Solares",           root=(190, 120, 20), edge=(255, 224, 110), vein=(130, 80, 10), glow=(255, 250, 190), bone=(110, 70, 10), alpha=235),
 "ceniza":     dict(name="Alas de Ceniza",         root=(46, 46, 52),  edge=(120, 118, 126), vein=(24, 24, 28), glow=(255, 130, 40),  bone=(24, 24, 28),  alpha=235),
 "cristal":    dict(name="Alas de Cristal",        root=(80, 150, 210), edge=(200, 245, 255), vein=(50, 110, 170), glow=(255, 255, 255), bone=(60, 120, 180), alpha=190),
 "nebulosa":   dict(name="Alas Nebulosa",          root=(20, 12, 60),  edge=(150, 70, 220), vein=(10, 6, 34),   glow=(255, 160, 255), bone=(16, 10, 40),  alpha=230),
}
COLORS = {k: 0xFF000000 | (v["edge"][0] << 16) | (v["edge"][1] << 8) | v["edge"][2] for k, v in V.items()}

def paint(vid, p, seed):
    rng = np.random.default_rng(seed)
    W = SIZE * SS
    mask = Image.new("L", (W, W), 0)
    d = ImageDraw.Draw(mask)
    d.polygon([to_px(x, y) for x, y in outline()], fill=255)
    m = np.asarray(mask, np.float32) / 255.0

    # distancia radial desde el pivote para el degradado raiz -> borde
    ys, xs = np.mgrid[0:W, 0:W].astype(np.float32)
    mx = xs / (SIZE * SS) * MAP_W + MAP_X0
    my = MAP_Y1 - ys / (SIZE * SS) / V_MEM * MAP_H
    r = np.hypot(mx - WX * 0.6, my - WY * 0.4) / 30.0
    r = np.clip(r, 0, 1)[..., None]
    root, edge = np.array(p["root"], np.float32), np.array(p["edge"], np.float32)
    col = root * (1 - r) + edge * r

    # textura: ruido de celdas suaves
    n = rng.normal(0, 1, (64, 64)).astype(np.float32)
    n = np.asarray(Image.fromarray(n).resize((W, W), Image.BICUBIC), np.float32)[..., None]
    col = col * (1 + 0.10 * n)

    # venas: lineas desde la muneca hacia las puntas y festones
    veins = Image.new("L", (W, W), 0)
    vd = ImageDraw.Draw(veins)
    for (tx, ty) in tips():
        vd.line([to_px(WX, WY), to_px(tx, ty)], fill=255, width=SS * 2)
    ang = ANG + [math.atan2(BY - WY, BX - WX)]
    for i in range(4):
        am = (ang[i] + ang[i + 1]) / 2
        L = (LEN[i] + (LEN + [math.hypot(BX - WX, BY - WY)])[i + 1]) / 2 * 0.75
        vd.line([to_px(WX, WY), to_px(WX + L * math.cos(am), WY + L * math.sin(am))], fill=170, width=SS)
    veins = np.asarray(veins.filter(ImageFilter.GaussianBlur(SS * 0.6)), np.float32)[..., None] / 255.0
    col = col * (1 - 0.55 * veins) + np.array(p["vein"], np.float32) * 0.55 * veins

    # brillo del borde (interior cercano al contorno)
    edge_mask = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(SS * 3 + 1)).filter(ImageFilter.GaussianBlur(SS))
    inner = np.asarray(edge_mask, np.float32)[..., None] / 255.0
    rim = np.clip(m[..., None] - inner, 0, 1)
    col = col * (1 - 0.8 * rim) + np.array(p["glow"], np.float32) * 0.8 * rim

    if vid == "nebulosa":   # estrellas
        for _ in range(180):
            x, y = int(rng.integers(0, W)), int(rng.integers(0, W))
            if m[y, x] > 0.5:
                col[max(0, y - 2):y + 3, max(0, x - 2):x + 3] = np.array((255, 240, 255), np.float32)
    if vid == "ceniza":     # grietas de brasa
        cr = Image.new("L", (W, W), 0); cd = ImageDraw.Draw(cr)
        for _ in range(14):
            x, y = rng.uniform(0, W), rng.uniform(0.1 * W, 0.8 * W)
            for _ in range(7):
                nx, ny = x + rng.uniform(-30, 30) * SS / 2, y + rng.uniform(-30, 30) * SS / 2
                cd.line([(x, y), (nx, ny)], fill=255, width=SS); x, y = nx, ny
        cr = np.asarray(cr, np.float32)[..., None] / 255.0
        col = col * (1 - 0.8 * cr) + np.array(p["glow"], np.float32) * 0.8 * cr
    if vid == "cristal":    # facetas
        for _ in range(26):
            cx, cy = rng.uniform(0, W), rng.uniform(0, 0.9 * W)
            sz = rng.uniform(10, 40) * SS / 2
            f = Image.new("L", (W, W), 0); ImageDraw.Draw(f).polygon([(cx, cy - sz), (cx + sz * .7, cy), (cx, cy + sz), (cx - sz * .7, cy)], fill=70)
            f = np.asarray(f, np.float32)[..., None] / 255.0
            col = col * (1 - f) + np.array((255, 255, 255), np.float32) * f

    a = (m * p["alpha"])[..., None]
    img = np.concatenate([np.clip(col, 0, 255), a], axis=2).astype(np.uint8)
    big = Image.fromarray(img, "RGBA")
    # franja de huesos (opaca)
    bd = ImageDraw.Draw(big)
    bone = tuple(p["bone"]) + (255,)
    bd.rectangle([0, int(BONE_V0 * W), W, int(BONE_V1 * W)], fill=bone)
    small = big.resize((SIZE, SIZE), Image.LANCZOS)
    small.save(ROOT + "wings/%s.png" % vid, optimize=True)

    # ---- icono 32x32: las dos alas vistas de frente
    mem = big.crop((0, 0, W, int(V_MEM * W)))
    bb = mem.getbbox(); mem = mem.crop(bb)
    BIG = 256
    k = (BIG / 2 - 6) / mem.width
    mem = mem.resize((max(1, int(mem.width * k)), max(1, int(mem.height * k))), Image.LANCZOS)
    ic = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0))
    y0 = (BIG - mem.height) // 2
    ic.alpha_composite(mem, (BIG // 2 + 3, y0))
    ic.alpha_composite(mem.transpose(Image.FLIP_LEFT_RIGHT), (BIG // 2 - 3 - mem.width, y0))
    ic.resize((32, 32), Image.LANCZOS).save(ROOT + "icons/wings_%s.png" % vid)

def main():
    for i, (vid, p) in enumerate(V.items()):
        paint(vid, p, 700 + i)
    print("alas de membrana listas:", ", ".join(V))
    print("Java: " + ", ".join('add(Type.WINGS, "%s", "%s", 0x%08X)' % (k, v["name"], COLORS[k]) for k, v in V.items()))

if __name__ == "__main__":
    main()
