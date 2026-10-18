#!/usr/bin/env python3
"""Motor de mallas y texturas para los sombreros de Atenas (sin dependencias de Minecraft).
Coordenadas de AUTORIA: origen en el centro de la base de la cabeza, y arriba, z hacia adelante (la cara), x a la derecha.
La cabeza ocupa y 0..8, x -4..4, z -4..4. Al exportar se pasa al sistema de ModelRenderer (y -> -y, z -> -z).
Formato .atm (big endian): int 'ATM1', int grupos; por grupo: UTF nombre, byte flags, int quads, 32 floats por quad
(4 vertices x [x y z u v nx ny nz]), posiciones en unidades de pixel (1/16 de bloque) ya en el sistema de ModelRenderer.
Flags: bit0 = emisivo (sin luz, aditivo), bit1 = pulso (el brillo late), bit2 = gira sobre Y, bit3 = doble capa translucida (brillo suave)."""
import math, struct
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

F_EMIT, F_PULSE, F_SPIN, F_SOFT = 1, 2, 4, 8
rng = np.random.RandomState(7)

# ----------------------------------------------------------------------------- utilidades
def norm(v):
    n = np.linalg.norm(v, axis=-1, keepdims=True)
    return v / np.maximum(n, 1e-9)

def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)

def lerp(a, b, t):
    return a + (b - a) * t

def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32) / 255.0

def fbm(w, h, scale=8, octaves=4, seed=0, tile_x=True, persistence=0.5):
    """Ruido de valor fractal en [0,1], repetible en horizontal."""
    r = np.random.RandomState(seed)
    out = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        gw = max(2, int(scale * (2 ** o)))
        gh = max(2, int(scale * (2 ** o) * h / max(w, 1)))
        g = r.rand(gh, gw).astype(np.float32)
        big = np.tile(g, (1, 3)) if tile_x else g
        im = Image.fromarray(big, 'F').resize((w * 3 if tile_x else w, h), Image.BICUBIC)
        a = np.asarray(im)
        if tile_x: a = a[:, w:2 * w]
        out += a * amp
        tot += amp
        amp *= persistence
    out /= tot
    return np.clip(out, 0, 1)

