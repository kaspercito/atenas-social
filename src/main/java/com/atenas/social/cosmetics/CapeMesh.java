package com.atenas.social.cosmetics;


/**
 * Capa de tela como UNA sola malla continua. Antes eran tiras (cajas) rigidas colgadas una de otra: al doblarse, entre tira y
 * tira se abria una rendija por fuera y se montaban por dentro, y la capa se veia "cortada en partes". Ahora cada fila de tela
 * comparte sus vertices con la de arriba y la de abajo (en cada articulacion hay UN marco de referencia, el promedio de las dos
 * tiras vecinas), asi que la tela se curva sin cortes y la luz cambia suave de una fila a otra.
 *
 * Los giros salen de {@link CapeWave} (misma cadena de siempre: la capa cuelga del hombro y cada tira gira respecto a la de
 * arriba). Textura: la hoja 64x32 de siempre (capa de 10 x 16 x 1 en u=0, v=0), con los mismos UV que tenia la caja:
 * cara de afuera u 1..11, de adentro u 12..22, filas v 1..17; asi cualquier capa (normal o animada) se ve igual que antes.
 */
public class CapeMesh {
    public static final int SEGS = CapeWave.SEGS;
    private static final float HALF_W = 5f, DEPTH = 1f;
    /** Capa lejana (mas de 16 bloques): una fila de cada tantas y sin los cantos de 1 px. */
    public static final int FAR_ROW_STEP = 4;

    private final float[] rx = new float[SEGS], rz = new float[SEGS];
    /** Punto de cada articulacion (en la cara de adentro, que es la linea del pivote) y su orientacion 3x3 (por filas). */
    private final float[][] pos = new float[SEGS + 1][3];
    private final float[][] rot = new float[SEGS + 1][9];
    private float[] seg = identity();
    private final float[] tmp = new float[9], tmp2 = new float[9];

