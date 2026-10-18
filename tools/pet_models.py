"""Definicion de los modelos de mascotas (UNICA fuente de verdad).

`python3 tools/gen_pets.py` lee esto, reparte la hoja de textura (128 x 64 unidades logicas) y escribe
  src/main/resources/assets/atenassocial/pets/models.json
que el juego (PetSpec.java) carga tal cual. Asi la forma, la textura y la animacion nunca se desajustan.

Marco del modelo (como un jugador): x a la izquierda de la mascota, y hacia ABAJO, el frente es -z, los pies estan en y = 24.
Cada pieza: n=nombre (el nombre decide como la anima PetSpec.pose), p=padre, s=(w,h,d), o=origen de la caja (addBox),
at=pivote relativo al padre, role=como se pinta, share=comparte hoja de textura con otra pieza igual (patas, orejas, alas).
"""

def P(n, p, s, o, at, role, share=None):
    return dict(n=n, p=p, s=list(s), o=list(o), at=list(at), role=role, share=share or n)


def quad(bw, bh, bd, nw, nh, nd, hw, hh, hd, sw, sh, sd, ew, eh, ed, lw, ld, lu, ll, lp, tails, ear_style="perk", ear_x=None,
         neck_up=0.5, hop=False, tailUp=0.8, tailCurl=0.3, ear_rest=0.1, perch=0.5, waterAvoid=False, paw_extra=1):
    """Cuadrupedo: cuerpo, cuello, cabeza, hocico, mandibula, orejas, cola (hasta 3 tramos), 4 patas de 3 tramos (muslo, canilla, pata)."""
    L = lu + ll + lp
    pcs = [
        P("body", "", (bw, bh, bd), (-bw / 2, -bh / 2, -bd / 2), (0, 24 - L - bh / 2, 0), "body"),
        P("neck", "body", (nw, nh, nd), (-nw / 2, -nh / 2, -nd), (0, -bh / 2 + nh / 2 + 0.3, -bd / 2 + 1.2), "neck"),
        P("head", "neck", (hw, hh, hd), (-hw / 2, -hh / 2, -hd), (0, 0, -nd + 0.8), "head"),
        P("snout", "head", (sw, sh, sd), (-sw / 2, 0, -sd), (0, hh / 2 - sh - 0.9, -hd), "snout"),
        P("jaw", "head", (max(1, sw - 1) if sw > 2 else sw, 1, max(1, sd - 1)), (-(max(1, sw - 1) if sw > 2 else sw) / 2, 0, -max(1, sd - 1)),
          (0, hh / 2 - 0.9, -hd + 0.5), "jaw"),
    ]
    ex = ear_x if ear_x is not None else hw / 2 - ew / 2 - 0.4
    pcs.append(P("earL", "head", (ew, eh, ed), (-ew / 2, -eh, -ed / 2), (ex, -hh / 2 + 0.6, -hd * 0.32), "ear", "ear"))
    pcs.append(P("earR", "head", (ew, eh, ed), (-ew / 2, -eh, -ed / 2), (-ex, -hh / 2 + 0.6, -hd * 0.32), "ear", "ear"))
    prev, prevd = "body", None
    for i, (tw, th, td) in enumerate(tails):
        n = "tail%d" % (i + 1)
        if i == 0:
            pcs.append(P(n, "body", (tw, th, td), (-tw / 2, -th / 2, 0), (0, -bh / 2 + th / 2 + 0.6, bd / 2 - 0.6), "tail"))
        else:
            pcs.append(P(n, "tail%d" % i, (tw, th, td), (-tw / 2, -th / 2, 0), (0, 0, tails[i - 1][2]), "tail" if i < len(tails) - 1 else "tailtip"))
    fx, fzf, fzr = bw / 2 - lw / 2, -bd / 2 + ld / 2 + 0.6, bd / 2 - ld / 2 - 0.6
    for code, x, z in (("FR", -fx, fzf), ("FL", fx, fzf), ("RR", -fx, fzr), ("RL", fx, fzr)):
        pcs.append(P("leg%s1" % code, "", (lw, lu, ld), (-lw / 2, 0, -ld / 2), (x, 24 - L, z), "leg1", "leg1"))
        pcs.append(P("leg%s2" % code, "leg%s1" % code, (lw, ll, ld), (-lw / 2, 0, -ld / 2), (0, lu, 0), "leg2", "leg2"))
        pcs.append(P("leg%s3" % code, "leg%s2" % code, (lw + paw_extra, lp, ld + paw_extra), (-(lw + paw_extra) / 2, 0, -(ld + paw_extra) / 2 - 0.4),
                     (0, ll, 0), "paw", "paw"))
    return dict(shape="quad", pieces=pcs, scale=0.75, bodyH=bh, bodyD=bd, legH=L, legUp=lu, tailUp=tailUp, tailCurl=tailCurl,
                earRest=ear_rest, neckUp=neck_up, hop=hop, perchScale=perch, avoidWater=waterAvoid, flier=False, headH=hh)