def to_img(arr):
    return Image.fromarray((np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA')

def from_img(im):
    return np.asarray(im.convert('RGBA'), np.float32) / 255.0

def over(base, top):
    """Compone top (RGBA float) sobre base (RGBA float)."""
    ta = top[..., 3:4]
    ba = base[..., 3:4]
    oa = ta + ba * (1 - ta)
    oc = (top[..., :3] * ta + base[..., :3] * ba * (1 - ta)) / np.maximum(oa, 1e-6)
    return np.concatenate([oc, oa], -1)

class Canvas:
    """Lienzo para pintar una pieza (supermuestreado x2 para dibujar formas con borde suave)."""
    def __init__(self, w, h, arr=None, ss=2):
        self.w, self.h, self.ss = w, h, ss
        self.arr = arr if arr is not None else np.zeros((h, w, 4), np.float32)

    def draw(self, fn, blur=0):
        """fn(ImageDraw, S) dibuja en una capa RGBA (S = factor de escala) que se compone encima."""
        S = self.ss
        im = Image.new('RGBA', (self.w * S, self.h * S), (0, 0, 0, 0))
        fn(ImageDraw.Draw(im), S)
        im = im.resize((self.w, self.h), Image.LANCZOS)
        if blur: im = im.filter(ImageFilter.GaussianBlur(blur))
        self.arr = over(self.arr, from_img(im))
        return self

    def add(self, layer):
        self.arr = over(self.arr, layer)
        return self

    def glow(self, x, y, r, color, a=1.0, shape=2.0):
        yy, xx = np.mgrid[0:self.h, 0:self.w].astype(np.float32)
        d = np.sqrt((xx - x) ** 2 + (yy - y) ** 2) / r
        al = np.clip(1 - d, 0, 1) ** shape * a
        lay = np.zeros((self.h, self.w, 4), np.float32)
        lay[..., :3] = color
        lay[..., 3] = al
        self.arr = over(self.arr, lay)
        return self

# ----------------------------------------------------------------------------- atlas
class Atlas:
    def __init__(self, size=1024):
        self.size = size
        self.arr = np.zeros((size, size, 4), np.float32)
        self.x = 0; self.y = 0; self.rowh = 0
        self.used = []

    def alloc(self, w, h, pad=4):
        w, h = int(w), int(h)
        W, H = w + 2 * pad, h + 2 * pad
        if self.x + W > self.size:
            self.x = 0; self.y += self.rowh; self.rowh = 0
        if self.y + H > self.size:
            raise RuntimeError('atlas lleno (%d,%d)' % (w, h))
        r = (self.x + pad, self.y + pad, w, h)
        self.x += W; self.rowh = max(self.rowh, H)
        return r

    def paste(self, rect, arr, pad=4, wrap_x=False):
        x, y, w, h = rect
        assert arr.shape[0] == h and arr.shape[1] == w, (arr.shape, rect)
        p = np.pad(arr, ((pad, pad), (0, 0), (0, 0)), mode='edge')
        p = np.pad(p, ((0, 0), (pad, pad), (0, 0)), mode='wrap' if wrap_x else 'edge')
        self.arr[y - pad:y + h + pad, x - pad:x + w + pad] = p

    def image(self):
        return to_img(self.arr)

# ----------------------------------------------------------------------------- geometria
class Part:
    def __init__(self, name, flags=0):
        self.name, self.flags, self.quads = name, flags, []   # quads: lista de (4,8)

class Model:
    def __init__(self, size=1024):
        self.atlas = Atlas(size)
        self.parts = []

    def part(self, name, flags=0):
        p = Part(name, flags)
        self.parts.append(p)
        return p

    def save(self, path):
        S = self.atlas.size
        with open(path, 'wb') as f:
            parts = [p for p in self.parts if p.quads]
            f.write(b'ATM1')
            f.write(struct.pack('>i', len(parts)))
            for p in parts:
                nb = p.name.encode('utf-8')
                f.write(struct.pack('>H', len(nb)) + nb)
                f.write(struct.pack('>b', p.flags))
                f.write(struct.pack('>i', len(p.quads)))
                for q in p.quads:
                    for v in q:
                        x, y, z, u, vv, nx, ny, nz = v
                        f.write(struct.pack('>8f', x, -y, -z, u / S, vv / S, nx, -ny, -nz))

def surface(part, fn, nu, nv, rect, atlas_size, closed_u=False, outward=None, two_sided=False, flip=False):
    """Superficie parametrica fn(a,b)->xyz con a,b en [0,1]. rect=(x,y,w,h) en pixeles del atlas. Normales suaves numericas."""
    a = np.linspace(0, 1, nu + 1)
    b = np.linspace(0, 1, nv + 1)
    P = np.zeros((nu + 1, nv + 1, 3))
    for i, ai in enumerate(a):
        for j, bj in enumerate(b):
            P[i, j] = fn(ai, bj)
    # derivadas
    def d(arr, axis, closed):
        out = np.zeros_like(arr)
        n = arr.shape[axis]
        for k in range(n):
            if closed:
                p, q = (k - 1) % (n - 1), (k + 1) % (n - 1)
                kk = k % (n - 1)
                lo, hi = (kk - 1) % (n - 1), (kk + 1) % (n - 1)
            else:
                lo, hi = max(k - 1, 0), min(k + 1, n - 1)
            if axis == 0: out[k] = arr[hi] - arr[lo]
            else: out[:, k] = arr[:, hi] - arr[:, lo]
        return out
    Du = d(P, 0, closed_u)
    Dv = d(P, 1, False)
    N = np.cross(Du, Dv)
    # normales degeneradas (en los polos): promediar las vecinas
    ln = np.linalg.norm(N, axis=-1)
    for i in range(nu + 1):
        for j in range(nv + 1):
            if ln[i, j] < 1e-9:
                jj = j + 1 if j == 0 else j - 1
                N[i, j] = N[i, min(max(jj, 0), nv)]
    N = norm(N)
    sign = -1.0 if flip else 1.0
    if outward is not None:
        c = np.asarray(outward, float)
        s = np.sum(np.sum(N * (P - c), axis=-1))
        sign = 1.0 if s >= 0 else -1.0
        if flip: sign = -sign
    N = N * sign
    x0, y0, w, h = rect
    def vert(i, j, nflip=1.0):
        return [*P[i, j], x0 + a[i] * w, y0 + b[j] * h, *(N[i, j] * nflip)]
    for i in range(nu):
        for j in range(nv):
            q = np.array([vert(i, j), vert(i + 1, j), vert(i + 1, j + 1), vert(i, j + 1)])
            part.quads.append(q)
            if two_sided:
                q2 = np.array([vert(i, j, -1), vert(i, j + 1, -1), vert(i + 1, j + 1, -1), vert(i + 1, j, -1)])
                part.quads.append(q2)

def poly(part, pts, uvs, normal=None, two_sided=False):
    """Poligono plano convexo (3 o 4 puntos; 3 = triangulo). Sombreado plano."""
    pts = [np.asarray(p, float) for p in pts]
    if len(pts) == 3: pts = pts + [pts[2]]; uvs = list(uvs) + [uvs[2]]
    if normal is None:
        normal = np.cross(pts[1] - pts[0], pts[2] - pts[0])
        if np.linalg.norm(normal) < 1e-9: normal = np.cross(pts[2] - pts[0], pts[3] - pts[0])
        normal = normal / max(np.linalg.norm(normal), 1e-9)
    q = np.array([[*pts[k], uvs[k][0], uvs[k][1], *normal] for k in range(4)])
    part.quads.append(q)
    if two_sided:
        part.quads.append(np.array([[*pts[k], uvs[k][0], uvs[k][1], *(-normal)] for k in (0, 3, 2, 1)]))

def box(part, lo, hi, rect, faces=None, bevel=0.0):
    """Caja alineada a ejes, cada cara con la misma region del atlas (rect). Normales planas hacia afuera."""
    x0, y0, z0 = lo; x1, y1, z1 = hi
    rx, ry, rw, rh = rect
    def uv(u, v): return (rx + u * rw, ry + v * rh)
    fl = [
        ([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], (0, 0, 1)),
        ([(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)], (0, 0, -1)),
        ([(x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)], (1, 0, 0)),
        ([(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], (-1, 0, 0)),
        ([(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)], (0, 1, 0)),
        ([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (0, -1, 0)),
    ]
    for k, (ps, n) in enumerate(fl):
        if faces is not None and k not in faces: continue
        poly(part, ps, [uv(0, 1), uv(1, 1), uv(1, 0), uv(0, 0)], np.array(n, float))

# ----------------------------------------------------------------------------- formas utiles
def superellipse(theta, a, b=None, n=6.0):
    """Radio de un 'cuadrado redondeado' (superelipse) en la direccion theta. a = semieje x, b = semieje z."""
    b = a if b is None else b
    c, s = abs(math.cos(theta)), abs(math.sin(theta))
    return 1.0 / (((c / a) ** n + (s / b) ** n) ** (1.0 / n) + 1e-12)

def catmull(pts, n):
    """Spline Catmull-Rom por los puntos (lista de tuplas) -> n puntos equiespaciados por longitud de arco."""
    P = [np.asarray(p, float) for p in pts]
    P = [P[0] * 2 - P[1]] + P + [P[-1] * 2 - P[-2]]
    dense = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for t in np.linspace(0, 1, 24, endpoint=False):
            dense.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    dense.append(P[-2])
    dense = np.array(dense)
    seg = np.linalg.norm(np.diff(dense, axis=0), axis=1)
    s = np.concatenate([[0], np.cumsum(seg)])
    tt = np.linspace(0, s[-1], n)
    out = np.stack([np.interp(tt, s, dense[:, k]) for k in range(dense.shape[1])], axis=1)
    return out, s[-1]

def arclen(profile):
    pr = np.asarray(profile, float)
    return float(np.sum(np.linalg.norm(np.diff(pr, axis=0), axis=1)))

def lathe(part, model, profile, rect, nu=40, nv=24, shape=None, y0=0.0, two_sided=False, flip=False, center=(0, 0), bend=None, rot=0.0,
          smooth=True):
    """Superficie de revolucion. profile = [(r, y), ...]. shape(theta, t) -> factor de radio (t = 0..1 a lo largo del perfil; sin shape = circulo).
    bend(y) -> (dx, dz) desplaza el eje (cono torcido). rect = region del atlas (u alrededor, v a lo largo del perfil)."""
    pts, L = catmull(profile, nv + 1) if smooth else (np.asarray(profile, float), 0)
    nv_ = len(pts) - 1
    def fn(a, b):
        j = b * nv_
        k = min(int(j), nv_ - 1); f = j - k
        p = pts[k] * (1 - f) + pts[k + 1] * f
        th = a * 2 * math.pi + rot
        rr = p[0] * (shape(th, b) if shape else 1.0)
        dx, dz = bend(p[1] + y0) if bend else (0, 0)
        return (center[0] + dx + rr * math.sin(th), p[1] + y0, center[1] + dz + rr * math.cos(th))
    cen = (center[0], float(pts[:, 1].mean() + y0), center[1])
    surface(part, fn, nu, nv_, rect, model.atlas.size, closed_u=True, outward=cen, two_sided=two_sided, flip=flip)
    return Lat(fn, pts, L)

class Lat:
    """Resultado de lathe(): permite saber el punto/normal de la superficie en (angulo, altura) para pegar decals."""
    def __init__(self, fn, pts, L):
        self.fn, self.pts, self.L = fn, pts, L

    def b_of_y(self, y):
        ys = self.pts[:, 1]
        order = np.argsort(ys)
        return float(np.interp(y, ys[order], np.linspace(0, 1, len(ys))[order]))

    def at(self, th, y, off=0.0):
        """(posicion, normal hacia afuera, 'arriba' a lo largo del perfil) en angulo th (rad, 0 = frente) y altura y."""
        a = (th / (2 * math.pi)) % 1.0
        b = min(max(self.b_of_y(y), 0.002), 0.998)
        e = 1e-3
        p = np.asarray(self.fn(a, b))
        du = np.asarray(self.fn((a + e) % 1.0, b)) - np.asarray(self.fn((a - e) % 1.0, b))
        dv = np.asarray(self.fn(a, min(b + e, 1))) - np.asarray(self.fn(a, max(b - e, 0)))
        n = norm(np.cross(du, dv))
        radial = np.array([p[0], 0, p[2]])
        if np.dot(n, radial) < 0: n = -n
        up = norm(dv)
        if up[1] < 0: up = -up
        return p + n * off, n, up

def circ_len(profile, shape=None, n=64):
    """(longitud del perfil, circunferencia media) para dimensionar la textura."""
    pr = np.asarray(profile, float)
    L = arclen(pr)
    rm = float(np.mean(pr[:, 0]))
    cm = 2 * math.pi * rm
    if shape: cm *= float(np.mean([shape(2 * math.pi * i / n, 0.5) for i in range(n)]))
    return L, cm

def slab(part, model, fn, nu, nv, thickness, rect_top, rect_bot, rect_edge, up=(0, 1, 0), sides=('a0', 'a1', 'b1')):
    """Lamina con grosor: cara de arriba fn(a,b), cara de abajo desplazada -thickness*up, y cantos en los 4 lados."""
    up = np.asarray(up, float)
    surface(part, fn, nu, nv, rect_top, model.atlas.size, outward=None, flip=False)
    # orientar: queremos normal hacia 'up'
    n0 = part.quads[-1][0, 5:8]
    if np.dot(n0, up) < 0:
        for q in part.quads[-(nu * nv):]: q[:, 5:8] *= -1
    fn2 = lambda a, b: np.asarray(fn(a, b)) - up * thickness
    before = len(part.quads)
    surface(part, fn2, nu, nv, rect_bot, model.atlas.size, outward=None)
    n1 = part.quads[before][0, 5:8]
    if np.dot(n1, up) > 0:
        for q in part.quads[before:]: q[:, 5:8] *= -1
    # cantos: a=0, a=1, b=1 (y b=0 queda pegado a la gorra)
    ex, ey, ew, eh = rect_edge
    def wall(pa, pb, outn):
        for i in range(nu if outn is None else 0): pass
    def edge(idx_fn, count):
        for i in range(count):
            p0, p1 = idx_fn(i), idx_fn(i + 1)
            a0, a1 = np.asarray(fn(*p0)), np.asarray(fn(*p1))
            b0, b1 = a0 - up * thickness, a1 - up * thickness
            nrm = np.cross(a1 - a0, up)
            nrm = nrm / max(np.linalg.norm(nrm), 1e-9)
            u0, u1 = ex + ew * i / count, ex + ew * (i + 1) / count
            part.quads.append(np.array([[*a0, u0, ey, *nrm], [*a1, u1, ey, *nrm], [*b1, u1, ey + eh, *nrm], [*b0, u0, ey + eh, *nrm]]))
    if 'a0' in sides: edge(lambda i: (0.0, i / nv), nv)
    if 'a1' in sides: edge(lambda i: (1.0, i / nv), nv)
    if 'b1' in sides: edge(lambda i: (i / nu, 1.0), nu)
    if 'b0' in sides: edge(lambda i: (i / nu, 0.0), nu)

def rotmat(ax, ay=0.0, az=0.0):
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]); Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]); Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx

