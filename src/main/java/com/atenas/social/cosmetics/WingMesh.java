package com.atenas.social.cosmetics;

/**
 * Malla de plumas de las alas. NO usa nada de Minecraft: genera los vértices y se los entrega a un {@link Sink}
 * (en 1.8.9 es el Tessellator, en 1.20.1 un VertexConsumer). Así se puede probar fuera del juego.
 *
 * Cada pluma es una tira de cuadrados (segmentos) que sigue una curva y ondula fuera del plano del ala.
 * Atlas de textura (tools/gen_wings.py): 256 x 1040, 26 franjas de 256 x 40; en cada franja la raíz está a la izquierda (u=0),
 * la punta a la derecha (u=1) y el borde de ataque arriba (v pequeño). El orden y las medidas de LEN / WID DEBEN coincidir
 * con gen_wings.py: 0 = hueso, 1..11 = primarias, 12..19 = secundarias, 20..25 = coberteras.
 *
 * Unidades: 1/16 de bloque (como las cajas de Minecraft); "unit" las convierte (normalmente 1/16 = 0.0625).
 * Marco del jugador: x a su izquierda, y hacia ABAJO, z hacia atrás (la espalda es +z). Pivote del ala: (±1, 1, 2.2).
 */
public final class WingMesh {
    public interface Sink {
        void normal(float x, float y, float z);
        void vertex(float x, float y, float z, float u, float v);
    }

    public static final int N = 26;
    public static final int MAX_SEGS = 6;
    static final float[] LEN = {16f, 26f, 26f, 25f, 24f, 23f, 22f, 20f, 18f, 16f, 14f, 12f,
            17f, 17f, 16f, 15f, 14f, 12.5f, 11f, 9.5f, 10f, 10f, 9f, 8f, 7f, 6f};
    static final float[] WID = {2.6f, 5.2f, 5.2f, 5.2f, 5.2f, 5.2f, 5.2f, 5.2f, 5.2f, 5.2f, 5.2f, 5.2f,
            4.4f, 4.4f, 4.4f, 4.4f, 4.4f, 4.4f, 4.4f, 4.4f, 3.6f, 3.6f, 3.6f, 3.6f, 3.6f, 3.6f};
    /** Ángulo base de cada pluma en el plano del ala (radianes, positivo = punta hacia arriba). Mismos que el icono. */
    static final float[] ANG = new float[N];
    /** Profundidad (z) de cada pluma en el marco del ala: las capas no se pisan. */
    static final float[] DEPTH = new float[N];
    /** Cuánto se curva la punta hacia abajo (radianes) de cada pluma. */
    static final float[] CURL = new float[N];
    static final float ATLAS_H = 1040f, STRIP_H = 40f;

    static {
        ANG[0] = 0.78f; DEPTH[0] = 0f; CURL[0] = 0f;
        for (int i = 0; i < 11; i++) { ANG[1 + i] = 0.58f - i * 0.145f; DEPTH[1 + i] = 0.10f + (i % 2) * 0.35f; CURL[1 + i] = -0.26f; }
        for (int j = 0; j < 8; j++)  { ANG[12 + j] = 0.50f - j * 0.18f; DEPTH[12 + j] = 0.62f + (j % 2) * 0.30f; CURL[12 + j] = -0.20f; }
        for (int m = 0; m < 6; m++)  { ANG[20 + m] = 0.42f - m * 0.21f; DEPTH[20 + m] = 1.00f + (m % 2) * 0.30f; CURL[20 + m] = -0.12f; }
    }

    public static final float PIVOT_X = 1f, PIVOT_Y = 1f, PIVOT_Z = 2.2f;
    /** Tamaño general de las alas (multiplica largo y ancho de TODAS las plumas; las proporciones de la textura no cambian). */
    public static final float SIZE = 0.85f;

    // memoria de trabajo (el dibujo ocurre siempre en el hilo de render)
    private final float[] px = new float[MAX_SEGS + 1], py = new float[MAX_SEGS + 1], pz = new float[MAX_SEGS + 1];

    /**
     * Segmentos por pluma segun la distancia (al cuadrado, en bloques) al jugador que mira:
     * cerca = pluma suave (6), lejos = casi plana (1). Las alas lejanas se ven pequenas, no hace falta dibujarlas curvas.
     */
    public static int segsFor(double distSq) {
        if (distSq < 100.0) return MAX_SEGS;   // menos de 10 bloques
        if (distSq < 400.0) return 4;          // menos de 20
        if (distSq < 900.0) return 2;          // menos de 30
        return 1;
    }

    /** La cara de atras (luz correcta por los dos lados) solo se emite de cerca; de lejos con una cara basta. */
    public static boolean twoSidedFor(double distSq) { return distSq < 144.0; }