def bird(bw, bh, bd, hw, hh, hd, bkw, bkh, bkd, wl1, wl2, ww, tails, legs=(1, 2, 1), foot=(2, 1, 2), crest=None, horns=None,
         neck=None, scale=0.8, perch=0.9, snout=None, upright=0.0):
    """Ave / dragon: cuerpo, (cuello), cabeza, pico o hocico, mandibula, (cresta / cuernos), 2 alas de 2 tramos, cola de 1-3 tramos, 2 patas."""
    by = 24 - legs[1] - foot[1] - bh / 2
    pcs = [P("body", "", (bw, bh, bd), (-bw / 2, -bh / 2, -bd / 2), (0, by, 0), "body")]
    par, hy, hz = "body", -bh / 2 + 0.2, -bd / 2 + 0.8
    if neck:
        nw, nh, nd = neck
        pcs.append(P("neck", "body", (nw, nh, nd), (-nw / 2, -nh / 2, -nd), (0, -bh / 2 + nh / 2 + 0.2, -bd / 2 + 0.8), "neck"))
        par, hy, hz = "neck", 0, -nd + 0.6
    pcs.append(P("head", par, (hw, hh, hd), (-hw / 2, -hh / 2, -hd / 2), (0, hy - hh / 2 + 0.6, hz - hd / 2 + 0.6), "head"))
    if snout:
        sw, sh, sd = snout
        pcs.append(P("snout", "head", (sw, sh, sd), (-sw / 2, 0, -sd), (0, hh / 2 - sh - 0.6, -hd / 2), "snout"))
        pcs.append(P("jaw", "head", (sw, 1, sd - 1), (-sw / 2, 0, -(sd - 1)), (0, hh / 2 - 0.6, -hd / 2 + 0.3), "jaw"))
    else:
        pcs.append(P("beakU", "head", (bkw, bkh, bkd), (-bkw / 2, 0, -bkd), (0, -0.4, -hd / 2), "beak"))
        pcs.append(P("beakL", "head", (bkw, 1, max(1, bkd - 1)), (-bkw / 2, 0, -max(1, bkd - 1)), (0, bkh - 0.4, -hd / 2 + 0.2), "beak2"))
    if crest:
        cw, ch, cd = crest
        pcs.append(P("crest", "head", (cw, ch, cd), (-cw / 2, -ch, -cd / 2), (0, -hh / 2 + 0.6, -0.2), "crest"))
    if horns:
        cw, ch, cd = horns
        pcs.append(P("earL", "head", (cw, ch, cd), (-cw / 2, -ch, -cd / 2), (hw / 2 - cw / 2 - 0.2, -hh / 2 + 0.6, hd / 2 - 0.5), "horn", "horn"))
        pcs.append(P("earR", "head", (cw, ch, cd), (-cw / 2, -ch, -cd / 2), (-(hw / 2 - cw / 2 - 0.2), -hh / 2 + 0.6, hd / 2 - 0.5), "horn", "horn"))
    # alas: cuelgan hacia abajo desde el hombro (+y); abrirlas = girar en z
    pcs.append(P("wingL1", "body", (1, wl1, ww), (0, 0, -ww / 2), (bw / 2, -bh / 2 + 0.8, -bd / 2 + ww / 2 + 1.0), "wing1", "wing1"))
    pcs.append(P("wingL2", "wingL1", (1, wl2, max(1, ww - 1)), (0, 0, -max(1, ww - 1) / 2), (0, wl1, 0.2), "wing2", "wing2"))
    pcs.append(P("wingR1", "body", (1, wl1, ww), (-1, 0, -ww / 2), (-bw / 2, -bh / 2 + 0.8, -bd / 2 + ww / 2 + 1.0), "wing1", "wing1"))
    pcs.append(P("wingR2", "wingR1", (1, wl2, max(1, ww - 1)), (-1, 0, -max(1, ww - 1) / 2), (0, wl1, 0.2), "wing2", "wing2"))
    for i, (tw, th, td) in enumerate(tails):
        n = "tail%d" % (i + 1)
        if i == 0:
            pcs.append(P(n, "body", (tw, th, td), (-tw / 2, -th / 2, 0), (0, -bh / 2 + th / 2 + 0.8, bd / 2 - 0.6), "tail"))
        else:
            pcs.append(P(n, "tail%d" % i, (tw, th, td), (-tw / 2, -th / 2, 0), (0, 0, tails[i - 1][2]), "tail" if i < len(tails) - 1 else "tailtip"))
    lw, lh, ld = legs
    fw, fh, fd = foot
    for code, x in (("L", bw / 4 + 0.3), ("R", -bw / 4 - 0.3)):
        pcs.append(P("leg%s1" % code, "body", (lw, lh, ld), (-lw / 2, 0, -ld / 2), (x, bh / 2 - 0.2, 0.6), "leg1", "leg1"))
        pcs.append(P("leg%s2" % code, "leg%s1" % code, (fw, fh, fd), (-fw / 2, 0, -fd / 2 - 0.6), (0, lh, 0), "paw", "paw"))
    return dict(shape="bird", pieces=pcs, scale=scale, bodyH=bh, bodyD=bd, legH=lh + fh, legUp=lh, tailUp=0.1, tailCurl=0.15, earRest=0.0,
                neckUp=0.0, hop=False, perchScale=perch, avoidWater=False, flier=True, headH=hh, wing1=wl1, upright=upright)