def xform(part, i0, R, t=(0, 0, 0)):
    """Gira (R) y mueve (t) los quads de part desde el indice i0 (posiciones y normales)."""
    R = np.asarray(R, float); t = np.asarray(t, float)
    for q in part.quads[i0:]:
        for v in q:
            v[:3] = R @ v[:3] + t
            v[5:8] = R @ v[5:8]

def decal(part, c, normal, upv, w, h, rect, off=0.05, uv=((0, 1), (1, 1), (1, 0), (0, 0))):
    """Rectangulo pegado sobre una superficie (logos, gemas, estrellas). c = centro, normal = hacia afuera, upv = direccion 'arriba' del dibujo."""
    n = norm(np.asarray(normal, float)); u = norm(np.asarray(upv, float))
    r = norm(np.cross(u, n))
    u = np.cross(n, r)
    cc = np.asarray(c, float) + n * off
    rx, ry, rw, rh = rect
    pts = [cc - r * w / 2 - u * h / 2, cc + r * w / 2 - u * h / 2, cc + r * w / 2 + u * h / 2, cc - r * w / 2 + u * h / 2]
    uvs = [(rx + a * rw, ry + b * rh) for a, b in uv]
    poly(part, pts, uvs, n)

def tube(part, model, path_pts, radius, rect, nu=14, nv=30, cap_end=True, outward_hint=False, flip=False, twist=0.0, squash=1.0, ref=None):
    """Tubo que sigue una curva 3D (Catmull-Rom por path_pts). radius(t) con t en [0,1]. u alrededor, v a lo largo."""
    pts, L = catmull(path_pts, nv + 1)
    T = norm(np.gradient(pts, axis=0))
    ref = (np.array(ref, float) if ref is not None else (np.array([0, 0, 1.0]) if abs(T[0][2]) < 0.9 else np.array([1.0, 0, 0])))
    Nn, Bn = [], []
    n0 = norm(np.cross(T[0], ref)); b0 = np.cross(T[0], n0)
    Nn.append(n0); Bn.append(b0)
    for i in range(1, len(pts)):
        n = Nn[-1] - np.dot(Nn[-1], T[i]) * T[i]
        n = norm(n); Nn.append(n); Bn.append(np.cross(T[i], n))
    Nn, Bn = np.array(Nn), np.array(Bn)
    def fn(a, b):
        j = b * nv
        k = min(int(j), nv - 1); f = j - k
        c = pts[k] * (1 - f) + pts[k + 1] * f
        n = Nn[k] * (1 - f) + Nn[k + 1] * f
        bb = Bn[k] * (1 - f) + Bn[k + 1] * f
        r = radius(b)
        th = a * 2 * math.pi + twist * b
        return c + n * (r * math.cos(th)) + bb * (r * math.sin(th) * squash)
    # el radio nunca es 0 en el extremo (para tener normales), pero puede ser ~0.02
    surface(part, fn, nu, nv, rect, model.atlas.size, closed_u=True, outward=tuple(pts.mean(axis=0)) if outward_hint else None, flip=flip)
    return pts, L

