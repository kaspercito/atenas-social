package com.atenas.social.cosmetics;

/**
 * Alas de membrana (murcielago, dragon...). NO usa nada de Minecraft: entrega los vertices a un {@link WingMesh.Sink}
 * (el Tessellator en 1.8.9), asi se puede probar fuera del juego.
 *
 * Forma: un brazo (pivote -> muneca), 4 dedos que salen de la muneca y la membrana entre ellos, con el borde en festones.
 * La membrana es una malla polar alrededor de la muneca (SUB pasos por panel, RINGS anillos) para que pueda ondular.
 *
 * Textura 128 x 128 (tools/gen_membrane.py; las constantes de aqui DEBEN coincidir con las de ese script):
 *   - la membrana se proyecta en plano: u = (x - MAP_X0) / MAP_W ; v = V_MEM * (MAP_Y1 - y) / MAP_H
 *   - los huesos usan la franja BONE_V0..BONE_V1 (color liso).
 * Marco: igual que {@link WingMesh} (x a la izquierda del jugador, y hacia ABAJO en pantalla, la espalda es +z); aqui las
 * coordenadas del ala son "y hacia arriba" y se convierten al emitir.
 */
public final class MembraneWing {
    static final float PIVOT_X = WingMesh.PIVOT_X, PIVOT_Y = WingMesh.PIVOT_Y, PIVOT_Z = WingMesh.PIVOT_Z;

    // ---- geometria (en pixeles del modelo, 1/16 de bloque) ----
    static final float WX = 11f, WY = 12f;
    static final float[] ANG = {0.70f, 0.30f, -0.12f, -0.55f};
    static final float[] LEN = {17f, 24f, 24f, 19f};
    static final float BX = 3f, BY = -14f;
    static final int SUB = 4, RINGS = 2;
    static final float SCALLOP_FINGER = 0.22f, SCALLOP_BODY = 0.12f;
    static final float BONE_HW = 0.8f;

    // ---- textura ----
    static final float MAP_X0 = -1f, MAP_W = 37f, MAP_Y1 = 23.5f, MAP_H = 38.5f;
    static final float V_MEM = 0.90f, BONE_V0 = 0.93f, BONE_V1 = 0.99f;

    /** Separacion extra de la raiz de cada ala respecto a la espalda (con capa puesta). La fija WingsLayer antes de dibujar. */
    public float outset;
    /** Separacion extra hacia atras (con capa puesta). */
    public float zoff;

    /** Quads emitidos por ala y cara (para pruebas de rendimiento): paneles + huesos. */
    public static int quadsPerWing() { return 4 * SUB * RINGS + 6; }

    private final float[] ang = new float[5], len = new float[5];
    private final float[] qx = new float[4], qy = new float[4], qz = new float[4];

    public MembraneWing() {
        for (int i = 0; i < 4; i++) { ang[i] = ANG[i]; len[i] = LEN[i]; }
        ang[4] = (float) Math.atan2(BY - WY, BX - WX);
        len[4] = (float) Math.hypot(BX - WX, BY - WY);
    }

