package com.atenas.social.cosmetics;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Forma y animacion de las mascotas. NO usa nada de Minecraft (solo Gson): la forma de cada modelo sale de pets/models.json
 * (lo genera tools/gen_pets.py desde tools/pet_models.py, igual que las texturas), asi forma, textura y animacion no se desajustan,
 * y las dos versiones del juego (1.8.9 y 1.20.1) dibujan exactamente lo mismo.
 *
 * Marco del modelo (como un jugador): x a la izquierda de la mascota, y hacia ABAJO, el frente es -z, los pies en y = 24.
 * Unidades: 1/16 de bloque antes de la escala del modelo.
 */
public final class PetSpec {
    private PetSpec() {}

    // ------------------------------------------------------------------ datos (los llena Gson)
    public static final class Piece {
        public String n, p, role;
        public float[] s, o, at;
        public int[] uv;
    }

    public static final class Kind {
        public String shape;
        public float scale = 0.75f, bodyH, bodyD, legH, legUp, tailUp, tailCurl, earRest, neckUp, perchScale = 0.5f, headH;
        public boolean hop, avoidWater, flier;
        public List<Piece> pieces;
        public transient String name;
    }

    private static final class Root {
        int[] sheet;
        Map<String, String> pets;
        Map<String, Kind> kinds;
    }

    public static int texW = 128, texH = 64;
    private static final Map<String, Kind> KINDS = new HashMap<String, Kind>();
    private static final Map<String, String> PETS = new HashMap<String, String>();
    private static boolean loaded;

