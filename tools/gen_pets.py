"""Texturas e iconos de las MASCOTAS (perro, husky, dalmata, gato, gato negro, zorro).
Uso:  python3 tools/gen_pets.py     (necesita Pillow y numpy)

Cada modelo (dog / husky / cat / fox) tiene sus cajas en LAYOUT (u, v en unidades logicas de una hoja de 64x32).
Si cambias un tamano aqui, cambialo tambien en PetModel.java (tabla VARIANTS): deben coincidir.
La imagen real es x S veces mas grande (alta resolucion); el modelo solo usa fracciones.
"""
import os, math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources", "assets")
ASSET = "atenasclient" if os.path.isdir(os.path.join(RES, "atenasclient")) else "atenassocial"
ROOT = os.path.join(RES, ASSET, "textures", "cosmetics") + os.sep
os.makedirs(ROOT + "pets", exist_ok=True); os.makedirs(ROOT + "icons", exist_ok=True)

S = 6                      # pixeles por unidad logica
TW, TH = 64, 32            # hoja logica

# tamanos (w, h, d) por modelo. Orden de colocacion = orden de este dict.
SIZES = {
    "dog":   dict(body=(6, 6, 10), head=(6, 6, 5), snout=(3, 2, 3), ear=(2, 4, 1), leg=(2, 6, 2), tail1=(2, 2, 4), tail2=(2, 2, 3)),
    "husky": dict(body=(6, 6, 10), head=(6, 6, 5), snout=(3, 2, 3), ear=(2, 3, 1), leg=(2, 6, 2), tail1=(2, 3, 4), tail2=(2, 3, 3)),
    "cat":   dict(body=(5, 5, 10), head=(5, 5, 4), snout=(3, 2, 1), ear=(2, 2, 1), leg=(2, 6, 2), tail1=(1, 1, 5), tail2=(1, 1, 4)),
    "fox":   dict(body=(5, 5, 9),  head=(5, 5, 5), snout=(2, 2, 4), ear=(2, 4, 1), leg=(2, 6, 2), tail1=(3, 3, 5), tail2=(3, 3, 4)),
}

def layout(sizes):
    """Empaqueta las cajas desplegadas en filas (estantes) de la hoja 64x32. Devuelve {parte: (u, v)}."""
    pos, x, y, rowh = {}, 0, 0, 0
    for name, (w, h, d) in sizes.items():
        bw, bh = 2 * d + 2 * w, d + h
        if x + bw > TW:
            x, y, rowh = 0, y + rowh, 0
        assert y + bh <= TH, "no cabe: " + name
        pos[name] = (x, y); x += bw; rowh = max(rowh, bh)
    return pos

LAYOUT = {k: layout(v) for k, v in SIZES.items()}