KINDS = {
    "dog": quad(6, 6, 10, 4, 4, 3, 6, 5, 5, 3, 2, 3, 2, 4, 1, 2, 2, 3, 2, 1, [(2, 2, 3), (2, 2, 3), (1, 1, 2)], tailUp=0.85, tailCurl=0.30),
    "husky": quad(6, 6, 10, 4, 4, 3, 6, 5, 5, 3, 2, 3, 2, 3, 1, 2, 2, 3, 2, 1, [(2, 3, 3), (2, 3, 3), (2, 2, 2)], tailUp=1.05, tailCurl=0.5),
    "cat": quad(5, 5, 10, 3, 3, 3, 5, 4, 4, 3, 2, 1, 2, 2, 1, 2, 2, 3, 2, 1, [(1, 1, 4), (1, 1, 4), (1, 1, 3)], tailUp=0.35, tailCurl=0.55,
                perch=0.6, waterAvoid=True),
    "fox": quad(5, 5, 9, 3, 3, 3, 5, 4, 4, 2, 2, 4, 2, 4, 1, 2, 2, 3, 2, 1, [(3, 3, 4), (3, 3, 3), (2, 2, 3)], tailUp=0.15, tailCurl=0.2, perch=0.55),
    "rabbit": quad(5, 5, 7, 3, 3, 2, 4, 4, 4, 2, 1, 1, 1, 7, 1, 2, 2, 2, 1, 1, [(3, 3, 2)], ear_x=1.0, hop=True, tailUp=0.3, tailCurl=0.0,
                   ear_rest=0.12, perch=0.6, waterAvoid=True, neck_up=0.4),
    "panda": quad(7, 6, 9, 4, 4, 2, 6, 5, 5, 3, 2, 2, 2, 2, 1, 3, 3, 2, 2, 1, [(2, 2, 2)], tailUp=0.2, tailCurl=0.0, ear_x=2.2,
                  perch=0.5, neck_up=0.3),
    "parrot": bird(3, 4, 3, 3, 3, 3, 2, 2, 2, 3, 4, 3, [(2, 1, 5), (2, 1, 4)], legs=(1, 2, 1), foot=(2, 1, 2), crest=(1, 2, 2), scale=0.85, perch=0.95),
    "owl": bird(4, 5, 4, 5, 4, 4, 1, 1, 1, 4, 3, 3, [(3, 1, 3), (3, 1, 2)], legs=(1, 1, 1), foot=(3, 1, 2), horns=(1, 2, 1), scale=0.8, perch=0.9),
    "drake": bird(4, 4, 8, 4, 4, 4, 2, 2, 2, 5, 4, 4, [(2, 2, 4), (2, 2, 4), (1, 1, 3)], legs=(2, 2, 2), foot=(2, 1, 3), horns=(1, 3, 1),
                  neck=(2, 3, 3), snout=(3, 2, 3), scale=0.8, perch=0.7),
}

# id de mascota -> modelo
PET_KIND = {"perro": "dog", "dalmata": "dog", "husky": "husky", "gato": "cat", "gatonegro": "cat", "catalina": "cat", "zorro": "fox",
            "conejo": "rabbit", "panda": "panda", "loro": "parrot", "buho": "owl", "dragoncito": "drake"}
