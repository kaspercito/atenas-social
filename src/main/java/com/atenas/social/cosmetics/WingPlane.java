package com.atenas.social.cosmetics;

import java.util.HashMap;
import java.util.Map;

/**
 * Alas de PLANO (murcielago, dragon, mariposa, hada, fenix...). NO usa nada de Minecraft: genera una rejilla de cuadrados que cubre
 * el plano del ala y la entrega a un {@link WingMesh.Sink}. La silueta sale de la transparencia de la textura (tools/gen_wings2.py),
 * asi cualquier forma cuesta lo mismo; lo que se mueve es la rejilla.
 *
 * Plano: x hacia afuera 0..W, y hacia arriba -B..T, raiz (hombro) en (0,0). u = x / W ; v = (T - y) / H.
 * Animacion (todo por vertice, crece hacia la punta): el ala se dobla como un latigo (la punta se retrasa), sube y baja, y la tela ondula.
 */
public final class WingPlane {
    public static final float W = 36f, T = 24f, B = 12f, H = T + B;
    public static final int MAX_N = 12;

    /** Personalidad de cada ala: velocidad del aleteo, fuerza de la onda, latido en reposo (abrir y cerrar) y su frecuencia. */
    public static final class Style {
        public final float speed, amp, idle, idleFreq, open;
        Style(float speed, float amp, float idle, float idleFreq, float open) { this.speed = speed; this.amp = amp; this.idle = idle; this.idleFreq = idleFreq; this.open = open; }
    }

    private static final Map<String, Style> STYLES = new HashMap<String, Style>();
    private static final Style DEFAULT = new Style(1f, 1f, 0.05f, 0.25f, 1f);
    static {
        STYLES.put("murcielago", new Style(1.0f, 1.3f, 0.06f, 0.22f, 1.0f));
        STYLES.put("vampiro", new Style(0.9f, 1.4f, 0.05f, 0.20f, 1.05f));
        STYLES.put("dragon", new Style(0.75f, 1.8f, 0.08f, 0.15f, 1.1f));
        STYLES.put("abismo", new Style(1.9f, 0.9f, 0.12f, 0.9f, 0.95f));
        STYLES.put("hada", new Style(2.3f, 0.8f, 0.16f, 1.2f, 0.9f));
        STYLES.put("fantasma", new Style(0.7f, 2.2f, 0.06f, 0.18f, 1.0f));
        STYLES.put("jade", new Style(1.0f, 1.0f, 0.09f, 0.25f, 1.0f));
        STYLES.put("solar", new Style(1.0f, 0.5f, 0.09f, 0.20f, 1.05f));
        STYLES.put("ceniza", new Style(1.1f, 1.9f, 0.07f, 0.30f, 1.0f));
        STYLES.put("fenix", new Style(1.2f, 2.0f, 0.08f, 0.32f, 1.0f));
        STYLES.put("cristal", new Style(1.0f, 0.3f, 0.05f, 0.20f, 1.0f));
        STYLES.put("nebulosa", new Style(1.2f, 0.9f, 0.30f, 0.28f, 1.0f));
        STYLES.put("mariposa", new Style(1.3f, 0.9f, 0.34f, 0.32f, 1.0f));
        STYLES.put("tormenta", new Style(1.7f, 1.3f, 0.06f, 0.55f, 1.0f));
        STYLES.put("cibernetico", new Style(1.0f, 0.2f, 0.12f, 0.45f, 1.0f));
        STYLES.put("esqueleto", new Style(0.8f, 1.0f, 0.07f, 0.20f, 1.0f));
    }

    public static Style styleOf(String id) { Style s = STYLES.get(id); return s != null ? s : DEFAULT; }

    /** Divisiones de la rejilla segun la distancia (al cuadrado, en bloques): de lejos menos vertices. */
    public static int gridFor(double distSq) { return distSq < 100.0 ? MAX_N : distSq < 400.0 ? 8 : 5; }

    public float outset, zoff;

    /**
     * @param open   apertura del ala (rad; 0 = hacia el lado, mas = hacia atras)
     * @param time   tiempo en ticks (ya multiplicado por la velocidad del estilo)
     * @param flap   fuerza del aleteo (0.025 reposo .. 0.2 volando), ya multiplicada por la del estilo
     * @param n      divisiones de la rejilla (1..MAX_N)
     */
    public void emit(WingMesh.Sink s, float open, float time, float flap, boolean twoSided, int n, float unit) {
        if (n < 2) n = 2;
        if (n > MAX_N) n = MAX_N;
        float co = (float) Math.cos(open), so = (float) Math.sin(open);
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -1f : 1f; // ala derecha del jugador = x negativo
            float nx = sx * -so, nz = co;
            for (int face = 0; face < (twoSided ? 2 : 1); face++) {
                boolean back = face == 1;
                float f = back ? -1f : 1f;
                s.normal(nx * f, 0f, nz * f);
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n; i++) {
                        if (!back) {
                            v(s, i, j, n, sx, open, time, flap, unit);
                            v(s, i + 1, j, n, sx, open, time, flap, unit);
                            v(s, i + 1, j + 1, n, sx, open, time, flap, unit);
                            v(s, i, j + 1, n, sx, open, time, flap, unit);
                        } else {
                            v(s, i, j + 1, n, sx, open, time, flap, unit);
                            v(s, i + 1, j + 1, n, sx, open, time, flap, unit);
                            v(s, i + 1, j, n, sx, open, time, flap, unit);
                            v(s, i, j, n, sx, open, time, flap, unit);
                        }
                    }
                }
            }
        }
    }

    private void v(WingMesh.Sink s, int i, int j, int n, float sx, float open, float time, float flap, float unit) {
        float su = (float) i / n, sv = (float) j / n;
        float x = su * W, y = T - sv * H;
        float k = (float) Math.pow(su, 1.3);
        float ph = time * 0.55f - su * 1.7f - (y > 0 ? 0f : -y * 0.03f);   // la parte baja se retrasa un poco mas
        // el ala se dobla como un latigo: la punta gira mas y mas tarde que la raiz
        float psi = open + flap * 3.2f * k * (float) Math.sin(ph);
        float yv = y + flap * 6f * (float) Math.pow(su, 1.5) * (float) Math.cos(ph - 0.8f) - 1.0f * su * su;
        float X = x * (float) Math.cos(psi);
        float Z = x * (float) Math.sin(psi) + flap * 2.6f * su * (float) Math.sin(time * 0.9f - x * 0.25f + y * 0.2f);
        s.vertex(sx * (WingMesh.PIVOT_X + outset + X) * unit, (WingMesh.PIVOT_Y - yv) * unit, (WingMesh.PIVOT_Z + zoff + Z) * unit, su, sv);
    }
}
