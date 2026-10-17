package com.atenas.social.cosmetics;

/**
 * Forma y animacion de las mascotas. NO usa nada de Minecraft (el modelo de cada version solo aplica estos numeros), asi las dos
 * versiones del juego dibujan exactamente lo mismo y se puede probar fuera del juego.
 *
 * Marco del modelo (igual que el de un jugador): x a la izquierda de la mascota, y hacia ABAJO, el frente es -z, los pies
 * estan en y = 24. Unidades: 1/16 de bloque, antes de {@link #SCALE}.
 *
 * Las medidas (w, h, d) y la posicion de cada caja en la hoja de textura (u, v) DEBEN coincidir con tools/gen_pets.py
 * (diccionario SIZES y funcion layout; la hoja logica es de 64 x 32 y la imagen real es 6 veces mas grande).
 *
 *   modelo  parte   (u,v)    w h d        modelo  parte   (u,v)    w h d
 *   dog     body    (0,0)    6 6 10       cat     body    (0,0)    5 5 10
 *   husky   head    (32,0)   6 6 5        fox     head    (30,0)   5 5 4   (cat)  /  (28,0) 5 5 5 (fox)
 *   ... el resto se lee en las constantes de abajo.
 */
public final class PetSpec {
    private PetSpec() {}

    /** Tamano general de la mascota respecto a las medidas de la tabla (1 = como un lobo). */
    public static final float SCALE = 0.75f;
    /** Angulo del cuerpo al sentarse (radianes): el frente sube, el trasero baja. */
    static final float SIT_ANGLE = 0.75f;

    public static final class Part {
        public final int u, v, w, h, d;
        Part(int u, int v, int w, int h, int d) { this.u = u; this.v = v; this.w = w; this.h = h; this.d = d; }
    }

    public static final class Kind {
        public final String name;
        public final Part body, head, snout, ear, leg, tail1, tail2;
        /** Inclinacion de la cola en reposo (radianes, positivo = hacia arriba) y curvatura de su segunda parte. */
        public final float tailUp, tailCurl;

        Kind(String name, Part body, Part head, Part snout, Part ear, Part leg, Part tail1, Part tail2, float tailUp, float tailCurl) {
            this.name = name; this.body = body; this.head = head; this.snout = snout; this.ear = ear; this.leg = leg;
            this.tail1 = tail1; this.tail2 = tail2; this.tailUp = tailUp; this.tailCurl = tailCurl;
        }

        // ---- posiciones (relativas al padre de cada pieza)
        /** Centro del cuerpo (pieza raiz). */
        public float bodyY() { return 24f - leg.h - body.h / 2f; }
        /** Cabeza: hija del cuerpo; su pivote queda en la nuca. */
        public float headY() { return -body.h / 2f - head.h * 0.15f; }
        public float headZ() { return -body.d / 2f + 1f; }
        /** Hocico: hijo de la cabeza, en la mitad de abajo de la cara. */
        public float snoutY() { return head.h / 2f - snout.h - 1f; }
        public float snoutZ() { return -head.d; }
        /** Orejas: hijas de la cabeza, +x = oreja izquierda. */
        public float earX() { return head.w / 2f - ear.w / 2f - 0.5f; }
        public float earY() { return -head.h / 2f + 0.5f; }
        public float earZ() { return -head.d * 0.3f; }
        /** Patas: sueltas (no siguen la inclinacion del cuerpo), pivote en la cadera. */
        public float legX() { return body.w / 2f - leg.w / 2f; }
        public float legY() { return 24f - leg.h; }
        public float frontLegZ() { return -body.d / 2f + leg.d / 2f + 0.5f; }
        public float rearLegZ() { return body.d / 2f - leg.d / 2f - 0.5f; }
        /** Cola: hija del cuerpo, arriba y atras; la segunda parte cuelga del final de la primera. */
        public float tailY() { return -body.h / 2f + tail1.h / 2f + 0.5f; }
        public float tailZ() { return body.d / 2f - 0.5f; }
    }

