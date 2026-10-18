"""Texturas (256x256) e iconos (32x32) de las alas de plano (WingPlane): membrana, llama, cristal, mariposa, hada, hoja, fantasma, sol, ciber, hueso, tormenta.
Uso:  python3 tools/gen_wings2.py     (Pillow + numpy)

Plano del ala (DEBE coincidir con WingPlane.java): x hacia afuera 0..36, y hacia arriba -12..24, raiz (hombro) en (0,0).
  u = x / 36 ;  v = (24 - y) / 36
La forma sale de la transparencia: Java solo dibuja un plano subdividido que ondula.
"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources", "assets")
MOD = next(d for d in os.listdir(RES))
ROOT = os.path.join(RES, MOD, "textures", "cosmetics") + os.sep
os.makedirs(ROOT + "wings", exist_ok=True); os.makedirs(ROOT + "icons", exist_ok=True)

S, SS = 256, 3
N = S * SS
K = N / 36.0
ys, xs = np.mgrid[0:N, 0:N]
XX = xs / K
YY = 24.0 - ys / K
RAD = np.sqrt(XX ** 2 + YY ** 2)

def P(p): return (p[0] * K, (24.0 - p[1]) * K)
def lerp(a, b, t): return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
def C(c): return np.array(c, dtype=np.float32)
def smooth(t): t = np.clip(t, 0, 1); return t * t * (3 - 2 * t)

class Cv:
    def __init__(self): self.img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    def poly_mask(self, pts):
        m = Image.new("L", (N, N), 0); ImageDraw.Draw(m).polygon([P(p) for p in pts], fill=255); return m
    def paint(self, mask, rgb, alpha=1.0):
        a = np.array(mask, dtype=np.float32) * alpha
        if callable(alpha): a = np.array(mask, dtype=np.float32) * alpha()
        lay = np.dstack([np.clip(rgb, 0, 255), np.clip(a, 0, 255)]).astype(np.uint8)
        self.img.alpha_composite(Image.fromarray(lay, "RGBA"))
    def fill(self, pts, c0, c1, r0=0.0, r1=28.0, alpha=1.0, grad="rad", axis=None):
        m = self.poly_mask(pts)
        t = smooth(((RAD if grad == "rad" else (XX if grad == "x" else YY)) - r0) / (r1 - r0))
        rgb = C(c0)[None, None, :] * (1 - t[..., None]) + C(c1)[None, None, :] * t[..., None]
        self.paint(m, rgb, alpha); return m
    def solid(self, pts, c, alpha=1.0):
        m = self.poly_mask(pts); self.paint(m, np.zeros((N, N, 3), np.float32) + C(c), alpha); return m
    def edge(self, mask, c, w=0.7, alpha=1.0):
        k = max(1, int(w * K)) * 2 + 1
        er = mask.filter(ImageFilter.MinFilter(k))
        ed = Image.fromarray(np.clip(np.array(mask, np.int16) - np.array(er, np.int16), 0, 255).astype(np.uint8), "L")
        self.paint(ed, np.zeros((N, N, 3), np.float32) + C(c), alpha)
    def line(self, pts, c, w=0.6, alpha=1.0):
        m = Image.new("L", (N, N), 0); d = ImageDraw.Draw(m)
        q = [P(p) for p in pts]; d.line(q, fill=255, width=max(1, int(w * K)), joint="curve")
        r = w * K / 2
        for x, y in (q[0], q[-1]): d.ellipse([x - r, y - r, x + r, y + r], fill=255)
        self.paint(m, np.zeros((N, N, 3), np.float32) + C(c), alpha)
    def dot(self, p, r, c, alpha=1.0):
        m = Image.new("L", (N, N), 0); x, y = P(p); ImageDraw.Draw(m).ellipse([x - r * K, y - r * K, x + r * K, y + r * K], fill=255)
        self.paint(m, np.zeros((N, N, 3), np.float32) + C(c), alpha)
    def glow(self, c, blur=1.6, amt=0.55):
        a = self.img.split()[3].filter(ImageFilter.GaussianBlur(blur * K))
        g = Image.new("RGBA", (N, N), tuple(int(v) for v in c) + (0,)); g.putalpha(a.point(lambda v: int(v * amt)))
        out = Image.new("RGBA", (N, N), (0, 0, 0, 0)); out.alpha_composite(g); out.alpha_composite(self.img); self.img = out
    def finish(self):
        im = self.img.resize((S, S), Image.LANCZOS)
        a = np.array(im, np.float32); al = a[..., 3:4] / 255.0
        # relleno de color en los bordes transparentes (evita halos oscuros al filtrar la textura)
        pre = Image.fromarray(np.clip(a[..., :3] * al, 0, 255).astype(np.uint8), "RGB").filter(ImageFilter.GaussianBlur(3))
        ab = Image.fromarray(np.clip(al[..., 0] * 255, 0, 255).astype(np.uint8), "L").filter(ImageFilter.GaussianBlur(3))
        pb = np.array(pre, np.float32); abn = np.maximum(np.array(ab, np.float32)[..., None] / 255.0, 1e-3)
        fillc = np.clip(pb / abn, 0, 255)
        rgb = np.where(al > 0.5, a[..., :3], fillc * (1 - al) + a[..., :3] * al)
        return Image.fromarray(np.dstack([rgb, a[..., 3]]).astype(np.uint8), "RGBA")

def catmull(pts, n=8, closed=True):
    out = []; m = len(pts)
    for i in range(m if closed else m - 1):
        p0, p1, p2, p3 = pts[(i - 1) % m], pts[i], pts[(i + 1) % m], pts[(i + 2) % m]
        for k in range(n):
            t = k / n
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t ** 3) for j in (0, 1)))
    return out

def ellipse(c, a, b, ang, n=40):
    return [(c[0] + a * math.cos(t) * math.cos(ang) - b * math.sin(t) * math.sin(ang), c[1] + a * math.cos(t) * math.sin(ang) + b * math.sin(t) * math.cos(ang)) for t in [2 * math.pi * i / n for i in range(n)]]

def blade(start, ang, L, w, curve=0.0, n=14, tip=0.0):
    """Pluma/llama: tira que se afina, con curvatura. Devuelve (poligono, espina)."""
    sp = []; left = []; right = []
    for i in range(n + 1):
        t = i / n
        a = ang + curve * t
        if i == 0: x, y = start
        else: x += math.cos(pa) * L / n; y += math.sin(pa) * L / n
        pa = a
        sp.append((x, y))
    for i, (x, y) in enumerate(sp):
        t = i / n
        a = ang + curve * t
        ww = w * math.sin(math.pi * min(1, t * 0.9 + 0.08)) ** 0.7 * (1 - 0.5 * t) if t < 1 else 0
        if tip and i == n: ww = 0
        nx, ny = -math.sin(a), math.cos(a)
        left.append((x + nx * ww, y + ny * ww)); right.append((x - nx * ww, y - ny * ww))
    return left + right[::-1], sp

def spike(p, ang, L, w):
    return [(p[0] + math.cos(ang + math.pi / 2) * w, p[1] + math.sin(ang + math.pi / 2) * w), (p[0] + math.cos(ang) * L, p[1] + math.sin(ang) * L),
            (p[0] + math.cos(ang - math.pi / 2) * w, p[1] + math.sin(ang - math.pi / 2) * w)]

def bone_path(a, b, bend, n=10):
    mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    dx, dy = b[0] - a[0], b[1] - a[1]; l = math.hypot(dx, dy) or 1
    cx, cy = mx - dy / l * bend, my + dx / l * bend
    return [((1 - t) ** 2 * a[0] + 2 * (1 - t) * t * cx + t * t * b[0], (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * cy + t * t * b[1]) for t in [i / n for i in range(n + 1)]]

# ------------------------------------------------------------------ formas

def bat(cv, pal, fingers, dragon=False):
    root = (0.0, 0.0); wr = (10.0, 12.0)
    angs, lens = fingers
    tips = [(wr[0] + l * math.cos(a), wr[1] + l * math.sin(a)) for a, l in zip(angs, lens)]
    body = (2.5, -10.5)
    outline = [root, wr]
    seq = tips + [body]
    outline.append(tips[0])
    for i in range(len(seq) - 1):
        a, b = seq[i], seq[i + 1]
        sag = 0.30 if i < len(seq) - 2 else 0.14
        for k in range(1, 9):
            t = k / 8; q = lerp(a, b, t)
            f = sag * math.sin(math.pi * t); outline.append(lerp(q, wr, f))
    m = cv.fill(outline, pal["root"], pal["edge"], 3, 30, alpha=pal.get("alpha", 1.0))
    # venas radiales y borde luminoso
    for i in range(len(seq) - 1):
        mid = lerp(seq[i], seq[i + 1], 0.5); mid = lerp(mid, wr, 0.30 * 0.9)
        cv.line([wr, lerp(wr, mid, 0.55), mid], pal["vein"], 0.35, 0.55)
    cv.edge(m, pal["glow"], 0.55, 0.85)
    if dragon:  # escamas en la membrana
        rnd = random.Random(3)
        for _ in range(70):
            x, y = rnd.uniform(3, 30), rnd.uniform(-8, 22)
            if m.getpixel((int(P((x, y))[0]), int(P((x, y))[1]))) > 200: cv.line([(x - .8, y), (x, y - .6), (x + .8, y)], pal["vein"], 0.18, 0.5)
    # huesos curvos: brazo + dedos
    cv.line(bone_path(root, wr, 1.2), pal["bone"], 1.7); cv.line(bone_path(root, wr, 1.2), pal["boneHi"], 0.5, 0.7)
    for i, t in enumerate(tips):
        bp = bone_path(wr, t, 1.6 if i % 2 == 0 else -1.2)
        cv.line(bp, pal["bone"], 1.15); cv.line(bp, pal["boneHi"], 0.3, 0.6)
        cv.solid(spike(t, angs[i], 2.4 if dragon else 1.6, 0.55), pal["bone"])
    cv.dot(wr, 1.3, pal["bone"]); cv.dot(wr, 0.6, pal["boneHi"])
    cv.solid(spike(wr, 1.45, 5.0 if dragon else 3.6, 0.9), pal["bone"])   # garra del pulgar

def phoenix(cv, pal):
    sets = [(7, 1.0, pal["root"], pal["edge"]), (7, 0.62, pal["core"], pal["hot"])]
    for si, (n, sc, c0, c1) in enumerate(sets):
        for i in range(n):
            ang = 1.30 - i * 0.28
            L = [17, 22, 25, 26, 24, 20, 14][i] * sc + (1 if si else 0)
            poly, sp = blade((1.0 + i * 0.5, 0.5 + 0.4 * (3 - abs(3 - i))), ang, L, 3.4 * sc + 0.6, curve=0.55 - i * 0.04, n=18, tip=1)
            m = cv.fill(poly, c0, c1, 0, 28)
            if si == 0: cv.edge(m, pal["glow"], 0.35, 0.6)
    cv.glow(pal["glow"], 1.4, 0.5)

def crystal(cv, pal):
    shards = [(1.25, 17, 3.6), (0.95, 23, 4.2), (0.62, 27, 4.6), (0.28, 26, 4.4), (-0.05, 22, 4.0), (-0.38, 17, 3.4), (-0.7, 11, 2.8)]
    for i, (a, L, w) in enumerate(shards):
        b = (1.0 + 0.5 * i, 0.5)
        tip = (b[0] + math.cos(a) * L, b[1] + math.sin(a) * L)
        mid = lerp(b, tip, 0.38); nx, ny = -math.sin(a), math.cos(a)
        ml = (mid[0] + nx * w, mid[1] + ny * w); mr = (mid[0] - nx * w, mid[1] - ny * w)
        bl = (b[0] + nx * w * 0.35, b[1] + ny * w * 0.35); br = (b[0] - nx * w * 0.35, b[1] - ny * w * 0.35)
        m1 = cv.fill([bl, ml, tip, b], pal["lo"], pal["hi"], 0, 30)
        m2 = cv.fill([b, tip, mr, br], pal["root"], pal["lo"], 0, 30)
        cv.line([b, tip], pal["spark"], 0.28, 0.8)
        cv.edge(cv.poly_mask([bl, ml, tip, mr, br]), pal["spark"], 0.4, 0.9)
    rnd = random.Random(7)
    for _ in range(9):
        x, y = rnd.uniform(6, 28), rnd.uniform(0, 18); cv.line([(x - 1.2, y), (x + 1.2, y)], (255, 255, 255), 0.22); cv.line([(x, y - 1.2), (x, y + 1.2)], (255, 255, 255), 0.22)
    cv.glow(pal["glow"], 1.2, 0.45)

def butterfly(cv, pal, pattern):
    up = catmull([(0, 2), (5, 11), (13, 20), (23, 23.2), (31, 20), (34.3, 13), (30, 6.5), (21, 2), (9, 0)])
    lo = catmull([(0, -1), (9, -1.5), (19, -3.5), (25, -8), (22.5, -11.6), (15, -11.2), (8, -8), (3, -4.5)])
    for pts in (lo, up):
        m = cv.fill(pts, pal["root"], pal["edge"], 2, 32)
        if pattern == "monarch": cv.edge(m, (20, 14, 22), 1.6, 1.0)
        else: cv.edge(m, pal["glow"], 0.6, 0.9)
        for i in range(9):
            a = 0.1 + i * 0.16 if pts is up else -0.1 - i * 0.12
            end = (34 * math.cos(a) * 0.95, 20 * math.sin(a * 1.1) * (1 if pts is up else 0.55))
            cv.line([(0.5, 0.5), lerp((0.5, 0.5), end, 0.6), lerp((0.5, 0.5), end, 0.9)], pal["vein"], 0.3, 0.7)
    rnd = random.Random(11)
    if pattern == "monarch":
        for p in [(27, 15.5), (30.5, 12.5), (24, 19), (28, 8.5), (21, 5), (19, -7), (14.5, -9.3)]: cv.dot(p, 0.8, (255, 255, 255))
    else:
        for _ in range(60):
            x, y = rnd.uniform(2, 33), rnd.uniform(-10, 22)
            px, py = P((x, y))
            if cv.img.getpixel((int(px), int(py)))[3] > 200: cv.dot((x, y), rnd.choice([0.18, 0.25, 0.4]), (255, 255, 255), 0.9)
        for p in [(24, 17), (14, 12), (28, 9), (19, -6)]: cv.dot(p, 1.0, pal["glow"], 0.35)
    cv.line(bone_path((0, 0), (14, 11), 0.5), pal["bone"], 1.0)
    cv.glow(pal["glow"], 1.3, 0.4)

def fairy(cv, pal):
    for c, a, b, ang in [((15.5, 13.5), 16.5, 5.2, 0.62), ((12, -1.5), 12.5, 3.9, -0.42), ((10, 6), 13, 4, 0.18)]:
        pts = ellipse(c, a, b, ang)
        m = cv.fill(pts, pal["root"], pal["edge"], 0, 32, alpha=0.9)
        ca, sa = math.cos(ang), math.sin(ang)
        for k in range(-3, 4):  # celdas
            u = k / 3.6 * a
            cv.line([(c[0] + u * ca - b * 0.95 * math.sqrt(max(0, 1 - (u / a) ** 2)) * sa, c[1] + u * sa + b * 0.95 * math.sqrt(max(0, 1 - (u / a) ** 2)) * ca),
                     (c[0] + u * ca + b * 0.95 * math.sqrt(max(0, 1 - (u / a) ** 2)) * sa, c[1] + u * sa - b * 0.95 * math.sqrt(max(0, 1 - (u / a) ** 2)) * ca)], pal["vein"], 0.16, 0.5)
        cv.line([(c[0] - a * ca, c[1] - a * sa), (c[0] + a * ca, c[1] + a * sa)], pal["vein"], 0.35, 0.8)
        cv.edge(m, pal["glow"], 0.45, 0.9)
    cv.glow(pal["glow"], 1.4, 0.5)

def leaf(cv, pal):
    for a, L, w in [(1.05, 15, 5.0), (0.62, 22, 6.2), (0.2, 24, 6.4), (-0.22, 20, 5.6), (-0.6, 14, 4.4)]:
        base = (1.5, 0.5); poly, sp = blade(base, a, L, w, curve=0.35, n=16, tip=1)
        m = cv.fill(poly, pal["root"], pal["edge"], 0, 28)
        cv.edge(m, pal["glow"], 0.4, 0.8)
        cv.line(sp, pal["vein"], 0.45, 0.9)
        for i in range(2, 14, 2):
            p = sp[i]; ang = a + 0.35 * i / 16
            for s_ in (1, -1):
                q = (p[0] + math.cos(ang + s_ * 0.95) * w * 0.7 * (1 - i / 17), p[1] + math.sin(ang + s_ * 0.95) * w * 0.7 * (1 - i / 17))
                cv.line([p, q], pal["vein"], 0.2, 0.6)
    cv.glow(pal["glow"], 1.0, 0.3)

def ghost(cv, pal):
    for i, (a, L, w) in enumerate([(1.2, 15, 3.2), (0.95, 21, 3.8), (0.65, 25, 4.2), (0.35, 27, 4.4), (0.05, 26, 4.2), (-0.25, 22, 3.8), (-0.55, 17, 3.2)]):
        poly, sp = blade((1.0, 0.5), a, L, w, curve=0.5 - 0.1 * i, n=20, tip=1)
        # borde roto
        rag = []
        for j, (x, y) in enumerate(poly): rag.append((x + 0.35 * math.sin(j * 2.3 + i), y + 0.35 * math.cos(j * 1.9)))
        m = cv.fill(rag, pal["root"], pal["edge"], 0, 28, alpha=0.95)
        cv.line(sp[2:-3], pal["vein"], 0.22, 0.5)
        cv.edge(m, pal["glow"], 0.35, 0.6)
    cv.glow(pal["glow"], 1.6, 0.5)

def sun(cv, pal):
    hub = (2.0, 2.0)
    n = 13
    for i in range(n):
        a = 1.5 - i * (2.25 / (n - 1)); L = 27 if i % 2 == 0 else 19
        w = 0.17 if i % 2 == 0 else 0.15
        tip = (hub[0] + math.cos(a) * L, hub[1] + math.sin(a) * L)
        l = (hub[0] + math.cos(a + w) * 6, hub[1] + math.sin(a + w) * 6); r = (hub[0] + math.cos(a - w) * 6, hub[1] + math.sin(a - w) * 6)
        m = cv.fill([hub, l, tip, r], pal["root"], pal["edge"], 2, 28)
        cv.edge(m, pal["glow"], 0.3, 0.8)
    for rr in (9, 14, 19):
        pts = [(hub[0] + rr * math.cos(t), hub[1] + rr * math.sin(t)) for t in [-0.8 + k * 0.05 for k in range(0, 48)]]
        cv.line(pts, pal["bone"], 0.45, 0.8)
    cv.dot(hub, 3.6, pal["bone"]); cv.dot(hub, 2.3, pal["glow"])
    cv.glow(pal["glow"], 1.5, 0.55)

def cyber(cv, pal):
    for i, (a, L, w) in enumerate([(1.2, 15, 3.0), (0.9, 22, 3.6), (0.58, 26, 3.8), (0.27, 27, 3.8), (-0.04, 24, 3.6), (-0.34, 19, 3.2)]):
        b = (1.0 + 0.4 * i, 0.5); ca, sa = math.cos(a), math.sin(a); nx, ny = -sa, ca
        def pt(t, s_): return (b[0] + ca * L * t + nx * w * s_, b[1] + sa * L * t + ny * w * s_)
        poly = [pt(0, 0.6), pt(0.15, 1), pt(0.86, 1), pt(1, 0.1), pt(0.9, -1), pt(0.15, -1), pt(0, -0.6)]
        m = cv.fill(poly, pal["root"], pal["edge"], 0, 30)
        cv.edge(m, pal["glow"], 0.4, 1.0)
        cv.line([pt(0.08, 0), pt(0.8, 0.0)], pal["glow"], 0.3, 0.95)
        for t in (0.3, 0.5, 0.7): cv.line([pt(t, -0.6), pt(t + 0.05, 0.6)], pal["vein"], 0.25, 0.9)
        cv.solid([pt(0.88, 0.5), pt(0.98, 0.1), pt(0.88, -0.4)], pal["glow"])
    cv.glow(pal["glow"], 1.1, 0.5)

def bone(cv, pal):
    wr = (10.5, 14.0)
    angs = [0.9, 0.5, 0.1, -0.3, -0.68]; lens = [11, 19, 22, 19, 13]
    tips = [(wr[0] + l * math.cos(a), wr[1] + l * math.sin(a)) for a, l in zip(angs, lens)]
    seq = tips + [(3, -9)]
    mem = [(0, 0), wr, tips[0]]
    for i in range(len(seq) - 1):
        for k in range(1, 9):
            tt = k / 8; q = lerp(seq[i], seq[i + 1], tt); mem.append(lerp(q, wr, 0.34 * math.sin(math.pi * tt)))
    cv.fill(mem, pal["root"], pal["edge"], 3, 30, alpha=0.5)
    cv.line(bone_path((0, 0), wr, 1.4), pal["bone"], 1.8); cv.line(bone_path((0, 0), wr, 1.4), pal["boneHi"], 0.5, 0.8)
    for i, t in enumerate(tips):
        bp = bone_path(wr, t, 1.8 if i % 2 == 0 else -1.4)
        cv.line(bp, pal["bone"], 1.15); cv.line(bp, pal["boneHi"], 0.3, 0.8)
        for j in range(2, 9, 3): cv.dot(bp[j], 0.7, pal["bone"])
        cv.solid(spike(t, angs[i], 2.2, 0.5), pal["bone"])
    cv.dot(wr, 1.6, pal["bone"]); cv.dot(wr, 0.7, pal["boneHi"])
    for i in range(len(tips) - 1):  # costillas entre dedos
        cv.line([lerp(wr, tips[i], 0.62), lerp(wr, tips[i + 1], 0.62)], pal["vein"], 0.3, 0.8)
    cv.glow(pal["glow"], 1.0, 0.25)

def storm(cv, pal):
    for i, (a, L, w) in enumerate([(1.25, 16, 3.4), (0.92, 23, 4.0), (0.58, 27, 4.4), (0.24, 28, 4.4), (-0.1, 24, 4.0), (-0.42, 17, 3.2)]):
        b = (1.0 + 0.4 * i, 0.5); ca, sa = math.cos(a), math.sin(a); nx, ny = -sa, ca
        pts_l, pts_r = [], []
        for k in range(0, 8):
            t = k / 7; z = (1 if k % 2 else -1) * 0.9 * (1 - t * 0.5)
            ww = w * (1 - t) ** 0.55 * (0.75 + 0.25 * (k % 2))
            pts_l.append((b[0] + ca * L * t + nx * (ww + z), b[1] + sa * L * t + ny * (ww + z)))
            pts_r.append((b[0] + ca * L * t - nx * (ww - z), b[1] + sa * L * t - ny * (ww - z)))
        m = cv.fill(pts_l + pts_r[::-1], pal["root"], pal["edge"], 0, 30)
        cv.edge(m, pal["glow"], 0.35, 0.95)
        zz = [(b[0] + ca * L * k / 7 + nx * (0.8 if k % 2 else -0.8), b[1] + sa * L * k / 7 + ny * (0.8 if k % 2 else -0.8)) for k in range(0, 7)]
        cv.line(zz, pal["spark"], 0.3, 0.95)
    cv.glow(pal["glow"], 1.6, 0.6)

# ------------------------------------------------------------------ paletas

def pal(root, edge, vein, glow, bone="auto", boneHi=None, **kw):
    d = dict(root=root, edge=edge, vein=vein, glow=glow, bone=bone if bone != "auto" else vein, boneHi=boneHi or edge)
    d.update(kw); return d

BAT4 = ([0.95, 0.52, 0.06, -0.42], [13, 21.5, 24, 18])
BAT5 = ([1.0, 0.7, 0.34, 0.0, -0.36], [12.5, 19, 24.5, 23, 17])
DRG5 = ([1.05, 0.72, 0.36, 0.02, -0.34], [12.5, 20.5, 25.5, 24, 18])
W = {
 "murcielago": (lambda cv, p: bat(cv, p, BAT4), pal((50, 34, 86), (150, 112, 214), (24, 14, 44), (200, 160, 255), (28, 20, 40), (120, 96, 160))),
 "vampiro":    (lambda cv, p: bat(cv, p, BAT5), pal((70, 6, 24), (205, 28, 58), (30, 2, 10), (255, 110, 130), (18, 8, 12), (230, 190, 90))),
 "dragon":     (lambda cv, p: bat(cv, p, DRG5, True), pal((140, 26, 16), (255, 150, 40), (80, 12, 8), (255, 220, 100), (60, 24, 14), (255, 200, 110))),
 "abismo":     (fairy, pal((6, 70, 110), (60, 235, 225), (180, 255, 250), (80, 255, 235))),
 "fantasma":   (ghost, pal((150, 185, 215), (245, 252, 255), (110, 140, 175), (230, 250, 255))),
 "jade":       (leaf, pal((12, 88, 60), (110, 230, 160), (6, 52, 36), (170, 255, 205))),
 "solar":      (sun, pal((225, 130, 20), (255, 232, 120), (150, 80, 10), (255, 250, 190), (140, 80, 10))),
 "ceniza":     (phoenix, pal((40, 38, 46), (120, 116, 126), (60, 58, 66), (255, 120, 40), core=(255, 120, 30), hot=(255, 220, 120))),
 "cristal":    (crystal, pal((90, 150, 205), (200, 245, 255), (60, 110, 170), (160, 235, 255), lo=(150, 215, 245), hi=(240, 253, 255), spark=(255, 255, 255))),
 "nebulosa":   (lambda cv, p: butterfly(cv, p, "galaxy"), pal((30, 14, 80), (170, 80, 230), (90, 40, 160), (190, 130, 255), (20, 10, 50))),
 "mariposa":   (lambda cv, p: butterfly(cv, p, "monarch"), pal((235, 110, 10), (255, 170, 40), (30, 18, 20), (255, 200, 90), (30, 18, 20))),
 "hada":       (fairy, pal((255, 150, 210), (255, 245, 255), (255, 255, 255), (255, 200, 245))),
 "fenix":      (phoenix, pal((200, 30, 10), (255, 150, 20), (0, 0, 0), (255, 170, 50), core=(255, 160, 20), hot=(255, 245, 160))),
 "tormenta":   (storm, pal((14, 20, 70), (80, 120, 230), (0, 0, 0), (120, 190, 255), spark=(255, 245, 140))),
 "cibernetico": (cyber, pal((16, 20, 40), (40, 52, 98), (0, 220, 255), (0, 240, 255))),
 "esqueleto":  (bone, pal((120, 120, 130), (210, 205, 195), (140, 135, 125), (180, 255, 200), (222, 216, 200), (255, 252, 240))),
}

def icon(tex):
    pair = Image.new("RGBA", (tex.width * 2, tex.height), (0, 0, 0, 0))
    pair.alpha_composite(tex.transpose(Image.FLIP_LEFT_RIGHT), (0, 0)); pair.alpha_composite(tex, (tex.width, 0))
    box = pair.getbbox() or (0, 0, pair.width, pair.height)
    pair = pair.crop(box)
    sc = min(30 / pair.width, 30 / pair.height)
    pair = pair.resize((max(1, int(pair.width * sc)), max(1, int(pair.height * sc))), Image.LANCZOS)
    ic = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); ic.alpha_composite(pair, ((32 - pair.width) // 2, (32 - pair.height) // 2)); return ic

if __name__ == "__main__":
    import sys
    only = sys.argv[1:]
    for name, (fn, p) in W.items():
        if only and name not in only: continue
        cv = Cv(); fn(cv, p); tex = cv.finish()
        tex.save(ROOT + "wings/%s.png" % name); icon(tex).save(ROOT + "icons/wings_%s.png" % name)
        print("ok", name)