def faces(u, v, w, h, d):
    """Rectangulos (x, y, ancho, alto) de cada cara de una caja desplegada como en ModelBox."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}

def mix(a, b, t): return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
def clamp8(c): return tuple(int(max(0, min(255, round(x)))) for x in c)

def noise(rng, h, w, amp):
    n = rng.normal(0, amp, (h, w, 1)).astype(np.float32)
    return n

class Sheet:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (TW * S, TH * S), (0, 0, 0, 0)); self.rng = np.random.default_rng(seed)

    def fill(self, rect, top, bottom, fur=0.05, side_dark=0.0):
        x, y, w, h = rect; W, H = w * S, h * S
        ty = np.linspace(0, 1, H, dtype=np.float32)[:, None, None]
        base = np.array(top, np.float32) * (1 - ty) + np.array(bottom, np.float32) * ty
        base = np.repeat(base, W, axis=1)
        # pelaje: trazos verticales suaves
        stripes = self.rng.normal(0, fur * 90, (1, W, 1)).astype(np.float32)
        base = base + stripes + noise(self.rng, H, W, fur * 45)
        if side_dark:
            tx = np.linspace(-1, 1, W, dtype=np.float32)[None, :, None]
            base = base * (1 - side_dark * np.abs(tx) ** 2)
        arr = np.clip(base, 0, 255).astype(np.uint8)
        tile = Image.fromarray(arr, "RGB").convert("RGBA")
        self.img.paste(tile, (x * S, y * S))

    def draw(self, rect):
        """ImageDraw sobre una cara con coordenadas locales en unidades (x, y)."""
        x, y, w, h = rect
        layer = Image.new("RGBA", (w * S * 4, h * S * 4), (0, 0, 0, 0))
        return layer, ImageDraw.Draw(layer), (x, y, w, h)

    def commit(self, layer, rect):
        x, y, w, h = rect
        layer = layer.resize((w * S, h * S), Image.LANCZOS)
        self.img.alpha_composite(layer, (x * S, y * S))

def U(v): return v * S * 4          # unidades -> pixeles del lienzo x4

def paint(variant, spec, seed):
    kind = spec["model"]
    sizes = SIZES[kind]; lay = LAYOUT[kind]
    sh = Sheet(seed); base, belly, dark = spec["base"], spec["belly"], spec["dark"]
    F = {n: faces(*lay[n], *sizes[n]) for n in sizes}

    # ---- cuerpo
    b = F["body"]
    sh.fill(b["top"], dark, base, 0.05); sh.fill(b["bottom"], belly, belly, 0.03)
    for side in ("right", "left"): sh.fill(b[side], base, belly if spec.get("belly_side", True) else base, 0.05)
    sh.fill(b["front"], base, mix(base, dark, .3), 0.05); sh.fill(b["back"], base, mix(base, dark, .3), 0.05)
    # ---- cabeza
    hd = F["head"]
    for n in ("top", "right", "left", "back"): sh.fill(hd[n], mix(base, dark, .15) if n != "top" else dark, base, 0.05)
    sh.fill(hd["bottom"], belly, belly, 0.03)
    sh.fill(hd["front"], mix(base, dark, .1), base, 0.04)
    # ---- hocico, orejas, patas, colas
    sn = F["snout"]
    for n in sn: sh.fill(sn[n], spec.get("muzzle", belly), spec.get("muzzle", belly), 0.03)
    ear = F["ear"]
    for n in ear: sh.fill(ear[n], spec["ear"], spec["ear"], 0.04)
    lg = F["leg"]
    for n in lg: sh.fill(lg[n], base, spec.get("sock", base), 0.05)
    for t in ("tail1", "tail2"):
        tt = F[t]
        for n in tt:
            tip = spec.get("tail_tip", base) if t == "tail2" else base
            if t == "tail1" and spec.get("tail1_end"):
                sh.fill(tt[n], base, spec["tail1_end"], 0.06)   # base blanca que se oscurece hacia la punta
            else:
                sh.fill(tt[n], base if t == "tail1" else tip, tip if t == "tail2" else base, 0.05)

    rng = random.Random(seed)
    # ---- manchas / rayas del pelaje (sobre cuerpo y cabeza)
    pat = spec.get("pattern")
    if pat in ("spots", "tabby", "mask", "fox"):
        for n, rect in list(b.items()) + [("h" + k, r) for k, r in hd.items()]:
            if n in ("bottom", "hbottom"): continue
            layer, d, (x, y, w, h) = sh.draw(rect)
            if pat == "spots":
                for _ in range(max(2, w * h // 9)):
                    cx, cy, r = rng.uniform(0, w), rng.uniform(0, h), rng.uniform(.5, 1.3)
                    d.ellipse([U(cx - r), U(cy - r * .85), U(cx + r), U(cy + r * .85)], fill=spec["mark"] + (255,))
                if spec.get("mark2"):   # segundo color de manchas (gato calico)
                    for _ in range(max(1, w * h // 14)):
                        cx, cy, r = rng.uniform(0, w), rng.uniform(0, h), rng.uniform(.6, 1.5)
                        d.ellipse([U(cx - r), U(cy - r * .85), U(cx + r), U(cy + r * .85)], fill=spec["mark2"] + (255,))
            elif pat == "tabby":
                if n in ("top", "right", "left", "htop", "hright", "hleft"):
                    for i in range(int(w) + 1):
                        if i % 2 == 0: d.rectangle([U(i), 0, U(i + .7), U(h * (0.85 if n[0] != "h" else 0.4))], fill=spec["mark"] + (200,))
            sh.commit(layer, rect)
    if pat == "mask":    # husky: mascara oscura arriba, cara blanca abajo, pecho blanco
        layer, d, (x, y, w, h) = sh.draw(hd["front"])
        d.rectangle([0, U(h * .55), U(w), U(h)], fill=spec["belly"] + (255,))
        d.polygon([(U(w * .5), U(h * .35)), (U(w * .2), U(h * .6)), (U(w * .8), U(h * .6))], fill=spec["belly"] + (255,))
        sh.commit(layer, hd["front"])
    if pat == "fox":     # zorro: mejillas y pecho blancos
        layer, d, (x, y, w, h) = sh.draw(hd["front"])
        d.polygon([(0, U(h * .6)), (U(w * .5), U(h * .45)), (U(w), U(h * .6)), (U(w), U(h)), (0, U(h))], fill=spec["belly"] + (255,))
        sh.commit(layer, hd["front"])
        layer, d, (x, y, w, h) = sh.draw(b["front"]); d.rectangle([0, U(h * .4), U(w), U(h)], fill=spec["belly"] + (255,)); sh.commit(layer, b["front"])

    if pat == "patches":   # bicolor: manchas oscuras a cada lado de la cara y franja blanca en medio
        M = spec["mark"] + (255,)
        def poly(rect, pts):
            layer, d, (x, y, w, h) = sh.draw(rect)
            d.polygon([(U(px * w), U(py * h)) for px, py in pts], fill=M)
            sh.commit(layer, rect)
        # frente: la mancha izquierda baja mas y rodea el ojo; la derecha es algo mas corta (como en la foto)
        poly(hd["front"], [(0, 0), (.40, 0), (.37, .30), (.42, .62), (.30, .84), (.10, .80), (0, .70)])
        poly(hd["front"], [(.62, 0), (1, 0), (1, .62), (.88, .74), (.70, .66), (.66, .36)])
        # arriba: dos bandas oscuras (orejas) y el centro blanco
        poly(hd["top"], [(0, 0), (.38, 0), (.34, 1), (0, 1)])
        poly(hd["top"], [(.64, 0), (1, 0), (1, 1), (.68, 1)])
        # costados: la parte de arriba (donde nace la oreja) es oscura, la mejilla queda blanca
        poly(hd["right"], [(0, 0), (1, 0), (1, .55), (.55, .70), (0, .50)])
        poly(hd["left"], [(0, 0), (1, 0), (1, .50), (.45, .66), (0, .55)])
        # nuca
        poly(hd["back"], [(0, 0), (.36, 0), (.32, .60), (0, .50)])
        poly(hd["back"], [(.64, 0), (1, 0), (1, .50), (.68, .60)])

    # ---- cara: ojos, nariz, boca (cara frontal de la cabeza)
    layer, d, (x, y, w, h) = sh.draw(hd["front"])
    ey = h * .38; ex = w * .26; er = max(.6, w * .11)
    for sx in (ex, w - ex):
        d.ellipse([U(sx - er), U(ey - er * 1.15), U(sx + er), U(ey + er * 1.15)], fill=(18, 14, 16, 255))
        d.ellipse([U(sx - er * .55), U(ey - er * .6), U(sx + er * .55), U(ey + er * .6)], fill=spec["eye"] + (255,))
        d.ellipse([U(sx - er * .2), U(ey - er * .75), U(sx + er * .25), U(ey - er * .2)], fill=(255, 255, 255, 255))
    if kind == "cat":
        d.polygon([(U(w * .42), U(h * .6)), (U(w * .58), U(h * .6)), (U(w * .5), U(h * .72))], fill=spec["nose"] + (255,))
        for yy in (.7, .8):
            d.line([(U(w * .62), U(h * yy)), (U(w * .95), U(h * (yy - .05)))], fill=(235, 235, 235, 200), width=max(1, int(U(.05))))
            d.line([(U(w * .38), U(h * yy)), (U(w * .05), U(h * (yy - .05)))], fill=(235, 235, 235, 200), width=max(1, int(U(.05))))
    sh.commit(layer, hd["front"])
    layer, d, (x, y, w, h) = sh.draw(sn["front"])
    d.ellipse([U(w * .26), U(h * .02), U(w * .74), U(h * .55)], fill=spec["nose"] + (255,))
    d.ellipse([U(w * .36), U(h * .08), U(w * .5), U(h * .24)], fill=(255, 255, 255, 110))
    d.line([(U(w * .5), U(h * .55)), (U(w * .5), U(h * .8))], fill=(40, 25, 25, 255), width=max(1, int(U(.07))))
    sh.commit(layer, sn["front"])
    # lengua en la cara inferior del hocico (para cuando jadea)
    layer, d, (x, y, w, h) = sh.draw(sn["bottom"]); d.rectangle([U(w * .3), U(h * .3), U(w * .7), U(h * .9)], fill=(238, 118, 134, 255)); sh.commit(layer, sn["bottom"])
    # interior de la oreja
    layer, d, (x, y, w, h) = sh.draw(ear["front"])
    d.polygon([(U(w * .2), U(h)), (U(w * .8), U(h)), (U(w * .5), U(h * .25))], fill=spec["ear_in"] + (255,))
    sh.commit(layer, ear["front"])
    if spec.get("pattern") == "fox":   # punta negra de las orejas
        layer, d, (x, y, w, h) = sh.draw(ear["back"]); d.rectangle([0, 0, U(w), U(h * .35)], fill=(25, 20, 20, 255)); sh.commit(layer, ear["back"])
        layer, d, (x, y, w, h) = sh.draw(ear["front"]); d.rectangle([0, 0, U(w), U(h * .3)], fill=(25, 20, 20, 255)); sh.commit(layer, ear["front"])
    sh.img.save(ROOT + "pets/%s.png" % variant, optimize=True)

    # ---- icono 32x32: cara + orejas
    fr = sh.img.crop((hd["front"][0] * S, hd["front"][1] * S, (hd["front"][0] + hd["front"][2]) * S, (hd["front"][1] + hd["front"][3]) * S))
    sn_img = sh.img.crop((sn["front"][0] * S, sn["front"][1] * S, (sn["front"][0] + sn["front"][2]) * S, (sn["front"][1] + sn["front"][3]) * S))
    ear_img = sh.img.crop((ear["back"][0] * S, ear["back"][1] * S, (ear["back"][0] + ear["back"][2]) * S, (ear["back"][1] + ear["back"][3]) * S))
    BIG = 256; ic = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0))
    fw = int(BIG * .62); face = fr.resize((fw, fw), Image.LANCZOS)
    fx, fy = (BIG - fw) // 2, int(BIG * .30)
    ew, eh = int(fw * .30), int(fw * (.55 if spec["model"] in ("dog", "fox") else .4))
    e = ear_img.resize((ew, eh), Image.LANCZOS)
    ic.alpha_composite(e, (fx - 2, fy - eh + int(eh * .35))); ic.alpha_composite(e.transpose(Image.FLIP_LEFT_RIGHT), (fx + fw - ew + 2, fy - eh + int(eh * .35)))
    ic.alpha_composite(face, (fx, fy))
    sw, shh = int(fw * .5), int(fw * (.35 if kind != "cat" else .24))
    ic.alpha_composite(sn_img.resize((sw, shh), Image.LANCZOS), (fx + (fw - sw) // 2, fy + int(fw * .52)))
    ic = ic.crop(ic.getbbox()); k = 30 / max(ic.size)
    ic = ic.resize((max(1, int(ic.width * k)), max(1, int(ic.height * k))), Image.LANCZOS)
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); out.alpha_composite(ic, ((32 - ic.width) // 2, (32 - ic.height) // 2))
    out.save(ROOT + "icons/pet_%s.png" % variant)

PETS = {
    "perro":    dict(model="dog", base=(205, 150, 84), belly=(240, 214, 160), dark=(170, 118, 62), ear=(150, 100, 52), ear_in=(224, 160, 150),
                     nose=(30, 22, 22), eye=(70, 40, 20), muzzle=(236, 208, 156), tail_tip=(240, 214, 160), pattern=None),
    "husky":    dict(model="husky", base=(104, 112, 126), belly=(244, 244, 248), dark=(60, 66, 80), ear=(70, 76, 90), ear_in=(210, 190, 200),
                     nose=(24, 22, 28), eye=(110, 190, 255), muzzle=(244, 244, 248), tail_tip=(240, 240, 246), pattern="mask", sock=(236, 236, 242)),
    "dalmata":  dict(model="dog", base=(244, 244, 246), belly=(255, 255, 255), dark=(226, 226, 232), ear=(28, 28, 34), ear_in=(70, 60, 66),
                     nose=(20, 20, 24), eye=(60, 40, 30), muzzle=(255, 255, 255), tail_tip=(250, 250, 252), pattern="spots", mark=(30, 30, 36)),
    "gato":     dict(model="cat", base=(226, 142, 62), belly=(252, 228, 190), dark=(196, 110, 40), ear=(206, 120, 48), ear_in=(240, 160, 160),
                     nose=(226, 120, 130), eye=(120, 224, 100), muzzle=(252, 228, 190), tail_tip=(190, 104, 36), pattern="tabby", mark=(150, 80, 26), sock=(250, 224, 188)),
    "gatonegro": dict(model="cat", base=(36, 36, 46), belly=(60, 60, 74), dark=(22, 22, 30), ear=(30, 30, 40), ear_in=(110, 70, 90),
                     nose=(200, 110, 130), eye=(232, 232, 70), muzzle=(54, 54, 66), tail_tip=(30, 30, 40), pattern=None),
    "zorro":    dict(model="fox", base=(232, 112, 40), belly=(250, 244, 236), dark=(196, 86, 24), ear=(224, 100, 34), ear_in=(250, 220, 200),
                     nose=(22, 18, 20), eye=(110, 60, 20), muzzle=(250, 244, 236), tail_tip=(252, 248, 240), pattern="fox", sock=(40, 30, 28)),
    # Catalina: gata blanca con dos manchas negras en la cara (ojos y orejas), patas blancas y cola larga gris oscura. Privada:
    # solo la ven y la eligen los nicks que le des en /admin.
    "catalina": dict(model="cat", base=(247, 245, 243), belly=(255, 254, 252), dark=(238, 235, 232), ear=(40, 36, 40), ear_in=(150, 110, 116),
                     nose=(236, 150, 160), eye=(110, 200, 90), muzzle=(255, 254, 252), tail_tip=(48, 44, 48), tail1_end=(70, 64, 68),
                     pattern="patches", mark=(30, 27, 31), sock=(255, 253, 250)),
}

if __name__ == "__main__":
    import sys
    only = sys.argv[1:]   # p. ej. `python3 tools/gen_pets.py catalina` regenera solo esa
    for i, (n, spec) in enumerate(PETS.items()):
        if only and n not in only: continue
        paint(n, spec, 100 + i)
    for k, v in LAYOUT.items():
        print(k, v)
    print("mascotas listas")