    public static final Kind DOG = new Kind("dog",
            new Part(0, 0, 6, 6, 10), new Part(32, 0, 6, 6, 5), new Part(0, 16, 3, 2, 3), new Part(12, 16, 2, 4, 1),
            new Part(18, 16, 2, 6, 2), new Part(26, 16, 2, 2, 4), new Part(38, 16, 2, 2, 3), 0.85f, 0.35f);
    public static final Kind HUSKY = new Kind("husky",
            new Part(0, 0, 6, 6, 10), new Part(32, 0, 6, 6, 5), new Part(0, 16, 3, 2, 3), new Part(12, 16, 2, 3, 1),
            new Part(18, 16, 2, 6, 2), new Part(26, 16, 2, 3, 4), new Part(38, 16, 2, 3, 3), 1.05f, 0.45f);
    public static final Kind CAT = new Kind("cat",
            new Part(0, 0, 5, 5, 10), new Part(30, 0, 5, 5, 4), new Part(48, 0, 3, 2, 1), new Part(56, 0, 2, 2, 1),
            new Part(0, 15, 2, 6, 2), new Part(8, 15, 1, 1, 5), new Part(20, 15, 1, 1, 4), 0.35f, 0.55f);
    public static final Kind FOX = new Kind("fox",
            new Part(0, 0, 5, 5, 9), new Part(28, 0, 5, 5, 5), new Part(48, 0, 2, 2, 4), new Part(0, 14, 2, 4, 1),
            new Part(6, 14, 2, 6, 2), new Part(14, 14, 3, 3, 5), new Part(30, 14, 3, 3, 4), 0.15f, 0.2f);

    /** Modelo de cada mascota segun su id (las texturas e iconos se llaman igual que el id). */
    public static Kind kindFor(String id) {
        if ("husky".equals(id)) return HUSKY;
        if ("gato".equals(id) || "gatonegro".equals(id)) return CAT;
        if ("zorro".equals(id)) return FOX;
        return DOG; // perro, dalmata y cualquier id nuevo
    }

    // ---- indices del arreglo que llena pose()
    public static final int BODY_X = 0, BODY_DY = 1, HEAD_X = 2, HEAD_Y = 3, SNOUT_X = 4, EAR_L_Z = 5, EAR_R_Z = 6,
            TAIL_X = 7, TAIL_Y = 8, TAIL2_X = 9, LEG_FR = 10, LEG_FL = 11, LEG_RR = 12, LEG_RL = 13, REAR_DY = 14, REAR_DZ = 15;
    public static final int POSE_SIZE = 16;

    /**
     * Calcula la pose (angulos en radianes, desplazamientos en unidades del modelo) para el momento pt (0..1) entre dos ticks.
     * Las cuatro patas van en diagonal; al sentarse el cuerpo se inclina, baja, y las patas de atras se pliegan hacia adelante.
     */
    public static void pose(Kind k, PetBrain b, float pt, float[] o) {
        float swing = b.iswing(pt), walk = b.iwalk(pt), sit = b.isit(pt), mouth = b.imouth(pt);
        float sitAng = SIT_ANGLE * sit;
        // al sentarse, el punto mas bajo del trasero baja hasta el suelo (y = 24); de pie, el cuerpo cuelga de las patas
        float lowest = k.body.h / 2f * (float) Math.cos(sitAng) + k.body.d / 2f * (float) Math.sin(sitAng);
        float delta = k.leg.h * sit - lowest + k.body.h / 2f;
        o[BODY_X] = -sitAng;
        o[BODY_DY] = delta;

        o[HEAD_X] = (float) Math.toRadians(b.iheadPitch(pt));
        o[HEAD_Y] = (float) Math.toRadians(b.iheadYaw(pt));
        o[SNOUT_X] = -mouth * 0.22f;
        float ear = 0.10f + b.iear(pt);
        o[EAR_L_Z] = ear;
        o[EAR_R_Z] = -ear;

        o[TAIL_X] = k.tailUp - sit * 0.5f * k.tailUp;
        o[TAIL_Y] = b.itail(pt);
        o[TAIL2_X] = k.tailCurl;

        float a = (float) Math.cos(walk * 0.6662f) * 1.4f * swing * (1f - sit);
        o[LEG_FR] = a;
        o[LEG_RL] = a;
        o[LEG_FL] = -a;
        o[LEG_RR] = -a;
        float fold = -1.45f * sit; // sentada: las patas de atras apuntan hacia adelante
        o[LEG_RR] += fold;
        o[LEG_RL] += fold;
        o[REAR_DY] = delta * 0.9f;
        o[REAR_DZ] = -1.0f * sit;
    }
}
