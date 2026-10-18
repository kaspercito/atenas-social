"""Texturas e iconos de las MASCOTAS + models.json (la forma de cada modelo, que lee el juego).
Uso:  python3 tools/gen_pets.py [id ...]     (necesita Pillow y numpy)

La forma sale de tools/pet_models.py. Este script reparte la hoja (128 x 64 unidades logicas, 8 px por unidad = 1024 x 512),
pinta pelaje / plumas / escamas por pieza y escribe pets/<id>.png, icons/pet_<id>.png y pets/models.json.
"""
import os, sys, json, math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
import pet_models as PM

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources", "assets")
ASSET = "atenasclient" if os.path.isdir(os.path.join(RES, "atenasclient")) else "atenassocial"
ROOT = os.path.join(RES, ASSET, "textures", "cosmetics") + os.sep
os.makedirs(ROOT + "pets", exist_ok=True); os.makedirs(ROOT + "icons", exist_ok=True)
S = 8                       # pixeles por unidad logica
TW, TH = 128, 64            # hoja logica
Q = 3                       # supermuestreo al dibujar formas


# ------------------------------------------------------------------ reparto de la hoja
def box_size(s):
    w, h, d = s
    return int(2 * d + 2 * w), int(d + h)

def layout(kind):
    """{clave de textura: (u, v)}; las piezas con la misma clave (patas, orejas, alas) comparten textura."""
    groups = {}
    for p in kind["pieces"]:
        groups.setdefault(p["share"], p["s"])
    order = sorted(groups.items(), key=lambda kv: -box_size(kv[1])[1] * 1000 - box_size(kv[1])[0])
    pos, x, y, rowh = {}, 0, 0, 0
    for key, s in order:
        bw, bh = box_size(s)
        if x + bw > TW:
            x, y, rowh = 0, y + rowh, 0
        assert y + bh <= TH, "no cabe " + key
        pos[key] = (x, y); x += bw; rowh = max(rowh, bh)
    return pos

def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}

def write_models():
    out = {"sheet": [TW, TH], "pets": PM.PET_KIND, "kinds": {}}
    for name, k in PM.KINDS.items():
        lay = layout(k)
        d = {kk: vv for kk, vv in k.items() if kk != "pieces"}
        d["pieces"] = []
        for p in k["pieces"]:
            q = dict(p); q["uv"] = list(lay[p["share"]]); d["pieces"].append(q)
        out["kinds"][name] = d
    path = os.path.join(RES, "atenassocial", "pets")
    os.makedirs(path, exist_ok=True)
    with open(os.path.join(path, "models.json"), "w") as f:
        json.dump(out, f, separators=(",", ":"))
    if ASSET != "atenassocial":
        p2 = os.path.join(RES, ASSET, "pets"); os.makedirs(p2, exist_ok=True)
        with open(os.path.join(p2, "models.json"), "w") as f:
            json.dump(out, f, separators=(",", ":"))


# ------------------------------------------------------------------ utilidades de color
def mix(a, b, t): return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))
def sm(x): return x * x * (3 - 2 * x)
def U(v): return int(v * S * Q)