def ellipsoid(part, model, c, r, rect, nu=24, nv=14, flip=False, lat0=-1.0, lat1=1.0):
    """Elipsoide (r = (rx, ry, rz)); lat0/lat1 recortan en [-1,1] sobre el eje y (-1 abajo)."""
    cx, cy, cz = c
    def fn(a, b):
        th = a * 2 * math.pi
        s = lerp(lat0, lat1, 1 - b)
        ph = math.asin(max(-1, min(1, s)))
        return (cx + r[0] * math.cos(ph) * math.sin(th), cy + r[1] * math.sin(ph), cz + r[2] * math.cos(ph) * math.cos(th))
    surface(part, fn, nu, nv, rect, model.atlas.size, closed_u=True, outward=c, flip=flip)

def torus(part, model, c, R, r, rect, nu=48, nv=14, tilt=(0, 0), squash=1.0):
    """Toro horizontal centrado en c (radio mayor R, menor r). tilt=(rot_x, rot_z) en radianes."""
    cx, cy, cz = c
    rx, rz = tilt
    def fn(a, b):
        th = a * 2 * math.pi
        ph = b * 2 * math.pi
        x = (R + r * math.cos(ph)) * math.sin(th)
        z = (R + r * math.cos(ph)) * math.cos(th)
        y = r * math.sin(ph) * squash
        # inclinacion
        y2 = y * math.cos(rx) - z * math.sin(rx); z2 = y * math.sin(rx) + z * math.cos(rx)
        x2 = x * math.cos(rz) - y2 * math.sin(rz); y3 = x * math.sin(rz) + y2 * math.cos(rz)
        return (cx + x2, cy + y3, cz + z2)
    # normal hacia afuera del centro del tubo: se calcula con 'outward' no sirve (toro) -> signo fijo por prueba
    surface(part, fn, nu, nv, rect, model.atlas.size, closed_u=True, outward=None, flip=False)