    public static int quadsFor(int segs, boolean twoSided) { return 2 * N * segs * (twoSided ? 2 : 1); }

    /**
     * @param open   apertura del ala en radianes (0 = estirada hacia el lado, mayor = más hacia atrás)
     * @param time   tiempo en ticks (para la onda)
     * @param flap   fuerza de la onda (0 = rígida; 0.10 = aleteo en el aire)
     * @param segs   segmentos por pluma (1..MAX_SEGS); menos = más barato (plumas lejanas)
     * @param twoSided si es true también emite la cara de atrás con la normal invertida (luz correcta por los dos lados)
     * @param unit   escala de salida (1/16 para bloques)
     */
    public void emit(Sink s, float open, float time, float flap, int segs, boolean twoSided, float unit) {
        if (segs < 1) segs = 1;
        if (segs > MAX_SEGS) segs = MAX_SEGS;
        float co = (float) Math.cos(open), so = (float) Math.sin(open);
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -1f : 1f; // ala derecha del jugador = x negativo
            // normal del plano del ala (eje z del ala) ya girado por "open"
            float nx = sx * -so, nz = co;
            for (int face = 0; face < (twoSided ? 2 : 1); face++) {
                float f = face == 0 ? 1f : -1f;
                s.normal(nx * f, 0f, nz * f);
                for (int k = 0; k < N; k++) emitFeather(s, k, sx, co, so, time, flap, segs, face == 1, unit);
            }
        }
    }

    private void emitFeather(Sink s, int k, float sx, float co, float so, float time, float flap, int segs, boolean back, float unit) {
        float len = LEN[k] * SIZE, hw = WID[k] * 0.5f * SIZE;
        float amp = flap * len;
        float dl = len / segs;
        float x = 0f, y = 0f;
        px[0] = 0f; py[0] = 0f; pz[0] = DEPTH[k];
        for (int i = 1; i <= segs; i++) {
            float t = (float) i / segs, tm = (i - 0.5f) / segs;
            float a = ANG[k] + CURL[k] * tm * tm;
            x += (float) Math.cos(a) * dl;
            y += (float) Math.sin(a) * dl;
            px[i] = x; py[i] = y;
            // la punta se mueve más que la raíz; cada pluma va un poco retrasada respecto a la anterior (onda que recorre el ala)
            pz[i] = DEPTH[k] + (amp == 0f ? 0f : amp * (float) Math.pow(t, 1.5) * (float) Math.sin(time * 0.55f - k * 0.22f - t * 1.4f));
        }
        float v0 = (k * STRIP_H + 0.5f) / ATLAS_H, v1 = ((k + 1) * STRIP_H - 0.5f) / ATLAS_H;
        for (int i = 0; i < segs; i++) {
            float u0 = (float) i / segs, u1 = (float) (i + 1) / segs;
            if (!back) {
                v(s, i, +1, hw, sx, co, so, segs, unit, u0, v0);
                v(s, i + 1, +1, hw, sx, co, so, segs, unit, u1, v0);
                v(s, i + 1, -1, hw, sx, co, so, segs, unit, u1, v1);
                v(s, i, -1, hw, sx, co, so, segs, unit, u0, v1);
            } else {
                v(s, i, -1, hw, sx, co, so, segs, unit, u0, v1);
                v(s, i + 1, -1, hw, sx, co, so, segs, unit, u1, v1);
                v(s, i + 1, +1, hw, sx, co, so, segs, unit, u1, v0);
                v(s, i, +1, hw, sx, co, so, segs, unit, u0, v0);
            }
        }
    }

    /** Un vértice: punto i de la curva, lado +1 (borde de ataque, arriba) o -1 (abajo). */
    private void v(Sink s, int i, int edge, float hw, float sx, float co, float so, int segs, float unit, float u, float vv) {
        int a = Math.max(i - 1, 0), b = Math.min(i + 1, segs);
        float tx = px[b] - px[a], ty = py[b] - py[a];
        float tl = (float) Math.sqrt(tx * tx + ty * ty);
        if (tl < 1e-6f) { tx = 1f; ty = 0f; tl = 1f; }
        // perpendicular en el plano del ala, hacia arriba: (-ty, tx)
        float X = px[i] + (-ty / tl) * hw * edge;
        float Y = py[i] + (tx / tl) * hw * edge;
        float Z = pz[i];
        // giro de apertura alrededor del eje vertical, en el marco "hacia afuera"
        float Xr = X * co - Z * so;
        float Zr = X * so + Z * co;
        s.vertex(sx * (PIVOT_X + Xr) * unit, (PIVOT_Y - Y) * unit, (PIVOT_Z + Zr) * unit, u, vv);
    }
}