    private static float[] identity() { return new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1}; }

    /** out = a * b (matrices 3x3 por filas). */
    private static void mul(float[] a, float[] b, float[] out) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
            }
        }
    }

    private static void rotX(float ang, float[] out) {
        float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
        out[0] = 1; out[1] = 0; out[2] = 0;
        out[3] = 0; out[4] = c; out[5] = -s;
        out[6] = 0; out[7] = s; out[8] = c;
    }

    private static void rotZ(float ang, float[] out) {
        float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
        out[0] = c; out[1] = -s; out[2] = 0;
        out[3] = s; out[4] = c; out[5] = 0;
        out[6] = 0; out[7] = 0; out[8] = 1;
    }

    /** Orientacion = padre * Rz(z) * Rx(x), como encadena ModelRenderer un hijo a su padre. */
    private void compose(float[] parent, float z, float x, float[] out) {
        rotZ(z, tmp);
        rotX(x, tmp2);
        float[] zx = new float[9];
        mul(tmp, tmp2, zx);
        mul(parent, zx, out);
    }

    /** Calcula articulaciones segun los giros ya cargados en rx / rz. */
    private void build() {
        float[] o = identity();
        float[] next = new float[9];
        float px = 0f, py = 0f, pz = 0f;
        for (int s = 0; s < SEGS; s++) {
            pos[s][0] = px; pos[s][1] = py; pos[s][2] = pz;
            if (s == 0) {
                System.arraycopy(o, 0, rot[0], 0, 9);
            } else {
                compose(o, rz[s] * 0.5f, rx[s] * 0.5f, rot[s]); // marco de la articulacion: a medio camino entre las dos tiras
                compose(o, rz[s], rx[s], next);
                System.arraycopy(next, 0, o, 0, 9);
            }
            // la tira s cuelga de su articulacion a lo largo de su propio eje y
            px += o[1] * CapeWave.SEG_H;
            py += o[4] * CapeWave.SEG_H;
            pz += o[7] * CapeWave.SEG_H;
        }
        pos[SEGS][0] = px; pos[SEGS][1] = py; pos[SEGS][2] = pz;
        System.arraycopy(o, 0, rot[SEGS], 0, 9);
    }

    private int frame = 0, frames = 1;

    private void vtx(WingMesh.Sink out, int j, float lx, float lz, float u, float v, float nx, float ny, float nz, float scale) {
        float[] m = rot[j];
        float[] p = pos[j];
        float x = p[0] + m[0] * lx + m[2] * lz;
        float y = p[1] + m[3] * lx + m[5] * lz;
        float z = p[2] + m[6] * lx + m[8] * lz;
        out.normal(m[0] * nx + m[1] * ny + m[2] * nz, m[3] * nx + m[4] * ny + m[5] * nz, m[6] * nx + m[7] * ny + m[8] * nz);
        // textura de 64 x (32 * fotogramas): cada fotograma ocupa su franja de 32 de alto
        out.vertex(x * scale, y * scale, z * scale, u / 64f, (32f * frame + v) / (32f * frames));
    }

    /** Pone la capa en su pose (ver {@link CapeWave#angles}) y entrega sus cuadrados al Sink (GL_QUADS; se dibuja sin culling). */
    public void emit(WingMesh.Sink wr, int frame, int frames, float scale, float bend, float amp, float time, float side) {
        emit(wr, frame, frames, scale, bend, amp, time, side, false);
    }

    public void emit(WingMesh.Sink wr, int frame, int frames, float scale, float bend, float amp, float time, float side, boolean far) {
        this.frame = frame;
        this.frames = frames;
        CapeWave.angles(bend, amp, time, side, rx, rz);
        build();

        final int step = far ? FAR_ROW_STEP : 1;
        for (int r = 0; r < SEGS; r += step) {
            int a = r, b = Math.min(SEGS, r + step);
            float va = 1f + r * CapeWave.SEG_H, vb = 1f + b * CapeWave.SEG_H;
            // cara de afuera (la que se ve desde atras): u 1..11, normal hacia afuera (-z del marco)
            vtx(wr, a, -HALF_W, -DEPTH, 1f, va, 0, 0, -1, scale);
            vtx(wr, b, -HALF_W, -DEPTH, 1f, vb, 0, 0, -1, scale);
            vtx(wr, b, HALF_W, -DEPTH, 11f, vb, 0, 0, -1, scale);
            vtx(wr, a, HALF_W, -DEPTH, 11f, va, 0, 0, -1, scale);
            // cara de adentro (hacia el cuerpo): u 22..12 (espejada, como la caja)
            vtx(wr, a, -HALF_W, 0f, 22f, va, 0, 0, 1, scale);
            vtx(wr, a, HALF_W, 0f, 12f, va, 0, 0, 1, scale);
            vtx(wr, b, HALF_W, 0f, 12f, vb, 0, 0, 1, scale);
            vtx(wr, b, -HALF_W, 0f, 22f, vb, 0, 0, 1, scale);
            if (far) continue;
            // canto izquierdo y derecho (1 de grueso)
            vtx(wr, a, -HALF_W, -DEPTH, 1f, va, -1, 0, 0, scale);
            vtx(wr, a, -HALF_W, 0f, 0f, va, -1, 0, 0, scale);
            vtx(wr, b, -HALF_W, 0f, 0f, vb, -1, 0, 0, scale);
            vtx(wr, b, -HALF_W, -DEPTH, 1f, vb, -1, 0, 0, scale);
            vtx(wr, a, HALF_W, 0f, 12f, va, 1, 0, 0, scale);
            vtx(wr, a, HALF_W, -DEPTH, 11f, va, 1, 0, 0, scale);
            vtx(wr, b, HALF_W, -DEPTH, 11f, vb, 1, 0, 0, scale);
            vtx(wr, b, HALF_W, 0f, 12f, vb, 1, 0, 0, scale);
        }
        // borde de arriba (hombros) y de abajo (punta)
        vtx(wr, 0, -HALF_W, 0f, 1f, 0f, 0, -1, 0, scale);
        vtx(wr, 0, -HALF_W, -DEPTH, 1f, 1f, 0, -1, 0, scale);
        vtx(wr, 0, HALF_W, -DEPTH, 11f, 1f, 0, -1, 0, scale);
        vtx(wr, 0, HALF_W, 0f, 11f, 0f, 0, -1, 0, scale);
        vtx(wr, SEGS, HALF_W, -DEPTH, 21f, 0f, 0, 1, 0, scale);
        vtx(wr, SEGS, -HALF_W, -DEPTH, 11f, 0f, 0, 1, 0, scale);
        vtx(wr, SEGS, -HALF_W, 0f, 11f, 1f, 0, 1, 0, scale);
        vtx(wr, SEGS, HALF_W, 0f, 21f, 1f, 0, 1, 0, scale);
    }
}
