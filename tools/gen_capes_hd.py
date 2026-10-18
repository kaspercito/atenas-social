#!/usr/bin/env python3
"""Capas HD (32): cara de 160x256, hoja de 1024x512 por fotograma (escala x16 sobre el UV 64x32 de CapeModel), tira vertical de 12.
Uso: python3 tools/gen_capes_hd.py <carpeta_assets_atenasclient> [ids ...] [--contact salida.png] [--mod <assets_atenassocial>]
 - sin ids = todas. Las estaticas (1 fotograma) son: atardecer sakura oceano bosque hielo.
 - Escribe textures/cosmetics/capes/<id>.png e icons/cape_<id>.png (128x128).
Fuentes de los disenos: hd_scenes.py (10 escenas), hd_fields.py (15 campos numericos), hd_paint.py (atenas, matrix y 5 estaticas)."""
import os, sys, time
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hd_core import FR, FW, FH

PAD = 16          # relleno de borde de los cantos (8 a 80x128 -> 16 a 160x256)


def registry():
    import hd_scenes, hd_fields, hd_paint
    reg = {}
    for cid, fn in hd_scenes.CAPES.items():
        reg[cid] = (lambda f, fn=fn: fn(f / float(FR)), FR, 3)
    for cid, fn in hd_fields.FIELDS.items():
        reg[cid] = (lambda f, fn=fn: _from_arr(fn(f)), FR, 3)
    for cid, (fn, frames, icf) in hd_paint.PAINT.items():
        reg[cid] = (fn, frames, icf)
    return reg


def _from_arr(a):
    from hd_core import to_rgb
    return to_rgb(a)


def face(img):
    """Imagen de trabajo (320x512) -> cara final RGB uint8 (256x160x3)."""
    if not isinstance(img, Image.Image):
        from hd_core import to_rgb
        img = to_rgb(img)
    return np.array(img.convert('RGB').resize((FW, FH), Image.LANCZOS))


def sheet(outer):
    """Un fotograma de 1024x512: cara de afuera en (16,16), de adentro en (192,16), cantos con el borde estirado."""
    inner = (outer * 0.62).astype(np.uint8)
    fr = np.zeros((512, 1024, 4), np.uint8)
    po = np.pad(outer, ((PAD, PAD), (PAD, PAD), (0, 0)), mode='edge')
    fr[0:FH + 2 * PAD, 0:FW + 2 * PAD, :3] = po
    fr[0:FH + 2 * PAD, 0:FW + 2 * PAD, 3] = 255
    pi = np.pad(inner, ((PAD, PAD), (0, PAD), (0, 0)), mode='edge')
    x0 = 12 * PAD
    fr[0:FH + 2 * PAD, x0:x0 + FW + PAD, :3] = pi
    fr[0:FH + 2 * PAD, x0:x0 + FW + PAD, 3] = 255
    return fr


def icon(outer):
    """Icono 128x128: la capa (80x128) con marco oscuro, como los de 32x32 pero a 4x."""
    im = Image.fromarray(outer).resize((72, 120), Image.LANCZOS).convert('RGBA')
    out = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    out.paste(Image.new('RGBA', (80, 128), (0, 0, 0, 120)), (24, 0))
    out.paste(im, (28, 4))
    return out


def build(args):
    cid, assets, mods = args
    fn, frames, icf = registry()[cid]
    t0 = time.time()
    faces = [face(fn(f)) for f in range(frames)]
    strip = np.concatenate([sheet(o) for o in faces], axis=0)
    for base in [assets] + mods:
        cd = os.path.join(base, 'textures', 'cosmetics', 'capes'); idr = os.path.join(base, 'textures', 'cosmetics', 'icons')
        os.makedirs(cd, exist_ok=True); os.makedirs(idr, exist_ok=True)
        Image.fromarray(strip, 'RGBA').save(os.path.join(cd, cid + '.png'), optimize=True)
        icon(faces[min(icf, frames - 1)]).save(os.path.join(idr, 'cape_' + cid + '.png'), optimize=True)
    small = [Image.fromarray(faces[min(k, frames - 1)]).resize((80, 128), Image.LANCZOS) for k in (0, 3, 6, 9)]
    sz = os.path.getsize(os.path.join(assets, 'textures', 'cosmetics', 'capes', cid + '.png')) / 1024.0
    print('%-12s %2d fot  %6.0f KB  %.1fs' % (cid, frames, sz, time.time() - t0), flush=True)
    return cid, small


def main():
    a = sys.argv[1:]
    contact = None; mods = []
    if '--contact' in a:
        i = a.index('--contact'); contact = a[i + 1]; del a[i:i + 2]
    while '--mod' in a:
        i = a.index('--mod'); mods.append(a[i + 1]); del a[i:i + 2]
    assets = a[0]
    reg = registry()
    ids = a[1:] or list(reg)
    from multiprocessing import Pool
    with Pool(2) as p:
        res = dict(p.map(build, [(c, assets, mods) for c in ids], chunksize=1))
    if contact:
        sheetim = Image.new('RGB', (len(ids) * 88, 4 * 134), (30, 30, 36))
        for ci, cid in enumerate(ids):
            for k, im in enumerate(res[cid]):
                sheetim.paste(im, (ci * 88 + 4, k * 134 + 3))
        sheetim.save(contact)


if __name__ == '__main__':
    main()