# ----------------------------------------------------------------------------- vista previa (rasterizador de software)
def render(model, tex_arr, yaw=35, pitch=18, size=512, ss=2, bg=(0, 0, 0, 0), head=True, scale=None, center=None, glow_t=1.0, spin=0.0, dist=60,
          body=False):
    """Dibuja el modelo con un rasterizador simple (z-buffer, luz difusa, textura bilinear). Devuelve imagen RGBA PIL."""
    S = size * ss
    zb = np.full((S, S), 1e9, np.float32)
    col = np.zeros((S, S, 4), np.float32)
    col[..., :] = bg
    ar = math.radians(yaw); ap = math.radians(pitch)
    # camara orbita alrededor del centro; el rostro mira a +z
    def cam(p):
        x, y, z = p
        x1 = x * math.cos(ar) - z * math.sin(ar); z1 = x * math.sin(ar) + z * math.cos(ar)
        y1 = y * math.cos(ap) - z1 * math.sin(ap); z2 = y * 0 + y * math.sin(ap) + z1 * math.cos(ap)
        return np.array([x1, y1, -z2])
    th = tex_arr.shape[0]; tw = tex_arr.shape[1]
    all_pts = []
    for p in model.parts:
        for q in p.quads: all_pts.extend(q[:, :3])
    all_pts = np.array(all_pts) if all_pts else np.zeros((1, 3))
    if head: all_pts = np.vstack([all_pts, [[-4, 0, -4], [4, 8, 4]]])
    mn, mx = all_pts.min(axis=0), all_pts.max(axis=0)
    ctr = (mn + mx) / 2 if center is None else np.array(center, float)
    ext = float(np.max(mx - mn)) * 0.62 if scale is None else scale
    f = S / (2.0 * ext)
    ldir = norm(np.array([0.45, 0.8, 0.55]))
    ldir2 = norm(np.array([-0.5, 0.3, -0.4]))
    def lit(n, emit):
        if emit: return 1.0
        nc = n
        return 0.38 + 0.62 * max(0.0, float(np.dot(nc, ldir))) * 0.85 + 0.18 * max(0.0, float(np.dot(nc, ldir2)))
    def rot_n(n):
        x, y, z = n
        x1 = x * math.cos(ar) - z * math.sin(ar); z1 = x * math.sin(ar) + z * math.cos(ar)
        y1 = y * math.cos(ap) - z1 * math.sin(ap); z2 = y * math.sin(ap) + z1 * math.cos(ap)
        return np.array([x1, y1, z2])
    def tri(v3, shade_emit, addv, tex, p_flags):
        pts = []
        for v in v3:
            c = cam(np.array(v[:3]) - ctr)
            pts.append((S / 2 + c[0] * f, S / 2 - c[1] * f, c[2]))
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        x0, x1 = int(max(0, math.floor(min(xs)))), int(min(S - 1, math.ceil(max(xs))))
        y0, y1 = int(max(0, math.floor(min(ys)))), int(min(S - 1, math.ceil(max(ys))))
        if x1 < x0 or y1 < y0: return
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        (ax, ay, az), (bx, by, bz), (cx_, cy_, cz_) = pts
        den = (by - cy_) * (ax - cx_) + (cx_ - bx) * (ay - cy_)
        if abs(den) < 1e-9: return
        w0 = ((by - cy_) * (gx - cx_) + (cx_ - bx) * (gy - cy_)) / den
        w1 = ((cy_ - ay) * (gx - cx_) + (ax - cx_) * (gy - cy_)) / den
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any(): return
        z = w0 * az + w1 * bz + w2 * cz_
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        ok = m & ((z < sub) | addv)
        if not ok.any(): return
        u = w0 * v3[0][3] + w1 * v3[1][3] + w2 * v3[2][3]
        vv = w0 * v3[0][4] + w1 * v3[1][4] + w2 * v3[2][4]
        n = w0[..., None] * np.array(v3[0][5:8]) + w1[..., None] * np.array(v3[1][5:8]) + w2[..., None] * np.array(v3[2][5:8])
        n = n / np.maximum(np.linalg.norm(n, axis=-1, keepdims=True), 1e-9)
        # textura bilineal
        th_, tw_ = tex.shape[:2]
        fu = np.clip(u * tw_ - 0.5, 0, tw_ - 1.001); fv = np.clip(vv * th_ - 0.5, 0, th_ - 1.001)
        iu, iv = fu.astype(int), fv.astype(int); du, dv = (fu - iu)[..., None], (fv - iv)[..., None]
        t = (tex[iv, iu] * (1 - du) * (1 - dv) + tex[iv, iu + 1] * du * (1 - dv) + tex[iv + 1, iu] * (1 - du) * dv + tex[iv + 1, iu + 1] * du * dv)
        # luz: se calcula en espacio del mundo con la normal original (hacia el espectador por cara)
        nw = n
        # normal en espacio camara para decidir cara frontal
        nx, ny, nz = nw[..., 0], nw[..., 1], nw[..., 2]
        x1_ = nx * math.cos(ar) - nz * math.sin(ar); z1_ = nx * math.sin(ar) + nz * math.cos(ar)
        y1_ = ny * math.cos(ap) - z1_ * math.sin(ap); z2_ = ny * math.sin(ap) + z1_ * math.cos(ap)
        facing = z2_  # >0 mira a la camara? (camara en -z... ver abajo)
        dn = np.maximum(0, nx * ldir[0] + ny * ldir[1] + nz * ldir[2])
        dn2 = np.maximum(0, nx * ldir2[0] + ny * ldir2[1] + nz * ldir2[2])
        # normal doble cara: si apunta lejos de la camara, se invierte para iluminar
        flipm = facing < 0
        sgn = np.where(flipm, -1.0, 1.0)
        dn = np.maximum(0, sgn * (nx * ldir[0] + ny * ldir[1] + nz * ldir[2]))
        dn2 = np.maximum(0, sgn * (nx * ldir2[0] + ny * ldir2[1] + nz * ldir2[2]))
        shade = 0.42 + 0.58 * dn * 0.9 + 0.16 * dn2
        if shade_emit:
            rgb = t[..., :3] * glow_t
            alpha = t[..., 3] * glow_t
        else:
            rgb = t[..., :3] * shade[..., None]
            alpha = t[..., 3]
        mm = ok & (alpha > 0.02 if not addv else alpha > 0.0)
        if not mm.any(): return
        region = col[y0:y1 + 1, x0:x1 + 1]
        if addv:
            region[mm, :3] = np.minimum(1, region[mm, :3] + rgb[mm] * alpha[mm, None])
            region[mm, 3] = np.maximum(region[mm, 3], np.minimum(1, alpha[mm]))
        else:
            region[mm, :3] = rgb[mm]
            region[mm, 3] = 1.0
            sub[mm] = z[mm]
    tex = tex_arr
    # cuerpo / cabeza de referencia
    if head or body:
        ref = Model(8)
        rp = ref.part('ref')
        hs = np.full((2, 2, 4), [0.80, 0.62, 0.50, 1.0], np.float32)
        hs[1, :, :3] = [0.35, 0.25, 0.2]
        def refbox(lo, hi, c):
            tt = np.full((4, 4, 4), 1.0, np.float32); tt[..., :3] = c
            m = Model(4); pp = m.part('r'); box(pp, lo, hi, (0, 0, 4, 4)); return m, tt
        pieces = []
        if head: pieces.append(((-4, 0, -4), (4, 8, 4), (0.78, 0.60, 0.48)))
        if body:
            pieces.append(((-4, -12, -2), (4, 0, 2), (0.25, 0.55, 0.75)))
        for lo, hi, c in pieces:
            m, tt = refbox(lo, hi, c)
            for q in m.parts[0].quads:
                v = [list(x) for x in q]
                for k in range(4): v[k][3] = 0.5; v[k][4] = 0.5
                global_tex = tt
                tri([v[0], v[1], v[2]], False, False, tt, 0); tri([v[0], v[2], v[3]], False, False, tt, 0)
    # primero opacas, luego emisivas
    for pass_emit in (False, True):
        for p in model.parts:
            emit = bool(p.flags & F_EMIT)
            if emit != pass_emit: continue
            spin_a = math.radians(spin) if (p.flags & F_SPIN) else 0.0
            for q in p.quads:
                v = [list(x) for x in q]
                for k in range(4): v[k][3] /= model.atlas.size; v[k][4] /= model.atlas.size
                if spin_a:
                    for k in range(4):
                        x, y, z = v[k][:3]
                        v[k][0] = x * math.cos(spin_a) + z * math.sin(spin_a); v[k][2] = -x * math.sin(spin_a) + z * math.cos(spin_a)
                        nx, nz = v[k][5], v[k][7]
                        v[k][5] = nx * math.cos(spin_a) + nz * math.sin(spin_a); v[k][7] = -nx * math.sin(spin_a) + nz * math.cos(spin_a)
                tri([v[0], v[1], v[2]], emit, emit, tex, p.flags); tri([v[0], v[2], v[3]], emit, emit, tex, p.flags)
    im = Image.fromarray((np.clip(col, 0, 1) * 255).astype(np.uint8), 'RGBA')
    if ss > 1: im = im.resize((size, size), Image.LANCZOS)
    return im