def blur_axis(n, k, axis):
    """Media movil de ancho k a lo largo de un eje (hebras de pelo)."""
    k = max(1, int(k))
    if k == 1: return n
    pad = [(0, 0), (0, 0)]; pad[axis] = (k // 2, k - k // 2 - 1)
    c = np.cumsum(np.pad(n, pad, mode="wrap"), axis=axis)
    zero = np.zeros_like(np.take(c, [0], axis=axis))
    c = np.concatenate([zero, c], axis=axis)
    sl_hi = [slice(None)] * 2; sl_lo = [slice(None)] * 2
    sl_hi[axis] = slice(k, None); sl_lo[axis] = slice(0, -k)
    r = (c[tuple(sl_hi)] - c[tuple(sl_lo)]) / k
    return r / (r.std() + 1e-6)

class Sheet:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (TW * S, TH * S), (0, 0, 0, 0))
        self.rng = np.random.default_rng(seed)

    def fur(self, rect, top, bot, flow="v", fine=1.0, light=1.0, edge=0.2, hair=0.10):
        """Rellena una cara con pelaje: degradado, hebras en la direccion del pelo, mechones, grano y sombra en los bordes."""
        x, y, w, h = rect; W, H = w * S, h * S
        ty = np.linspace(0, 1, H, dtype=np.float32)[:, None, None]
        base = np.array(top, np.float32) * (1 - ty) + np.array(bot, np.float32) * ty
        base = np.repeat(base, W, axis=1)
        axis = 0 if flow == "v" else 1
        strands = blur_axis(self.rng.normal(0, 1, (H, W)).astype(np.float32), S * 0.9 if axis == 0 else S * 0.9, axis)
        strands2 = blur_axis(self.rng.normal(0, 1, (H, W)).astype(np.float32), S * 0.35, axis)
        grain = self.rng.normal(0, 1, (H, W)).astype(np.float32)
        low = self.rng.normal(0, 1, (max(2, H // (S // 2)), max(2, W // (S // 2)))).astype(np.float32)
        clump = np.array(Image.fromarray(low).resize((W, H), Image.BICUBIC), np.float32)
        v = (strands * 0.55 + strands2 * 0.35 + grain * 0.10 * fine) * hair + clump * 0.035
        base = base * (1 + v[:, :, None])
        base[:, :, 0] += strands * 2.0; base[:, :, 2] -= strands * 1.5     # leve variacion calida
        if edge:
            yy = np.minimum(np.arange(H), H - 1 - np.arange(H))[:, None].astype(np.float32)
            xx = np.minimum(np.arange(W), W - 1 - np.arange(W))[None, :].astype(np.float32)
            e = np.clip(np.minimum(yy, xx) / (S * 0.9), 0, 1)
            base = base * (1 - edge + edge * sm(e))[:, :, None]
        base *= light
        arr = np.clip(base, 0, 255).astype(np.uint8)
        self.img.paste(Image.fromarray(arr, "RGB").convert("RGBA"), (x * S, y * S))

    def draw(self, rect):
        x, y, w, h = rect
        layer = Image.new("RGBA", (w * S * Q, h * S * Q), (0, 0, 0, 0))
        return layer, ImageDraw.Draw(layer), (x, y, w, h)

    def commit(self, layer, rect, soft=0.0):
        x, y, w, h = rect
        if soft: layer = layer.filter(ImageFilter.GaussianBlur(soft * S * Q))
        layer = layer.resize((w * S, h * S), Image.LANCZOS)
        self.img.alpha_composite(layer, (x * S, y * S))

LIGHT = {"top": 1.10, "bottom": 0.72, "front": 1.0, "back": 0.9, "right": 0.95, "left": 0.95}


# ------------------------------------------------------------------ pintura de una pieza
def paint_piece(sh, role, F, sp, rng, kind):
    """F = caras de la pieza. sp = receta de la mascota."""
    base, belly, dark = sp["base"], sp["belly"], sp["dark"]
    bird = kind["flier"] and sp.get("feathers", False)
    def fr(face, a, b, flow="v", **kw):
        sh.fur(F[face], a, b, flow=flow, light=LIGHT[face], **kw)
    side = "u"   # el pelo del costado corre hacia atras
    if role == "body":
        fr("top", dark, base); fr("bottom", belly, belly, edge=0.25)
        for s in ("right", "left"): fr(s, base, belly if sp.get("belly_side", True) else base, flow=side)
        fr("front", base, mix(base, dark, .35)); fr("back", base, mix(base, dark, .35))
    elif role in ("head",):
        for n in ("top", "right", "left", "back"): fr(n, mix(base, dark, .15) if n != "top" else dark, base, flow="v" if n in ("back",) else side)
        fr("bottom", belly, belly); fr("front", mix(base, dark, .08), base, hair=0.07)
    elif role == "neck":
        for n in ("top", "right", "left", "back"): fr(n, base, base)
        fr("bottom", belly, belly); fr("front", belly if sp.get("chest", True) else base, base)
    elif role == "snout":
        m = sp.get("muzzle", belly)
        for n in F: fr(n, m, m, hair=0.06)
    elif role == "jaw":
        m = sp.get("muzzle", belly)
        for n in F: fr(n, m, m, hair=0.05)
        sh.fur(F["top"], (206, 96, 112), (186, 78, 96), hair=0.03, light=1.0)   # interior de la boca
    elif role == "ear":
        for n in F: fr(n, sp["ear"], sp["ear"], hair=0.08)
    elif role in ("tail", "tailtip"):
        tip = sp.get("tail_tip", base)
        for n in F:
            if role == "tailtip": fr(n, mix(base, tip, .5), tip, flow="u" if n in ("right", "left") else "v")
            else: fr(n, base, sp.get("tail1_end", mix(base, tip, .25)), flow="u" if n in ("right", "left") else "v")
    elif role == "leg1":
        for n in F: fr(n, base, mix(base, sp.get("sock", base), .35))
    elif role == "leg2":
        for n in F: fr(n, mix(base, sp.get("sock", base), .35), sp.get("sock", base))
    elif role == "paw":
        sk = sp.get("sock", base)
        for n in F: fr(n, sk, mix(sk, dark, .25), edge=0.3)
        lay, d, (x, y, w, h) = sh.draw(F["front"])      # dedos
        for i in range(1, int(w)):
            d.line([(U(i * w / w - 0.0), U(h * .45)), (U(i), U(h))], fill=(20, 16, 16, 110), width=max(1, U(.08)))
        sh.commit(lay, F["front"])
        lay, d, (x, y, w, h) = sh.draw(F["bottom"])     # almohadillas
        d.ellipse([U(w * .2), U(h * .3), U(w * .8), U(h * .9)], fill=sp.get("pad", (70, 48, 48)) + (230,))
        sh.commit(lay, F["bottom"])
    # -------- aves / dragon
    elif role == "beak":
        c = sp.get("beak", (232, 190, 70))
        for n in F: sh.fur(F[n], c, mix(c, (20, 16, 14), .25), hair=0.04, light=LIGHT[n], edge=0.3)
    elif role == "beak2":
        c = sp.get("beak2", mix(sp.get("beak", (232, 190, 70)), (20, 16, 14), .3))
        for n in F: sh.fur(F[n], c, mix(c, (10, 8, 8), .2), hair=0.04, light=LIGHT[n], edge=0.3)
    elif role == "crest":
        c = sp.get("crest", sp["dark"])
        for n in F: fr(n, c, mix(c, base, .4), hair=0.14)
    elif role == "horn":
        c = sp.get("horn", (232, 222, 190))
        for n in F: sh.fur(F[n], mix(c, (60, 50, 40), .35), c, flow="v", hair=0.05, light=LIGHT[n], edge=0.25)
    elif role in ("wing1", "wing2"):
        c = sp.get(role, base) if role in sp else (base if role == "wing1" else dark)
        for n in F: fr(n, c, mix(c, (0, 0, 0), .18), hair=0.12, edge=0.15)
    if bird and role in ("body", "head", "neck", "wing1", "wing2", "tail", "tailtip"):
        scallops(sh, F, role, sp, rng)

def scallops(sh, F, role, sp, rng):
    """Plumas / escamas: filas de arcos que se solapan."""
    for n, rect in F.items():
        if n == "bottom": continue
        lay, d, (x, y, w, h) = sh.draw(rect)
        if role in ("wing1", "wing2", "tail", "tailtip"):       # barbas largas
            cols = max(3, int(w * 2.2))
            for i in range(cols):
                cx = (i + 0.5) / cols * w
                d.line([(U(cx), 0), (U(cx + rng.uniform(-.1, .1)), U(h))], fill=(0, 0, 0, 55), width=max(1, U(.05)))
            for j in range(int(h * 1.5)):
                yy = rng.uniform(0, h)
                d.line([(0, U(yy)), (U(w), U(yy + .25))], fill=(255, 255, 255, 18), width=max(1, U(.04)))
        else:
            sz = sp.get("scale_size", 0.85)
            rows = int(h / sz) + 2
            for r in range(rows):
                off = (sz / 2) if r % 2 else 0
                for c in range(int(w / sz) + 2):
                    cx, cy = c * sz + off, r * sz * 0.8
                    d.arc([U(cx - sz / 2), U(cy - sz / 2), U(cx + sz / 2), U(cy + sz / 2)], 0, 180, fill=(0, 0, 0, 70), width=max(1, U(.05)))
                    d.ellipse([U(cx - sz * .28), U(cy - sz * .25), U(cx + sz * .28), U(cy)], fill=(255, 255, 255, 14))
        sh.commit(lay, rect)


# ------------------------------------------------------------------ marcas por especie y cara
def marks(sh, kind, F, sp, rng):
    """F: {clave de textura: caras}. Aqui van manchas, rayas, antifaces y la cara (ojos, nariz, boca)."""
    pat = sp.get("pattern")
    body_faces = [("body", n) for n in ("top", "right", "left", "front", "back")] + [("head", n) for n in ("top", "right", "left", "back")]
    if pat in ("spots", "tabby", "bars"):
        for key, n in body_faces + [("neck", "right"), ("neck", "left"), ("neck", "top")]:
            if key not in F: continue
            lay, d, (x, y, w, h) = sh.draw(F[key][n])
            if pat == "spots":
                for _ in range(max(2, int(w * h) // 8)):
                    cx, cy, r = rng.uniform(0, w), rng.uniform(0, h), rng.uniform(.45, 1.15)
                    d.ellipse([U(cx - r), U(cy - r * .85), U(cx + r), U(cy + r * .85)], fill=sp["mark"] + (255,))
            elif pat == "tabby" and n in ("top", "right", "left"):
                i = 0.0
                while i < w + 1:
                    wob = rng.uniform(-.25, .25)
                    d.polygon([(U(i), 0), (U(i + .55), 0), (U(i + .45 + wob), U(h * .88)), (U(i + .05 + wob), U(h * .8))], fill=sp["mark"] + (200,))
                    i += rng.uniform(1.1, 1.6)
            elif pat == "bars":                                    # barrado del buho / plumas moteadas
                for r in range(int(h / .9) + 1):
                    for c in range(int(w / .9) + 1):
                        if rng.random() < .55:
                            cx, cy = c * .9 + rng.uniform(0, .4), r * .9
                            d.ellipse([U(cx), U(cy), U(cx + .55), U(cy + .45)], fill=sp["mark"] + (150,))
            sh.commit(lay, F[key][n], soft=0.015)
    hf = F["head"]["front"]
    if pat == "mask":     # husky
        lay, d, (x, y, w, h) = sh.draw(hf)
        B = sp["belly"] + (255,)
        d.rectangle([0, U(h * .58), U(w), U(h)], fill=B)
        d.polygon([(U(w * .5), U(h * .22)), (U(w * .3), U(h * .62)), (U(w * .7), U(h * .62))], fill=B)
        d.ellipse([U(w * .1), U(h * .2), U(w * .36), U(h * .5)], fill=B); d.ellipse([U(w * .64), U(h * .2), U(w * .9), U(h * .5)], fill=B)
        sh.commit(lay, hf, soft=0.04)
        lay, d, (x, y, w, h) = sh.draw(F["neck"]["front"]); d.rectangle([0, 0, U(w), U(h)], fill=B); sh.commit(lay, F["neck"]["front"], soft=0.05)
    if pat == "fox":
        lay, d, (x, y, w, h) = sh.draw(hf)
        d.polygon([(0, U(h * .55)), (U(w * .5), U(h * .42)), (U(w), U(h * .55)), (U(w), U(h)), (0, U(h))], fill=sp["belly"] + (255,))
        sh.commit(lay, hf, soft=0.04)
        for key in ("body", "neck"):
            lay, d, (x, y, w, h) = sh.draw(F[key]["front"]); d.rectangle([0, U(h * .3), U(w), U(h)], fill=sp["belly"] + (255,)); sh.commit(lay, F[key]["front"], soft=0.06)
        for n in ("front", "back"):
            lay, d, (x, y, w, h) = sh.draw(F["ear"][n]); d.rectangle([0, 0, U(w), U(h * .35)], fill=(25, 20, 20, 255)); sh.commit(lay, F["ear"][n], soft=0.04)
    if pat == "patches":    # Catalina
        M = sp["mark"] + (255,)
        def poly(rect, pts):
            lay, d, (x, y, w, h) = sh.draw(rect)
            d.polygon([(U(px * w), U(py * h)) for px, py in pts], fill=M); sh.commit(lay, rect, soft=0.03)
        poly(hf, [(0, 0), (.40, 0), (.37, .30), (.42, .62), (.30, .84), (.10, .80), (0, .70)])
        poly(hf, [(.62, 0), (1, 0), (1, .62), (.88, .74), (.70, .66), (.66, .36)])
        poly(F["head"]["top"], [(0, 0), (.38, 0), (.34, 1), (0, 1)]); poly(F["head"]["top"], [(.64, 0), (1, 0), (1, 1), (.68, 1)])
        poly(F["head"]["right"], [(0, 0), (1, 0), (1, .55), (.55, .70), (0, .50)]); poly(F["head"]["left"], [(0, 0), (1, 0), (1, .50), (.45, .66), (0, .55)])
        poly(F["head"]["back"], [(0, 0), (.36, 0), (.32, .60), (0, .50)]); poly(F["head"]["back"], [(.64, 0), (1, 0), (1, .50), (.68, .60)])
    if pat == "panda":
        M = sp["mark"] + (255,)
        lay, d, (x, y, w, h) = sh.draw(hf)
        for cx in (.27, .73):
            d.ellipse([U(w * (cx - .15)), U(h * .22), U(w * (cx + .15)), U(h * .62)], fill=M)
        sh.commit(lay, hf, soft=0.03)
        lay, d, (x, y, w, h) = sh.draw(F["body"]["front"]); d.rectangle([0, 0, U(w), U(h)], fill=M); sh.commit(lay, F["body"]["front"], soft=0.04)
        for n in ("right", "left"):                      # banda negra de los hombros
            lay, d, (x, y, w, h) = sh.draw(F["body"][n]); d.rectangle([0, 0, U(w * .30), U(h)], fill=M); sh.commit(lay, F["body"][n], soft=0.05)
        lay, d, (x, y, w, h) = sh.draw(F["body"]["top"]); d.rectangle([0, 0, U(w), U(h * .28)], fill=M); sh.commit(lay, F["body"]["top"], soft=0.05)
        for key in ("leg1", "leg2", "paw"):
            for n in F[key]:
                lay, d, (x, y, w, h) = sh.draw(F[key][n]); d.rectangle([0, 0, U(w), U(h)], fill=M); sh.commit(lay, F[key][n])
        for n in F["ear"]:
            lay, d, (x, y, w, h) = sh.draw(F["ear"][n]); d.rectangle([0, 0, U(w), U(h)], fill=M); sh.commit(lay, F["ear"][n])
    if pat == "owl":
        lay, d, (x, y, w, h) = sh.draw(hf)
        dc = sp["disc"] + (255,)
        d.ellipse([U(w * .02), U(h * .12), U(w * .52), U(h * .96)], fill=dc); d.ellipse([U(w * .48), U(h * .12), U(w * .98), U(h * .96)], fill=dc)
        sh.commit(lay, hf, soft=0.06)
        lay, d, (x, y, w, h) = sh.draw(F["body"]["front"]); d.ellipse([U(w * .1), U(h * .05), U(w * .9), U(h * 1.1)], fill=sp["belly"] + (255,))
        sh.commit(lay, F["body"]["front"], soft=0.08)
    if pat == "parrot":
        lay, d, (x, y, w, h) = sh.draw(hf)
        d.ellipse([U(w * .08), U(h * .25), U(w * .92), U(h * .95)], fill=sp["face"] + (255,)); sh.commit(lay, hf, soft=0.04)
        lay, d, (x, y, w, h) = sh.draw(F["tail"]["top"])
        d.rectangle([U(w * .3), 0, U(w * .7), U(h)], fill=sp["tail_mid"] + (255,)); sh.commit(lay, F["tail"]["top"], soft=0.05)
    if pat == "drake":
        lay, d, (x, y, w, h) = sh.draw(F["body"]["bottom"])
        for r in range(int(h / .7)): d.line([(0, U(r * .7)), (U(w), U(r * .7))], fill=(120, 90, 40, 140), width=max(1, U(.1)))
        sh.commit(lay, F["body"]["bottom"])
        for key in ("body", "neck"):
            for n in ("right", "left"):
                if n not in F[key]: continue
                lay, d, (x, y, w, h) = sh.draw(F[key][n])
                for r in range(int(w / .9)):
                    d.polygon([(U(r * .9), U(h * .15)), (U(r * .9 + .5), U(h * .15)), (U(r * .9 + .25), 0)], fill=sp["horn"] + (230,))
                sh.commit(lay, F[key][n])
    if pat == "rabbit":
        lay, d, (x, y, w, h) = sh.draw(hf)
        d.ellipse([U(w * .25), U(h * .45), U(w * .75), U(h * 1.05)], fill=sp["belly"] + (255,)); sh.commit(lay, hf, soft=0.05)
    # ---- cara
    lay, d, (x, y, w, h) = sh.draw(hf)
    big = sp.get("big_eyes", False)
    ey = h * (.42 if big else .40); ex = w * (.27 if big else .27); er = max(.55, w * (.17 if big else .105))
    for k, sx in enumerate((ex, w - ex)):
        d.ellipse([U(sx - er * 1.15), U(ey - er * 1.25), U(sx + er * 1.15), U(ey + er * 1.25)], fill=sp.get("lid", (22, 16, 18)) + (255,))
        d.ellipse([U(sx - er * .95), U(ey - er * 1.05), U(sx + er * .95), U(ey + er * 1.05)], fill=mix(sp["eye"], (0, 0, 0), .35) + (255,))
        d.ellipse([U(sx - er * .8), U(ey - er * .85), U(sx + er * .8), U(ey + er * .9)], fill=sp["eye"] + (255,))
        d.ellipse([U(sx - er * .8), U(ey + er * .1), U(sx + er * .8), U(ey + er * .9)], fill=mix(sp["eye"], (255, 255, 255), .25) + (255,))
        if sp.get("slit"):
            d.ellipse([U(sx - er * .16), U(ey - er * .9), U(sx + er * .16), U(ey + er * .9)], fill=(12, 10, 10, 255))
        else:
            d.ellipse([U(sx - er * .42), U(ey - er * .5), U(sx + er * .42), U(ey + er * .55)], fill=(8, 6, 8, 255))
        d.ellipse([U(sx - er * .55), U(ey - er * .7), U(sx - er * .05), U(ey - er * .2)], fill=(255, 255, 255, 255))
        d.ellipse([U(sx + er * .1), U(ey + er * .1), U(sx + er * .32), U(ey + er * .32)], fill=(255, 255, 255, 170))
    if kind["shape"] == "quad":
        d.arc([U(ex - er * 1.2), U(ey - er * 2.3), U(ex + er * 1.0), U(ey - er * 1.1)], 190, 350, fill=mix(sp["dark"], (0, 0, 0), .4) + (130,), width=max(1, U(.07)))
        d.arc([U(w - ex - er * 1.0), U(ey - er * 2.3), U(w - ex + er * 1.2), U(ey - er * 1.1)], 190, 350, fill=mix(sp["dark"], (0, 0, 0), .4) + (130,), width=max(1, U(.07)))
    if sp["model"] == "cat":
        d.polygon([(U(w * .42), U(h * .62)), (U(w * .58), U(h * .62)), (U(w * .5), U(h * .74))], fill=sp["nose"] + (255,))
        for yy in (.72, .82):
            d.line([(U(w * .62), U(h * yy)), (U(w * 1.0), U(h * (yy - .07)))], fill=(240, 240, 240, 190), width=max(1, U(.04)))
            d.line([(U(w * .38), U(h * yy)), (U(w * 0.0), U(h * (yy - .07)))], fill=(240, 240, 240, 190), width=max(1, U(.04)))
    sh.commit(lay, hf)
    for key, rr in (("snout", None),):
        if key in F:
            lay, d, (x, y, w, h) = sh.draw(F[key]["front"])
            d.ellipse([U(w * .2), U(h * .0), U(w * .8), U(h * .6)], fill=sp["nose"] + (255,))
            d.ellipse([U(w * .34), U(h * .08), U(w * .52), U(h * .26)], fill=(255, 255, 255, 120))
            d.line([(U(w * .5), U(h * .6)), (U(w * .5), U(h * .95))], fill=(40, 25, 25, 255), width=max(1, U(.06)))
            sh.commit(lay, F[key]["front"])
    if "jaw" in F:
        lay, d, (x, y, w, h) = sh.draw(F["jaw"]["top"]); d.rectangle([U(w * .3), U(h * .3), U(w * .7), U(h * .95)], fill=(238, 118, 134, 255)); sh.commit(lay, F["jaw"]["top"], soft=.05)
    if "ear" in F and kind["shape"] == "quad":
        lay, d, (x, y, w, h) = sh.draw(F["ear"]["front"])
        d.polygon([(U(w * .15), U(h)), (U(w * .85), U(h)), (U(w * .5), U(h * .2))], fill=sp["ear_in"] + (255,)); sh.commit(lay, F["ear"]["front"], soft=.04)


# ------------------------------------------------------------------ una mascota
def paint(pid, sp, seed):
    kname = sp["model"]; kind = PM.KINDS[kname]
    lay = layout(kind)
    sh = Sheet(seed); rng = random.Random(seed)
    sizes = {p["share"]: p["s"] for p in kind["pieces"]}
    roles = {p["share"]: p["role"] for p in kind["pieces"]}
    F = {key: faces(*lay[key], *sizes[key]) for key in sizes}
    for key in sizes: pass
    # indices por rol para marks(): claves de textura -> caras
    byrole = {}
    for key, role in roles.items():
        byrole.setdefault(role, F[key])
    for key in sizes:
        paint_piece(sh, roles[key], F[key], sp, rng, kind)
    FR = {"body": byrole["body"], "head": byrole["head"], "neck": byrole.get("neck", byrole["body"]), "ear": byrole.get("ear", byrole.get("horn", byrole["head"])),
          "leg1": byrole.get("leg1", byrole["body"]), "leg2": byrole.get("leg2", byrole["body"]), "paw": byrole.get("paw", byrole["body"]),
          "tail": byrole.get("tail", byrole["body"])}
    if "snout" in byrole: FR["snout"] = byrole["snout"]
    if "jaw" in byrole: FR["jaw"] = byrole["jaw"]
    marks(sh, kind, FR, sp, rng)
    sh.img.save(ROOT + "pets/%s.png" % pid, optimize=True)
    icon(pid, sh, FR, byrole, kind, sp)

def icon(pid, sh, FR, byrole, kind, sp):
    def crop(r): return sh.img.crop((r[0] * S, r[1] * S, (r[0] + r[2]) * S, (r[1] + r[3]) * S))
    BIG = 256; ic = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0))
    hf = FR["head"]["front"]; aspect = hf[3] / hf[2]
    fw = int(BIG * .66); fh = int(fw * aspect); fx, fy = (BIG - fw) // 2, int(BIG * .30)
    eared = byrole.get("ear") or byrole.get("horn")
    if eared is not None and kind["shape"] == "quad" or (eared is not None and kind["flier"]):
        e = crop(eared["back"]); ew = max(8, int(fw * .26)); eh = max(8, int(ew * e.height / max(1, e.width)))
        ic.alpha_composite(e.resize((ew, eh), Image.LANCZOS), (fx + 2, fy - eh + int(eh * .3)))
        ic.alpha_composite(e.transpose(Image.FLIP_LEFT_RIGHT).resize((ew, eh), Image.LANCZOS), (fx + fw - ew - 2, fy - eh + int(eh * .3)))
    ic.alpha_composite(crop(hf).resize((fw, fh), Image.LANCZOS), (fx, fy))
    mz = byrole.get("snout") or byrole.get("beak")
    if mz is not None:
        m = crop(mz["front"]); mw = int(fw * (.5 if "snout" in byrole else .3)); mh = max(6, int(mw * m.height / max(1, m.width)))
        ic.alpha_composite(m.resize((mw, mh), Image.LANCZOS), (fx + (fw - mw) // 2, fy + int(fh * .55)))
    ic = ic.crop(ic.getbbox()); k = 60 / max(ic.size)
    ic = ic.resize((max(1, int(ic.width * k)), max(1, int(ic.height * k))), Image.LANCZOS)
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); out.alpha_composite(ic, ((64 - ic.width) // 2, (64 - ic.height) // 2))
    out.save(ROOT + "icons/pet_%s.png" % pid)


PETS = {
    "perro": dict(model="dog", base=(205, 150, 84), belly=(240, 214, 160), dark=(170, 118, 62), ear=(150, 100, 52), ear_in=(224, 160, 150),
                  nose=(30, 22, 22), eye=(120, 70, 30), muzzle=(236, 208, 156), tail_tip=(240, 214, 160), sock=(236, 206, 150)),
    "husky": dict(model="husky", base=(104, 112, 126), belly=(244, 244, 248), dark=(60, 66, 80), ear=(70, 76, 90), ear_in=(210, 190, 200),
                  nose=(24, 22, 28), eye=(110, 190, 255), muzzle=(244, 244, 248), tail_tip=(240, 240, 246), pattern="mask", sock=(236, 236, 242)),
    "dalmata": dict(model="dog", base=(244, 244, 246), belly=(255, 255, 255), dark=(226, 226, 232), ear=(28, 28, 34), ear_in=(70, 60, 66),
                    nose=(20, 20, 24), eye=(110, 66, 30), muzzle=(255, 255, 255), tail_tip=(250, 250, 252), pattern="spots", mark=(30, 30, 36)),
    "gato": dict(model="cat", base=(226, 142, 62), belly=(252, 228, 190), dark=(196, 110, 40), ear=(206, 120, 48), ear_in=(240, 160, 160),
                 nose=(226, 120, 130), eye=(120, 224, 100), muzzle=(252, 228, 190), tail_tip=(190, 104, 36), pattern="tabby", mark=(150, 80, 26), sock=(250, 224, 188)),
    "gatonegro": dict(model="cat", base=(36, 36, 46), belly=(60, 60, 74), dark=(22, 22, 30), ear=(30, 30, 40), ear_in=(110, 70, 90),
                      nose=(200, 110, 130), eye=(232, 232, 70), muzzle=(54, 54, 66), tail_tip=(30, 30, 40), lid=(10, 10, 14)),
    "zorro": dict(model="fox", base=(232, 112, 40), belly=(250, 244, 236), dark=(196, 86, 24), ear=(224, 100, 34), ear_in=(250, 220, 200),
                  nose=(22, 18, 20), eye=(150, 90, 20), muzzle=(250, 244, 236), tail_tip=(252, 248, 240), pattern="fox", sock=(46, 34, 30)),
    "catalina": dict(model="cat", base=(247, 245, 243), belly=(255, 254, 252), dark=(238, 235, 232), ear=(40, 36, 40), ear_in=(150, 110, 116),
                     nose=(236, 150, 160), eye=(110, 200, 90), muzzle=(255, 254, 252), tail_tip=(48, 44, 48), tail1_end=(70, 64, 68),
                     pattern="patches", mark=(30, 27, 31), sock=(255, 253, 250)),
    # ---- nuevas
    "conejo": dict(model="rabbit", base=(206, 188, 168), belly=(250, 244, 236), dark=(170, 150, 132), ear=(196, 176, 158), ear_in=(240, 176, 186),
                   nose=(232, 132, 146), eye=(80, 44, 36), muzzle=(250, 244, 236), tail_tip=(255, 252, 248), pattern="rabbit", sock=(236, 224, 208)),
    "panda": dict(model="panda", base=(246, 246, 243), belly=(244, 244, 240), dark=(228, 228, 224), ear=(26, 26, 30), ear_in=(60, 56, 60),
                  nose=(22, 20, 24), eye=(60, 40, 30), muzzle=(250, 250, 246), tail_tip=(246, 246, 243), pattern="panda", mark=(26, 26, 30), sock=(26, 26, 30),
                  lid=(10, 10, 12)),
    "loro": dict(model="parrot", feathers=True, base=(216, 38, 38), belly=(236, 72, 52), dark=(168, 24, 30), ear=(200, 30, 30), ear_in=(0, 0, 0),
                 nose=(0, 0, 0), eye=(250, 210, 70), muzzle=(236, 230, 214), tail_tip=(246, 206, 60), pattern="parrot", face=(246, 242, 234),
                 tail_mid=(220, 40, 40), beak=(238, 232, 216), beak2=(52, 48, 52), wing1=(232, 196, 44), wing2=(36, 94, 214), crest=(200, 30, 30),
                 sock=(130, 124, 120), pad=(90, 84, 80), scale_size=0.8, big_eyes=True, lid=(40, 36, 40)),
    "buho": dict(model="owl", feathers=True, base=(138, 104, 72), belly=(232, 212, 176), dark=(92, 66, 44), ear=(100, 74, 50), ear_in=(0, 0, 0),
                 nose=(0, 0, 0), eye=(246, 190, 40), muzzle=(214, 180, 90), tail_tip=(200, 170, 130), pattern="owl", disc=(214, 184, 142), mark=(70, 48, 32),
                 beak=(214, 176, 70), beak2=(190, 150, 56), wing1=(122, 90, 60), wing2=(100, 72, 48), horn=(100, 74, 50),
                 sock=(210, 180, 120), pad=(80, 64, 48), big_eyes=True, scale_size=0.9, lid=(30, 22, 16)),
    "dragoncito": dict(model="drake", feathers=True, base=(66, 168, 108), belly=(242, 222, 152), dark=(36, 118, 78), ear=(230, 220, 184), ear_in=(0, 0, 0),
                       nose=(30, 70, 50), eye=(255, 206, 60), muzzle=(100, 190, 130), tail_tip=(36, 118, 78), pattern="drake", horn=(236, 226, 192),
                       wing1=(150, 58, 150), wing2=(190, 80, 140), sock=(50, 140, 90), pad=(40, 100, 70), slit=True, scale_size=0.7, big_eyes=True,
                       lid=(14, 40, 26), muzzle2=None),
}

if __name__ == "__main__":
    only = sys.argv[1:]
    write_models()
    for i, (n, spec) in enumerate(PETS.items()):
        if only and n not in only: continue
        paint(n, spec, 100 + i)
        print("ok", n)
    print("mascotas listas")