    /** Rutas donde puede estar models.json (cada mod tiene la suya). */
    private static final String[] PATHS = {"/assets/atenassocial/pets/models.json", "/assets/atenasclient/pets/models.json"};

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        for (String path : PATHS) {
            InputStream in = PetSpec.class.getResourceAsStream(path);
            if (in == null) continue;
            try {
                Root r = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Root.class);
                if (r.sheet != null) { texW = r.sheet[0]; texH = r.sheet[1]; }
                if (r.pets != null) PETS.putAll(r.pets);
                for (Map.Entry<String, Kind> e : r.kinds.entrySet()) { e.getValue().name = e.getKey(); KINDS.put(e.getKey(), e.getValue()); }
                return;
            } catch (Throwable t) {
                System.err.println("[Atenas] no se pudo leer " + path + ": " + t);
            }
        }
    }

    public static Kind kind(String name) { load(); return KINDS.get(name); }

    public static java.util.Collection<Kind> kinds() { load(); return KINDS.values(); }

    /** Modelo de cada mascota segun su id (las texturas e iconos se llaman igual que el id). */
    public static Kind kindFor(String id) {
        load();
        String k = PETS.get(id);
        Kind kind = k == null ? null : KINDS.get(k);
        return kind != null ? kind : KINDS.get("dog");
    }

    // ------------------------------------------------------------------ ranuras de la pose (nombre de pieza -> indice)
    public static final String[] NAMES = {
        "body", "neck", "head", "snout", "jaw", "beakU", "beakL", "crest", "earL", "earR", "tail1", "tail2", "tail3",
        "legFR1", "legFR2", "legFR3", "legFL1", "legFL2", "legFL3", "legRR1", "legRR2", "legRR3", "legRL1", "legRL2", "legRL3",
        "wingL1", "wingL2", "wingR1", "wingR2", "legL1", "legL2", "legR1", "legR2"};
    public static final int BODY = 0, NECK = 1, HEAD = 2, SNOUT = 3, JAW = 4, BEAKU = 5, BEAKL = 6, CREST = 7, EARL = 8, EARR = 9,
            T1 = 10, T2 = 11, T3 = 12, FR1 = 13, FR2 = 14, FR3 = 15, FL1 = 16, FL2 = 17, FL3 = 18, RR1 = 19, RR2 = 20, RR3 = 21,
            RL1 = 22, RL2 = 23, RL3 = 24, WL1 = 25, WL2 = 26, WR1 = 27, WR2 = 28, LL1 = 29, LL2 = 30, LR1 = 31, LR2 = 32;
    public static final int N = NAMES.length;
    private static final Map<String, Integer> IDX = new HashMap<String, Integer>();
    static { for (int i = 0; i < N; i++) IDX.put(NAMES[i], i); }

    /** Indice de ranura de una pieza, o -1 si no se anima. */
    public static int slot(String piece) { Integer i = IDX.get(piece); return i == null ? -1 : i; }

    /** Pose: rotaciones (radianes) y desplazamientos (unidades del modelo) por ranura. */
    public static final class Pose {
        public final float[] rx = new float[N], ry = new float[N], rz = new float[N], dx = new float[N], dy = new float[N], dz = new float[N];
        void clear() {
            java.util.Arrays.fill(rx, 0f); java.util.Arrays.fill(ry, 0f); java.util.Arrays.fill(rz, 0f);
            java.util.Arrays.fill(dx, 0f); java.util.Arrays.fill(dy, 0f); java.util.Arrays.fill(dz, 0f);
        }
    }

    /** Escala con la que se dibuja ahora (mas chica en el hombro). */
    public static float renderScale(Kind k, PetBrain b, float pt) {
        return k.scale * (1f + (k.perchScale - 1f) * b.iperch(pt));
    }

    public static void pose(Kind k, PetBrain b, float pt, Pose o) {
        o.clear();
        if (k.flier) poseBird(k, b, pt, o); else poseQuad(k, b, pt, o);
    }

    // ------------------------------------------------------------------ cuadrupedos
    private static void poseQuad(Kind k, PetBrain b, float pt, Pose o) {
        float swing = b.iswing(pt), walk = b.iwalk(pt), sit0 = b.isit(pt), mouth = b.imouth(pt), perch = b.iperch(pt), swim = b.iswim(pt);
        float t = b.age + pt;
        float sit = Math.max(sit0, perch * 0.9f);
        float sitAng = 0.75f * sit;
        float lowest = k.bodyH / 2f * (float) Math.cos(sitAng) + k.bodyD / 2f * (float) Math.sin(sitAng);
        float delta = k.legH * sit - lowest + k.bodyH / 2f;
        float g = swing * (1f - sit);
        float ph = walk * 0.6662f;

        // ---- cuerpo (al nadar se hunde y levanta el hocico; el conejo salta)
        float hopUp = k.hop ? Math.abs((float) Math.sin(ph * 0.9f)) * 2.4f * g : 0f;
        o.rx[BODY] = -sitAng - swim * 0.14f;
        o.dy[BODY] = delta + swim * 3.0f - hopUp + (float) Math.sin(t * 0.13f) * 0.12f * (1f - swim);
        o.dz[BODY] = 0f;

        // ---- cuello y cabeza
        float pitch = (float) Math.toRadians(b.iheadPitch(pt));
        o.rx[NECK] = -k.neckUp + pitch * 0.4f - sit * 0.25f + swim * -0.15f;
        o.rx[HEAD] = k.neckUp * 0.95f + pitch * 0.6f + sit * 0.2f;
        o.ry[HEAD] = (float) Math.toRadians(b.iheadYaw(pt));
        o.rz[HEAD] = b.iheadTilt(pt);
        o.rx[JAW] = mouth * 0.42f + (float) Math.max(0, Math.sin(t * 0.9f)) * 0.08f * mouth;
        float ear = k.earRest + b.iear(pt);
        float back = swing * 0.28f * (1f - sit);
        o.rz[EARL] = ear; o.rz[EARR] = -ear;
        o.rx[EARL] = back; o.rx[EARR] = back;
        if (k.hop) { o.rx[EARL] = 0.25f + back; o.rx[EARR] = 0.25f + back; }

        // ---- cola en tres tramos con retraso (ola)
        float ws = b.wagSpeed, amp = b.wagAmp;
        float up = k.tailUp * (1f - 0.5f * sit) * (1f - swim * 0.7f);
        o.rx[T1] = up;
        o.rx[T2] = k.tailCurl;
        o.rx[T3] = k.tailCurl * 0.7f;
        o.ry[T1] = (float) Math.sin(t * ws) * amp;
        o.ry[T2] = (float) Math.sin(t * ws - 0.8f) * amp * 0.9f;
        o.ry[T3] = (float) Math.sin(t * ws - 1.6f) * amp;

        // ---- patas: trote en diagonal con rodilla; sentada: las de atras se pliegan; nadando: bracean
        float a = (float) Math.cos(ph) * 1.0f * g;
        float fr = a, fl = -a, rr = -a, rl = a;
        if (k.hop) { fr = a; fl = a; rr = -a; rl = -a; }
        float pad = t * 0.55f;
        float pfr = (float) Math.sin(pad) * 0.9f, pfl = (float) Math.sin(pad + 3.14f) * 0.9f, prr = pfl, prl = pfr;
        fr += (pfr - fr) * swim; fl += (pfl - fl) * swim; rr += (prr - rr) * swim; rl += (prl - rl) * swim;
        float pfront = perch * 0.9f;                  // en el hombro las manos quedan dobladas hacia adelante
        legQuad(o, FR1, fr - 0.35f * pfront, 1.1f, 0.8f + pfront * 0.7f, swim);
        legQuad(o, FL1, fl - 0.35f * pfront, 1.1f, 0.8f + pfront * 0.7f, swim);
        float foldU = -1.35f * sit, foldL = 1.9f * sit;
        legQuad(o, RR1, rr + foldU, 0.9f, 0.7f, swim); o.rx[RR2] += foldL; o.rx[RR3] -= foldL * 0.45f;
        legQuad(o, RL1, rl + foldU, 0.9f, 0.7f, swim); o.rx[RL2] += foldL; o.rx[RL3] -= foldL * 0.45f;
        o.dy[RR1] = o.dy[RL1] = delta * 0.9f;
        o.dz[RR1] = o.dz[RL1] = -1.0f * sit;
    }

    private static void legQuad(Pose o, int s, float upper, float kneeK, float pawK, float swim) {
        o.rx[s] = upper;
        float lift = Math.max(0f, -upper) * kneeK + swim * 0.4f;
        o.rx[s + 1] = lift;
        o.rx[s + 2] = -lift * 0.55f * pawK + Math.max(0f, upper) * 0.15f;
    }

    // ------------------------------------------------------------------ aves y dragon
    private static void poseBird(Kind k, PetBrain b, float pt, Pose o) {
        float fly = b.ifly(pt), perch = b.iperch(pt), spread = b.ispread(pt), amp = b.iflapAmp(pt), mouth = b.imouth(pt);
        float t = b.age + pt, flap = b.iflap(pt);
        float s = (float) Math.sin(flap);
        float perched = 1f - fly;

        o.rx[BODY] = -0.45f * perched + 0.08f * fly;
        o.dy[BODY] = (float) Math.sin(t * 0.25f) * 0.35f * fly + (float) Math.sin(t * 0.09f) * 0.1f * perched;
        float pitch = (float) Math.toRadians(b.iheadPitch(pt));
        o.rx[NECK] = -0.3f * perched + pitch * 0.4f;
        o.rx[HEAD] = 0.45f * perched - 0.05f * fly + pitch * 0.6f;
        o.ry[HEAD] = (float) Math.toRadians(b.iheadYaw(pt));
        o.rz[HEAD] = b.iheadTilt(pt);
        o.rx[BEAKL] = mouth * 0.38f; o.rx[JAW] = mouth * 0.42f;
        o.rx[CREST] = -0.25f * fly + (float) Math.sin(t * 0.07f) * 0.06f;
        float ear = k.earRest + b.iear(pt) * 0.3f;
        o.rz[EARL] = ear; o.rz[EARR] = -ear; o.rx[EARL] = o.rx[EARR] = -0.2f * fly;

        // alas: plegadas cuelgan a los lados; abiertas, baten (el tramo de afuera va con retraso)
        float fold = 0.14f + (float) Math.sin(t * 0.08f) * 0.02f;
        float open = 1.5f + s * 0.75f * amp;
        float ang = fold + (open - fold) * spread;
        o.rz[WL1] = -ang; o.rz[WR1] = ang;
        float bend = (0.05f + spread * 0.42f * amp * (float) Math.sin(flap - 1.0f)) ;
        o.rz[WL2] = -bend; o.rz[WR2] = bend;
        float sweep = 0.3f * amp * spread * (float) Math.cos(flap);
        o.ry[WL1] = sweep; o.ry[WR1] = -sweep;
        o.rx[WL1] = o.rx[WR1] = 0.1f * amp * spread * (float) Math.cos(flap);

        // cola: abanico al volar, cae al posarse
        o.rx[T1] = 0.5f * perched + 0.05f * fly + (float) Math.sin(flap * 2f) * 0.05f * fly;
        o.rx[T2] = k.tailCurl * perched + 0.05f; o.rx[T3] = 0.1f;
        o.ry[T1] = (float) Math.sin(t * 0.2f) * 0.1f;
        o.ry[T2] = (float) Math.sin(t * 0.2f - 0.8f) * 0.12f; o.ry[T3] = (float) Math.sin(t * 0.2f - 1.6f) * 0.14f;

        // patas: recogidas al volar
        float tuck = 1.1f * fly;
        o.rx[LL1] = o.rx[LR1] = tuck; o.rx[LL2] = o.rx[LR2] = -tuck * 0.8f;
        o.rx[FR1] = 0; // (ranuras de cuadrupedo sin uso aqui)
    }
}