    /**
     * @param open apertura en radianes (0 = pegadas a la espalda)
     * @param time tiempo en ticks (fase de la onda)
     * @param flap fuerza de la onda (0 = rigida)
     * @param twoSided tambien emite la cara de atras (luz correcta por los dos lados)
     * @param unit escala de salida (1/16 para bloques)
     */
    public void emit(WingMesh.Sink s, float open, float time, float flap, boolean twoSided, float unit) {
        float co = (float) Math.cos(open), so = (float) Math.sin(open);
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -1f : 1f; // ala derecha del jugador = x negativo
            float nx = sx * -so, nz = co;
            for (int face = 0; face < (twoSided ? 2 : 1); face++) {
                boolean back = face == 1;
                float f = back ? -1f : 1f;
                s.normal(nx * f, 0f, nz * f);
                panels(s, sx, co, so, time, flap, back, unit);
                bones(s, sx, co, so, time, flap, back, unit);
            }
        }
    }

    // ------------------------------------------------------------------ membrana

    private void panels(WingMesh.Sink s, float sx, float co, float so, float time, float flap, boolean back, float unit) {
        for (int p = 0; p < 4; p++) {
            float d = p < 3 ? SCALLOP_FINGER : SCALLOP_BODY;
            for (int j = 0; j < SUB; j++) {
                for (int k = 0; k < RINGS; k++) {
                    point(0, p, j, k, d, 0f);
                    point(1, p, j + 1, k, d, 0f);
                    point(2, p, j + 1, k + 1, d, 0f);
                    point(3, p, j, k + 1, d, 0f);
                    quad(s, sx, co, so, time, flap, back, unit, true);
                }
            }
        }
        // triangulo entre el pivote, la muneca y el punto del cuerpo (cierra el hueco del lado del cuerpo)
        qx[0] = 0f; qy[0] = 0f; qx[1] = WX; qy[1] = WY; qx[2] = BX; qy[2] = BY; qx[3] = BX; qy[3] = BY;
        qz[0] = qz[1] = qz[2] = qz[3] = 0f;
        quad(s, sx, co, so, time, flap, back, unit, true);
    }

    /** Punto j (0..SUB) del anillo k (0..RINGS) del panel p, en la posicion "slot" del quad actual. */
    private void point(int slot, int p, int j, int k, float scallop, float z) {
        float t = (float) j / SUB;
        float a = ang[p] + (ang[p + 1] - ang[p]) * t;
        float r = (len[p] + (len[p + 1] - len[p]) * t) * (1f - scallop * (float) Math.sin(Math.PI * t));
        float f = (float) k / RINGS;
        qx[slot] = WX + f * r * (float) Math.cos(a);
        qy[slot] = WY + f * r * (float) Math.sin(a);
        qz[slot] = z;
    }

    // ------------------------------------------------------------------ huesos

    private void bones(WingMesh.Sink s, float sx, float co, float so, float time, float flap, boolean back, float unit) {
        segment(s, 0f, 0f, WX, WY, sx, co, so, time, flap, back, unit);
        for (int i = 0; i < 4; i++) {
            segment(s, WX, WY, WX + LEN[i] * (float) Math.cos(ANG[i]), WY + LEN[i] * (float) Math.sin(ANG[i]), sx, co, so, time, flap, back, unit);
        }
    }

    private void segment(WingMesh.Sink s, float x0, float y0, float x1, float y1, float sx, float co, float so,
                         float time, float flap, boolean back, float unit) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        float px = -dy / l * BONE_HW, py = dx / l * BONE_HW;
        float z = 0.15f; // un pelo por delante de la membrana: no hay z-fighting
        qx[0] = x0 + px; qy[0] = y0 + py; qz[0] = z;
        qx[1] = x1 + px; qy[1] = y1 + py; qz[1] = z;
        qx[2] = x1 - px; qy[2] = y1 - py; qz[2] = z;
        qx[3] = x0 - px; qy[3] = y0 - py; qz[3] = z;
        quad(s, sx, co, so, time, flap, back, unit, false);
    }

    // ------------------------------------------------------------------ salida

    private void quad(WingMesh.Sink s, float sx, float co, float so, float time, float flap, boolean back, float unit, boolean membrane) {
        if (!back) { for (int i = 0; i < 4; i++) vertex(s, i, sx, co, so, time, flap, unit, membrane, i); }
        else { for (int i = 3; i >= 0; i--) vertex(s, i, sx, co, so, time, flap, unit, membrane, i); }
    }

    private void vertex(WingMesh.Sink s, int i, float sx, float co, float so, float time, float flap, float unit, boolean membrane, int corner) {
        float X = qx[i], Y = qy[i];
        float r = (float) Math.sqrt(X * X + Y * Y);
        // la onda crece con la distancia al hombro y se retrasa hacia la punta (como una tela)
        float Z = qz[i] + (flap == 0f ? 0f : flap * r * 0.8f * (float) Math.sin(time * 0.55f - r * 0.12f));
        float u, v;
        if (membrane) {
            u = clamp01((X - MAP_X0) / MAP_W);
            v = clamp01(V_MEM * (MAP_Y1 - Y) / MAP_H);
        } else { // hueso: color liso de la franja inferior
            u = (corner == 0 || corner == 3) ? 0.1f : 0.9f;
            v = (corner < 2) ? BONE_V0 + 0.01f : BONE_V1 - 0.01f;
        }
        float Xr = X * co - Z * so;
        float Zr = X * so + Z * co;
        s.vertex(sx * (PIVOT_X + outset + Xr) * unit, (PIVOT_Y - Y) * unit, (PIVOT_Z + zoff + Zr) * unit, u, v);
    }

    private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }
}
